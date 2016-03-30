package se.meltwater.vertexcover;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.NodeIterator;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import se.meltwater.graph.Edge;
import se.meltwater.graph.IGraph;
import se.meltwater.graph.SimulatedGraph;

import java.util.*;

/**
 * @author Simon Lindhén
 * @author Johan Nilsson Hansen
 *
 * Maintains a 2-approximate Vertex Cover by
 * calculating a maximal matching and for every
 * edge in the matching we pick both nodes to the VC.
 *
 * Needs a reference to the graph to be able to perform deletions.
 *
 * Inspired by the "simple implementation" of Fully dynamic maintenance of vertex cover
 * by Zoran Ivković and Errol L. Lloyd.
 *
 */
public class DynamicVertexCover implements IDynamicVertexCover {

    private HashMap<Long, Long> maximalMatching = new HashMap<>();
    private LongArrayBitVector vertexCoverz;
    private IGraph graph;
    private float resizeFactor = 1.1f;

    public DynamicVertexCover(IGraph graph) {
        vertexCoverz = LongArrayBitVector.ofLength(1);

        this.graph = graph;

        graph.iterateAllEdges(edge -> {
            insertEdge(edge);
            return null;
        });
    }

    @Override
    public Map<Long, AffectedState> insertEdge(Edge edge) {
        Map<Long, AffectedState> affectedNodes = new HashMap<>();
        if(isInVertexCover(edge.from) || isInVertexCover(edge.to)) {
            return affectedNodes;
        }

        addEdgeToMaximalMatching(edge);
        addEdgeToVertexCover(edge);

        updateAffectedNodesFromEdge(edge, AffectedState.Added, affectedNodes);

        return affectedNodes;
    }

    /**
     * Deletes an edge and updates the Vertex Cover/Maximal matching
     * @param edge The deleted edge
     */
    @Override
    public Map<Long, AffectedState> deleteEdge(Edge edge) {
        Set<Long> removedNodes = new HashSet<>();
        Set<Long> addedNodes   = new HashSet<>();

        Map<Long, AffectedState> affectedNodes = new HashMap<>();
        if(!isInMaximalMatching(edge)) {
            return affectedNodes;
        }

        removeEdgeFromMaximalMatching(edge);
        removeEdgeFromVertexCover(edge);

        removedNodes.add(edge.from);
        removedNodes.add(edge.to);

        checkOutgoingEdgesFromDeletedEndpoint(edge.from, addedNodes);
        if(edge.from != edge.to)
            checkOutgoingEdgesFromDeletedEndpoint(edge.to, addedNodes);
        checkIncomingEdgesToDeletedEndpoints(edge, addedNodes);

        affectedNodes = createAffectedNodes(removedNodes, addedNodes);

        return affectedNodes;
    }

    private Map<Long,AffectedState> createAffectedNodes(Set<Long> removedNodes, Set<Long> addedNodes) {
        Map<Long, AffectedState> affectedNodes = new HashMap<>();
        for(Long removedNode : removedNodes) {
            updateAffectedNodes(removedNode, AffectedState.Removed, affectedNodes);
        }
        for(Long addedNode : addedNodes) {
            updateAffectedNodes(addedNode, AffectedState.Added, affectedNodes);
        }

        return affectedNodes;
    }

    /**
     * Inserts both endpoints of the edge with the specified state.
     * @param edge
     * @param state
     * @param affectedNodes
     */
    public void updateAffectedNodesFromEdge(Edge edge, AffectedState state, Map<Long, AffectedState> affectedNodes) {
        updateAffectedNodes(edge.from, state, affectedNodes);
        updateAffectedNodes(edge.to,   state, affectedNodes);
    }

    /**
     * If a node gets marked as both Added and Removed it is removed from {@code affectedNodes},
     * else it is inserted with the specified state.
     * @param node Node to insert to affected nodes
     * @param state Enum saying how the node was affected
     * @param affectedNodes Currently affected nodes
     */
    public static void updateAffectedNodes(Long node, AffectedState state, Map<Long, AffectedState> affectedNodes) {
        AffectedState mappedState = affectedNodes.get(node);

        if(mappedState == state) {
            return;
        }

        if(mappedState == null) {
            affectedNodes.put(node, state);
        } else { //We have the node both as added and removed = not affected
            affectedNodes.remove(node);
        }
    }

