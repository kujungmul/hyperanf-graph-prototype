package it.unimi.dsi.big.webgraph.algo;

import it.unimi.dsi.big.webgraph.Edge;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.MutableGraph;
import it.unimi.dsi.big.webgraph.NodeIterator;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigArrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private long[][] maximalMatching;
    private long maximalMatchingLength;

    private LongArrayBitVector vertexCover;
    private MutableGraph graph;
    private float resizeFactor = 1.1f;

    public DynamicVertexCover(MutableGraph graph) {
        vertexCover = LongArrayBitVector.ofLength(1);

        this.graph = graph;

        long maxNode = graph.numNodes();
        maximalMatching = LongBigArrays.newBigArray(Math.max(maxNode, 1));
        LongBigArrays.fill(maximalMatching, -1);
        maximalMatchingLength = LongBigArrays.length(maximalMatching);

        graph.iterateAllEdges(edge -> {
            insertEdge(edge);
            return null;
        });
    }


    public long getMemoryUsageBytes() {
        return maximalMatchingLength * Long.BYTES + vertexCover.length() / Byte.SIZE;
    }


    @Override
    public Map<Long, AffectedState> insertEdge(Edge edge) {
        Map<Long, AffectedState> affectedNodes = new HashMap<>();
        if(isInVertexCover(edge.from) || isInVertexCover(edge.to)) {
            return affectedNodes;
        }

        addEdgeToMaximalMatching(edge);
        addEdgeToVertexCover(edge);

        updateAffectedNodesFromEdge(edge, AffectedState.ADDED, affectedNodes);

        return affectedNodes;
    }



    /**
     * Deletes an edge and updates the Vertex Cover/Maximal matching
     * @param edge The deleted edge
     */
    @Override
    public Map<Long, AffectedState> deleteEdge(Edge edge, MutableGraph graphTranspose) {
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
        checkIncomingEdgesToDeletedEndpoints(edge, addedNodes, graphTranspose);

        affectedNodes = createAffectedNodes(removedNodes, addedNodes);

        return affectedNodes;
    }

    private Map<Long,AffectedState> createAffectedNodes(Set<Long> removedNodes, Set<Long> addedNodes) {
        Map<Long, AffectedState> affectedNodes = new HashMap<>();
        for(Long removedNode : removedNodes) {
            updateAffectedNodes(removedNode, AffectedState.REMOVED, affectedNodes);
        }
        for(Long addedNode : addedNodes) {
            updateAffectedNodes(addedNode, AffectedState.ADDED, affectedNodes);
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
        LazyLongIterator succ = graph.successors(currentNode);
        long degree = graph.outdegree(currentNode);

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
    public void checkIncomingEdgesToDeletedEndpoints(Edge edge, Set<Long> addedNodes, MutableGraph graphTranspose) {
        checkTransposeFromNode(edge.from, addedNodes, graphTranspose);
        if(edge.from != edge.to) {
            checkTransposeFromNode(edge.to, addedNodes, graphTranspose);
        }
    }

    private void checkTransposeFromNode(Long node, Set<Long> addedNodes, MutableGraph graphTranspose) {
        NodeIterator nodeIt = graphTranspose.nodeIterator(node);
        nodeIt.nextLong();
        long outdegree = nodeIt.outdegree();
        LazyLongIterator succ = nodeIt.successors();

        while(outdegree-- != 0) {
            if(isInVertexCover(node)) {
                break;
            }

            long neighbor = succ.nextLong();
            if(isInVertexCover(neighbor)) {
                continue;
            }

            Edge incomingEdge = new Edge(neighbor, node);
            addEdgeToMaximalMatching(incomingEdge);
            addEdgeToVertexCover(incomingEdge);

            addedNodes.add(node);
            addedNodes.add(neighbor);

            break;
        }
    }


    @Override
    public boolean isInVertexCover(long node) {
        if(vertexCover.length() <= node) {
            return false;
        }
        return vertexCover.getBoolean(node);
    }

    private boolean isInMaximalMatching(Edge edge) {
        if(edge.from >= maximalMatchingLength) {
            return false;
        }

        long value = LongBigArrays.get(maximalMatching, edge.from);

        if(value == -1) {
            return false;
        }

        if(value != edge.to) {
            return false;
        }

        return true;
    }

    private void addEdgeToMaximalMatching(Edge edge) {
        if(edge.from >= maximalMatchingLength) {
            maximalMatching = LongBigArrays.ensureCapacity(maximalMatching, getNewLength(maximalMatchingLength, edge.from + 1, resizeFactor));
            long newLength = LongBigArrays.length(maximalMatching);
            LongBigArrays.fill(maximalMatching, maximalMatchingLength, newLength, -1);
            maximalMatchingLength = newLength;
        }

        LongBigArrays.set(maximalMatching, edge.from, edge.to);
    }

    private void addEdgeToVertexCover(Edge edge) {
        checkArrayCapacity(edge);
        vertexCover.set(edge.from, true);
        vertexCover.set(edge.to, true);
    }

    private void removeEdgeFromMaximalMatching(Edge edge) {
        if(!isInMaximalMatching(edge)) {
            return;
        }

        LongBigArrays.set(maximalMatching, edge.from, -1);
    }

    private void removeEdgeFromVertexCover(Edge edge) {
        checkArrayCapacity(edge);
        vertexCover.set(edge.from, false);
        vertexCover.set(edge.to,   false);
    }

    private void checkArrayCapacity(Edge edge) {
        long largestNode = Math.max(edge.from, edge.to);
        long limit = vertexCover.length();

        if (limit < largestNode + 1) {
            long minimalNewLength = largestNode + 1;
            long newLimit = getNewLength(vertexCover.length(), minimalNewLength, resizeFactor);
            vertexCover.length(newLimit);
        }
    }

    private long getNewLength(long oldLength, long minNewLength, float resizeFactor) {
        double resizePow = Math.max(Math.ceil(Math.log(minNewLength / (double)oldLength ) * (1 / Math.log(resizeFactor))), resizeFactor);
        return (long)(oldLength * Math.pow(resizeFactor, resizePow));
    }

    @Override
    public LongArrayBitVector getNodesInVertexCover(){
        return vertexCover;
    }

    @Override
    public LazyLongIterator getNodesInVertexCoverIterator(){
        return new VertexCoverIterator();
    }

    @Override
    public long getVertexCoverSize() {
        return vertexCover.count();
    }

    /**
     * Runs in O(n).
     * Made public for debugging and testing purposes
     * @return
     */
    public long getMaximalMatchingSize() {
        long count = 0;
        for (int i = 0; i < maximalMatching.length; i++) {
            for (int j = 0; j < maximalMatching[i].length; j++) {

                if(maximalMatching[i][j] != -1) {
                    count++;
                }
            }
        }

        return count;
    }



    private class VertexCoverIterator implements LazyLongIterator{
        private long last = -1;

        @Override
        public long nextLong() {
            return last = vertexCover.nextOne(last+1);
        }

        @Override
        public long skip(long l) {
            long num = 0;
            while (nextLong() != -1 && num < l)
                num++;
            return num;
        }
    }
}
