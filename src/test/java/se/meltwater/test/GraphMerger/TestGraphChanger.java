package se.meltwater.test.GraphMerger;

import it.unimi.dsi.big.webgraph.BVGraph;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.NodeIterator;
import it.unimi.dsi.big.webgraph.UnionImmutableGraph;
import org.junit.Test;
import se.meltwater.graphEditing.GraphMerger;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Lindhén
 * @author Johan Nilsson Hansen
 *
 * // TODO class description
 */
public class TestGraphChanger extends GraphMerger {

    public TestGraphChanger(){
        super("testGraphs/noBlocksUk","testGraphs/wordassociationNoBlocks","testGraphs/tempOutGraph");
    }

    @Test
    public void testMerge(){
        try {
            merge();
            BVGraph mergedGraph = BVGraph.loadMapped(newGraph);
            UnionImmutableGraph unionedGraph = new UnionImmutableGraph(BVGraph.loadMapped(graph1),BVGraph.loadMapped(graph2));
            assertEquals(mergedGraph.numNodes(),unionedGraph.numNodes());
            NodeIterator nodeItMerged = mergedGraph.nodeIterator();
            NodeIterator nodeItUnioned = unionedGraph.nodeIterator();

            for(long node = 0; node < mergedGraph.numNodes() ; node++){
                nodeItMerged.nextLong(); nodeItUnioned.nextLong();
                long outM = nodeItMerged.outdegree(), outU = nodeItUnioned.outdegree();
                assertEquals("Node " + node + " didn't match",outM,outU);
                if(outM == 0 || outM != outU)
                    continue;
                LazyLongIterator succItM = nodeItMerged.successors(), succItU = nodeItUnioned.successors();
                for(int succI = 0; succI < outM; succI++){
                    assertEquals("Successor at index " + succI + " for node " + node,succItM.nextLong(),succItU.nextLong());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            assertFalse(true);
        }

        File tempOut;
        for(String fileExtension : new String[]{".properties",".graph",".offsets"}){
            tempOut = new File(newGraph + fileExtension);
            if(tempOut.exists())
                tempOut.delete();
        }

    }

}