    /**
     * Takes a node that was deleted from the Vertex Cover.
     * Neighbors of this node might now be uncovered, and we must
     * check every outgoing edge to determine if it needs to be added
     * to the maximal matching.
     * @param currentNode A node deleted from the Vertex Cover
     */
    public void checkOutgoingEdgesFromDeletedEndpoint(long currentNode, Set<Long> addedNodes) {
        LazyLongIterator succ = graph.getSuccessors(currentNode);
        long degree = graph.getOutdegree(currentNode);

        while( degree != 0 ) {
            long successorOfCurrentNode = succ.nextLong();

            if(!isInVertexCover(successorOfCurrentNode)){
                Edge edge = new Edge(currentNode, successorOfCurrentNode);
                addEdgeToMaximalMatching(edge);
                addEdgeToVertexCover(edge);

                addedNodes.add(edge.from);
                addedNodes.add(edge.to);

                break;
            }

            degree--;
        }
    }

    /**
     * Takes an edge deleted from the Maximal Matching.
     * Each endpoint of the edge might have previously covered
     * incoming edges which now are uncovered. To determine what incoming
     * edges are uncovered we loop through all edges and test them.
     * @param edge An edge deleted from the Maximal Matching
     */
    public void checkIncomingEdgesToDeletedEndpoints(Edge edge, Set<Long> addedNodes) {
        NodeIterator nodeIt = graph.getNodeIterator();
        for(long currentNode = 0; currentNode < graph.getNumberOfNodes(); currentNode++) {
            nodeIt.nextLong();
            if(isInVertexCover(currentNode)) {
                continue;
            }

            LazyLongIterator succ = nodeIt.successors();
            long degree = nodeIt.outdegree();
            while( degree-- != 0 ) {
                long successorOfCurrentNode = succ.nextLong();

                if(!(successorOfCurrentNode == edge.from) && !(successorOfCurrentNode == edge.to)) {
                    continue;
                }

                if(!isInVertexCover(successorOfCurrentNode)){
                    Edge incomingEdge = new Edge(currentNode, successorOfCurrentNode);
                    addEdgeToMaximalMatching(incomingEdge);
                    addEdgeToVertexCover(incomingEdge);

                    addedNodes.add(Long.valueOf(currentNode));
                    addedNodes.add(Long.valueOf(successorOfCurrentNode));

                    break;
                }
            }
        }
    }


    @Override
    public boolean isInVertexCover(long node) {
        if(vertexCoverz.size64() <= node) {
            return false;
        }
        return vertexCoverz.getBoolean(node);
    }

    public boolean isInMaximalMatching(Edge edge) {
        Long value = maximalMatching.get(edge.from);
        if(value == null) {
            return false;
        }

        if(value != edge.to) {
            return false;
        }

        return true;
    }

    private void addEdgeToMaximalMatching(Edge edge) {
        maximalMatching.put(edge.from, edge.to);
    }

    private void addEdgeToVertexCover(Edge edge) {
        //vertexCoverz.length(Math.max(edge.from, edge.to) + 1);
        checkArrayCapacity(edge);
        vertexCoverz.set(edge.from, true);
        vertexCoverz.set(edge.to, true);
    }

    private void removeEdgeFromMaximalMatching(Edge edge) {
        if(!isInMaximalMatching(edge)) {
            return;
        }

        maximalMatching.remove(edge.from);
    }

    private void removeEdgeFromVertexCover(Edge edge) {

        //vertexCoverz.length(Math.max(edge.from, edge.to) + 1);
        checkArrayCapacity(edge);
        vertexCoverz.set(edge.from, false);
        vertexCoverz.set(edge.to,   false);
    }

    public void checkArrayCapacity(Edge edge) {
        long largestNode = Math.max(edge.from, edge.to);
        long limit = vertexCoverz.length();

        if (limit < largestNode + 1) {
            long minimalIncreaseSize = largestNode + 1 - limit;
            double resizePow = Math.ceil(Math.log((limit + minimalIncreaseSize) / (float)limit ) * (1/Math.log(resizeFactor)));
            long newLimit = (long)(limit * Math.pow(resizeFactor, resizePow));

            vertexCoverz.length(newLimit);
        }
    }

    @Override
    public LongArrayBitVector getNodesInVertexCover(){
        return vertexCoverz;
    }

    @Override
    public LazyLongIterator getNodesInVertexCoverIterator(){
        return new VertexCoverIterator();
    }

    @Override
    public long getVertexCoverSize() {
        return vertexCoverz.count();
    }

    public long getMaximalMatchingSize() {
        return maximalMatching.size();
    }



    private class VertexCoverIterator implements LazyLongIterator{
        private long last = -1;

        @Override
        public long nextLong() {
            return last = vertexCoverz.nextOne(last+1);
        }

        @Override
        public long skip(long l) {
            long num = 0;
            while (nextLong() != -1 && num < l) num++;
            return num;
        }
    }

}
