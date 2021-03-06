package it.unimi.dsi.big.webgraph;

import it.unimi.dsi.logging.ProgressLogger;

import java.io.IOException;
import java.util.*;

/**
 * @author Simon Lindhén
 * @author Johan Nilsson Hansen
 *
 * Instead of loading a physical graph file
 * this class can be used to simulate a graph.
 * Mainly used for testing purposes when it's
 * not feasable to create a physical file for
 * each test case.
 */
public class SimulatedGraph extends MutableGraph implements  Cloneable {

    private TreeMap<Long, TreeSet<Long>> iteratorNeighbors = new TreeMap<>();

    private long numArcs = 0;
    private long numNodes = 0;

    public SimulatedGraph() {}

    private Iterator<Long> emptyLongIterator(){
        return new Iterator<Long>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Long next() {
                return null;
            }
        };
    }

    @Override
    public Object clone() {
        try {
            SimulatedGraph clone = (SimulatedGraph) super.clone();
            clone.iteratorNeighbors = new TreeMap<>();
            for (Map.Entry<Long, TreeSet<Long>> entry : iteratorNeighbors.entrySet()) {
                clone.iteratorNeighbors.put(entry.getKey(), (TreeSet<Long>) entry.getValue().clone());
            }

            return clone;
        } catch(CloneNotSupportedException e) {
            throw new RuntimeException("Simulated graph should never throw this",e);
        }
    }



    @Override
    public MutableGraph copy(){
        SimulatedGraph copy = new SimulatedGraph();
        copy.iteratorNeighbors = (TreeMap<Long, TreeSet<Long>>) iteratorNeighbors.clone();
        copy.numArcs = numArcs;
        copy.numNodes = numNodes;
        return copy;
    }

    public void addNode(long node) {

        if(!iteratorNeighbors.containsKey(node)) {
            numNodes = Math.max(node+1,numNodes);
        }

    }

    @Override
    public boolean addEdges(Edge... edges){
        boolean allInserted = true;

        for (int i = 0; i < edges.length; i++) {
            allInserted &= addEdge(edges[i]);
        }

        return allInserted;
    }

    @Override
    public boolean addEdge(Edge edge){

        if(!containsNode(edge.from)) {
            addNode(edge.from);
        }
        if(!containsNode(edge.to)) {
            addNode(edge.to);
        }

        boolean wasAdded = iteratorNeighbors.computeIfAbsent(edge.from,k -> new TreeSet<>()).add(edge.to);

        if(wasAdded)
            numArcs++;
        return wasAdded;
    }

    public boolean deleteEdge(Edge edge) {
        Set<Long> neighbors = iteratorNeighbors.get(edge.from);
        if(neighbors != null) {
            boolean wasRemoved = neighbors.remove(edge.to);
            if(wasRemoved)
                numArcs--;
            return wasRemoved;
        }else
            return false;
    }

    public Iterator<Long> getLongIterator(long node){

        Set<Long> neighbor = iteratorNeighbors.get(node);
        if(neighbor == null)
            return emptyLongIterator();
        else
            return neighbor.iterator();

    }

    @Override
    public long numNodes() {
        return numNodes;
    }

    @Override
    public NodeIterator nodeIterator(long node) {
        return new SimulatedGraphNodeIterator(node,this);
    }

    @Override
    public NodeIterator nodeIterator(){
        return nodeIterator(0);
    }

    @Override
    public long outdegree(long node){
        Set<Long> neighbors = iteratorNeighbors.get(node);
        return neighbors == null ? 0 : neighbors.size();
    }

    @Override
    public LazyLongIterator successors(long node){
        return new SimulatedGraphSuccessorsIterator(getLongIterator(node));
    }

    @Override
    public long numArcs(){
        return numArcs;
    }

    @Override
    public boolean randomAccess() {
        return true;
    }

    @Override
    public MutableGraph transpose() {
        SimulatedGraph transpose = new SimulatedGraph();
        transpose.addNode(this.numNodes - 1); /* -1 as 0-indexed */
        Edge[] currentEdges = this.getAllEdges();

        for (int i = 0; i < currentEdges.length; i++) {
            Edge currentEdge = currentEdges[i];
            Edge flippedEdge = new Edge(currentEdge.to, currentEdge.from);
            transpose.addEdge(flippedEdge);
        }

        return transpose;
    }

    @Override
    public void store(String outputFile) throws IOException {
        BVGraph.store(this, outputFile, 0, 0, -1, -1, 0, new ProgressLogger());
    }

    @Override
    public long getMemoryUsageBytes() {
        return Utils.getMemoryUsage(this);
    }

    private static class SimulatedGraphNodeIterator extends NodeIterator{

        private long currentIndex;
        private SimulatedGraph graph;
        private long outdegree;
        private Map.Entry<Long,TreeSet<Long>> entry = null;
        private Iterator<Map.Entry<Long,TreeSet<Long>>> iterator;

        public SimulatedGraphNodeIterator(long startAt, SimulatedGraph graph){
            currentIndex = startAt-1;
            this.graph = graph;
            outdegree = -1;
            iterator = graph.iteratorNeighbors.tailMap(currentIndex).entrySet().iterator();
            if(iterator.hasNext())
                entry = iterator.next();

        }

        @Override
        public long outdegree() {
            return outdegree;
        }

        @Override
        public boolean hasNext() {
            return currentIndex+1 < graph.numNodes;
        }

        @Override
        public long nextLong() {
            if(!hasNext())
                return -1;

            if(iterator.hasNext() && currentIndex == entry.getKey()){
                entry = iterator.next();
            }
            outdegree = entry == null || entry.getKey() != currentIndex+1 ? 0 : entry.getValue().size();
            return ++currentIndex;
        }

        @Override
        public LazyLongIterator successors(){
            if(outdegree == -1)
                throw new IllegalStateException("nextLong never called");
            return outdegree == 0 ?
                    LazyLongIterators.EMPTY_ITERATOR :
                    new SimulatedGraphSuccessorsIterator(entry.getValue().iterator());

        }

    }

    private static class SimulatedGraphSuccessorsIterator implements LazyLongIterator{

        Iterator<Long> it;

        SimulatedGraphSuccessorsIterator(Iterator<Long> successors){
            it = successors;
        }

        @Override
        public long nextLong() {
            if(!it.hasNext()) {
                return -1;
            }
            return it.next();
        }

        @Override
        public long skip(long l) {
            int i = 0;
            while(it.hasNext() && i++ < l) it.next();
            return i;
        }
    }

    /**
     * Returns an array of all the edges in the graph.
     * Mainly used for testing purposes as it is slow
     * to iterate through all edges, return them and then
     * iterate them again.
     * Works in O(m)
     * @return List of all edges
     */
    public Edge[] getAllEdges() {
        ArrayList<Edge> edges = new ArrayList<>();
        this.iterateAllEdges(edge -> {
            edges.add(edge);
            return null;
        });

        Edge[] edgeArray = new Edge[edges.size()];
        edges.toArray(edgeArray);
        return edgeArray;
    }

}
