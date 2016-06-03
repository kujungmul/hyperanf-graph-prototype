package it.unimi.dsi.big.webgraph.labelling;

/*		 
 * Copyright (C) 2003-2015 Paolo Boldi and Sebastiano Vigna 
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.big.webgraph.*;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectBigArrays;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;

public class BitStreamArcLabelledGraphTest extends WebGraphTestCase {

	private static final int[] SIZES = { 0, 1, 2, 3, 4, 7 };
	private static final int MAX_WIDTH_FOR_FIXED = 32;
	private static final int[] WIDTHS = { -1, 0, 1, 2, 3, 8, 32, 40, 41, 63 };
	private static final int[] BATCH_SIZES = { 1, 2, 4, 5, 16 };
	
	public static File storeTempGraph( final ArcLabelledImmutableGraph g ) throws IOException, IllegalArgumentException, SecurityException {
		File basename = File.createTempFile( BitStreamArcLabelledGraphTest.class.getSimpleName(), "test" );
		BitStreamArcLabelledImmutableGraph.store( g, basename.toString(), basename.toString() + "-underlying" );
		BVGraph.store( g, basename.toString() + "-underlying" );
		return basename;
	}

	private static OutputBitStream createTempBitStream( final String name ) throws FileNotFoundException {
		File f = new File( name );
		f.deleteOnExit();
		return new OutputBitStream( f.getAbsolutePath() );
	}

	public String createGraphWithFixedWidthLabels( File basename, ImmutableGraph g, int width ) throws IllegalArgumentException, SecurityException, IOException {
		final int n = (int)g.numNodes();
		System.err.println( "Testing " + n + " nodes, width " + width+ ", basename " + basename );

		OutputBitStream labels = createTempBitStream( basename + "-fixedlabel" + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION );
		OutputBitStream offsets = createTempBitStream( basename + "-fixedlabel" + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION );
		offsets.writeGamma( 0 );
		for( int i = 0; i < n; i++ ) {
			int bits = 0;
			for( LongIterator j = LazyLongIterators.eager( g.successors( i ) ); j.hasNext(); ) bits += labels.writeInt( i * (int)j.nextLong() + i, width );
			offsets.writeGamma( bits );
		}
		labels.close();
		offsets.close();

		PrintWriter pw = new PrintWriter( new FileWriter( basename + "-fixedlabel.properties" ) );
		pw.println( ImmutableGraph.GRAPHCLASS_PROPERTY_KEY + " = " + BitStreamArcLabelledImmutableGraph.class.getName() );
		pw.println( BitStreamArcLabelledImmutableGraph.LABELSPEC_PROPERTY_KEY + " = " + FixedWidthIntLabel.class.getName() + "(TEST," + width + ")" );
		pw.println( ArcLabelledImmutableGraph.UNDERLYINGGRAPH_PROPERTY_KEY + " = " + basename.getName() );
		pw.close();
		
		return basename + "-fixedlabel";
	}

	public String createGraphWithFixedWidthListLabels( File basename, ImmutableGraph g, int width ) throws IllegalArgumentException, SecurityException, IOException {
		final int n = (int)g.numNodes();
		System.err.println( "Testing " + n + " nodes, element width " + width+ ", basename " + basename );

		OutputBitStream labels = createTempBitStream( basename + "-fixedlistlabel" + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION );
		OutputBitStream offsets = createTempBitStream( basename + "-fixedlistlabel" + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION );
		offsets.writeGamma( 0 );
		for( int i = 0; i < n; i++ ) {
			int bits = 0;
			for( LongIterator j = LazyLongIterators.eager( g.successors( i ) ); j.hasNext(); ) {
				int succ = (int)j.nextLong();
				bits += labels.writeGamma( ( succ + 1 ) * 2  ); // list length
				for( int k = 0; k < ( succ + 1 ) * 2 ; k++ ) bits += labels.writeInt( i * k + i, width );
			}
			offsets.writeGamma( bits );
		}
		labels.close();
		offsets.close();

		PrintWriter pw = new PrintWriter( new FileWriter( basename + "-fixedlistlabel" + ImmutableGraph.PROPERTIES_EXTENSION ) );
		pw.println( ImmutableGraph.GRAPHCLASS_PROPERTY_KEY + " = " + BitStreamArcLabelledImmutableGraph.class.getName() );
		pw.println( BitStreamArcLabelledImmutableGraph.LABELSPEC_PROPERTY_KEY + " = " + FixedWidthIntListLabel.class.getName() + "(TEST," + width + ")" );
		pw.println( ArcLabelledImmutableGraph.UNDERLYINGGRAPH_PROPERTY_KEY + " = " + basename.getName() );
		pw.close();
		
		return basename + "-fixedlistlabel";
	}

	public String createGraphWithGammaLabels( File basename, ImmutableGraph g ) throws IllegalArgumentException, SecurityException, IOException {
		// We create a complete graph with labels 
		final int n = (int)g.numNodes();
		System.err.println( "Testing " + n + " nodes, gamma coding, basename " + basename );

		OutputBitStream labels = createTempBitStream( basename + "-gammalabel" + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION );
		OutputBitStream offsets = createTempBitStream( basename + "-gammalabel" + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION );
		offsets.writeGamma( 0 );
		for( int i = 0; i < n; i++ ) {
			int bits = 0;
			for( LongIterator j = LazyLongIterators.eager( g.successors( i ) ); j.hasNext(); ) bits += labels.writeGamma( i * (int)j.nextLong() + i );
			offsets.writeGamma( bits );
		}
		labels.close();
		offsets.close();

		PrintWriter pw = new PrintWriter( new FileWriter( basename + "-gammalabel" + ImmutableGraph.PROPERTIES_EXTENSION ) );
		pw.println( ImmutableGraph.GRAPHCLASS_PROPERTY_KEY + " = " + BitStreamArcLabelledImmutableGraph.class.getName() );
		pw.println( BitStreamArcLabelledImmutableGraph.LABELSPEC_PROPERTY_KEY + " = " + GammaCodedIntLabel.class.getName() + "(TEST)" );
		pw.println( ArcLabelledImmutableGraph.UNDERLYINGGRAPH_PROPERTY_KEY + " = " + basename.getName() );
		pw.close();
		
		return basename + "-gammalabel";
	}

	public void testLabels( ArcLabelledImmutableGraph alg, final int width ) {
		
		final int mask = (int)( width == MAX_WIDTH_FOR_FIXED ? -1 : ( 1L << width ) - 1 );

		// Sequential access, iterators
		for( ArcLabelledNodeIterator nodeIterator = alg.nodeIterator(); nodeIterator.hasNext(); ) {
			int curr = (int)nodeIterator.nextLong();
			ArcLabelledNodeIterator.LabelledArcIterator l = nodeIterator.successors();
			int d = (int)nodeIterator.outdegree();
			while( d-- != 0 ) {
				int succ = (int)l.nextLong();
				if ( l.label() instanceof AbstractIntLabel )
					assertEquals( curr + " -> " + succ,( curr * succ + curr ) & mask, l.label().getInt() );
				else {
					int[] value = (int[]) l.label().get();
					assertEquals( ( succ + 1 ) * 2, value.length );
					for( int i = 0; i < value.length; i++ ) assertEquals(  "Successor of index " + i + " of " + curr + "(" + succ + ")", ( curr * i + curr ) & mask, value[ i ] );
				}
			}
		}

		// Sequential access, arrays
		for( ArcLabelledNodeIterator nodeIterator = alg.nodeIterator(); nodeIterator.hasNext(); ) {
			long curr = nodeIterator.nextLong();
			long d = nodeIterator.outdegree();
			long[][] succ = nodeIterator.successorBigArray();
			Label[][] label = nodeIterator.labelBigArray();
			for( int i = 0; i < d; i++ ) {
				if ( ObjectBigArrays.get( label, i ) instanceof AbstractIntLabel )
					assertEquals( curr + " -> " + LongBigArrays.get( succ, i ), ( curr * LongBigArrays.get( succ, i ) + curr ) & mask, ObjectBigArrays.get( label, i ).getInt() );
				else {
					int[] value = (int[]) ObjectBigArrays.get( label, i ).get();
					assertEquals( ( LongBigArrays.get( succ, i ) + 1 ) * 2, value.length );
					for( int j = 0; j < value.length; j++ ) assertEquals( ( curr * j + curr ) & mask, value[ j ] );
				}
			}
		}

		if ( ! alg.randomAccess() ) return;

		// Random access, iterators
		for( int curr = 0; curr < alg.numNodes(); curr++ ) {
			ArcLabelledNodeIterator.LabelledArcIterator l = alg.successors( curr );
			int d = (int)alg.outdegree( curr );
			while( d-- != 0 ) {
				int succ = (int)l.nextLong();
				if ( l.label() instanceof AbstractIntLabel )
					assertEquals( curr + " -> " + succ ,( curr * succ + curr ) & mask, l.label().getInt() );
				else {
					int[] value = (int[]) l.label().get();
					assertEquals( ( succ + 1 ) * 2, value.length );
					for( int i = 0; i < value.length; i++ ) assertEquals( ( curr * i + curr ) & mask, value[ i ] );
				}
			}
		}

		// Random access, arrays
		for( int curr = 0; curr < alg.numNodes(); curr++ ) {
			int d = (int)alg.outdegree( curr );
			long[][] succ = alg.successorBigArray( curr );
			Label[][] label = alg.labelBigArray( curr );
			for( int i = 0; i < d; i++ ) {
				if ( ObjectBigArrays.get( label, i ) instanceof AbstractIntLabel )
					assertEquals( curr + " -> " + LongBigArrays.get( succ, i ), ( curr * LongBigArrays.get( succ, i ) + curr ) & mask, ObjectBigArrays.get( label, i ).getInt() );
				else {
					int[] value = (int[]) ObjectBigArrays.get( label, i ).get();
					assertEquals( ( LongBigArrays.get( succ, i ) + 1 ) * 2, value.length );
					for( int j = 0; j < value.length; j++ ) assertEquals( ( curr * j + curr ) & mask, value[ j ] );
				}
			}
		}
	}
	
	@Test
	public void testLabels() throws IOException, IllegalArgumentException, SecurityException {
		for( int n: SIZES ) {
			for( int type = 0; type < 3; type++ ) {
				System.err.println( "Testing type " + type + "..." );
				final ImmutableGraph g = ImmutableGraph.wrap( type == 0 ? ArrayListMutableGraph.newCompleteGraph( n, false ).immutableView() :
					type == 1 ? ArrayListMutableGraph.newCompleteBinaryIntree( n ).immutableView() :
						ArrayListMutableGraph.newCompleteBinaryOuttree( n ).immutableView() );
				final File basename = BVGraphTest.storeTempGraph( g );
				// -1 means gamma coding
				for( int width: WIDTHS ) {
					final String basenameLabel = width == -1 ?
							createGraphWithGammaLabels( basename, g ) : 
								width < MAX_WIDTH_FOR_FIXED ?  createGraphWithFixedWidthLabels( basename, g, width ) :
									createGraphWithFixedWidthListLabels( basename, g, width - MAX_WIDTH_FOR_FIXED );
					
					System.err.println( "Testing offline..." );
					testLabels( BitStreamArcLabelledImmutableGraph.loadOffline( basenameLabel ), width % MAX_WIDTH_FOR_FIXED );
					System.err.println( "Testing standard..." );
					testLabels( BitStreamArcLabelledImmutableGraph.load( basenameLabel ), width % MAX_WIDTH_FOR_FIXED );
					
					new File( basenameLabel + ImmutableGraph.PROPERTIES_EXTENSION ).delete();
					new File( basenameLabel + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION ).delete();
					new File( basenameLabel + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION ).delete();
				}
				basename.delete();
				deleteGraph( basename );
			}
		}
	}

	@Test
	// Proceeds with the same test as before, but with a graph obtained as a union
	public void testUnion() throws IllegalArgumentException, SecurityException, IOException {
		for( int n: SIZES ) {
			for( int type = 0; type < 3; type++ ) {
				System.err.println( "Testing arc-labelled union type " + type + "..." );
				final ImmutableGraph g = ImmutableGraph.wrap( type == 0 ? ArrayListMutableGraph.newCompleteGraph( n, false ).immutableView() :
					type == 1 ? ArrayListMutableGraph.newCompleteBinaryIntree( n ).immutableView() :
						ArrayListMutableGraph.newCompleteBinaryOuttree( n ).immutableView() );
					
				// Now split the graph g into two (possibly non-disjoint) graphs
				ArrayListMutableGraph g0mut = new ArrayListMutableGraph();
				ArrayListMutableGraph g1mut = new ArrayListMutableGraph();
				g0mut.addNodes( (int)g.numNodes() ); g1mut.addNodes( (int)g.numNodes() );
				NodeIterator nit = g.nodeIterator();
				while ( nit.hasNext() ) {
					int from = (int)nit.nextLong();
					LazyLongIterator succ = nit.successors();
					int d = (int)nit.outdegree();
					while ( d-- != 0 ) {
						int to = (int)succ.nextLong();
						if ( Math.random() < .5 ) g0mut.addArc( from, to );
						else if ( Math.random() < .5 ) g1mut.addArc( from, to );
						else { g0mut.addArc( from, to ); g1mut.addArc( from, to ); }
					}
				}	
				ImmutableGraph g0 = ImmutableGraph.wrap( g0mut.immutableView() );
				ImmutableGraph g1 = ImmutableGraph.wrap( g1mut.immutableView() );
					
				final File basename0 = BVGraphTest.storeTempGraph( g0 );
				final File basename1 = BVGraphTest.storeTempGraph( g1 );
				// -1 means gamma coding
				for( int width: WIDTHS ) {
					final String basenameLabel0 = width == -1 ?
							createGraphWithGammaLabels( basename0, g0 ) : 
								width < MAX_WIDTH_FOR_FIXED ?  createGraphWithFixedWidthLabels( basename0, g0, width ) :
									createGraphWithFixedWidthListLabels( basename0, g0, width - MAX_WIDTH_FOR_FIXED );
					final String basenameLabel1 = width == -1 ?
							createGraphWithGammaLabels( basename1, g1 ) : 
								width < MAX_WIDTH_FOR_FIXED ?  createGraphWithFixedWidthLabels( basename1, g1, width ) :
									createGraphWithFixedWidthListLabels( basename1, g1, width - MAX_WIDTH_FOR_FIXED );
								
					
					System.err.println( "Testing arc-labelled union offline..." );
					testLabels( (ArcLabelledImmutableGraph) Transform.union( BitStreamArcLabelledImmutableGraph.loadOffline( basenameLabel0 ), BitStreamArcLabelledImmutableGraph.loadOffline( basenameLabel1 ) ), width % MAX_WIDTH_FOR_FIXED );
					System.err.println( "Testing arc-labelled union standard..." );
					testLabels( (ArcLabelledImmutableGraph) Transform.union( BitStreamArcLabelledImmutableGraph.load( basenameLabel0 ), BitStreamArcLabelledImmutableGraph.load( basenameLabel1 ) ), width % MAX_WIDTH_FOR_FIXED );
					
					new File( basenameLabel0 + ImmutableGraph.PROPERTIES_EXTENSION ).delete();
					new File( basenameLabel0 + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION ).delete();
					new File( basenameLabel0 + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION ).delete();
					new File( basenameLabel1 + ImmutableGraph.PROPERTIES_EXTENSION ).delete();
					new File( basenameLabel1 + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION ).delete();
					new File( basenameLabel1 + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION ).delete();
				}
				basename0.delete();
				deleteGraph(  basename0 );
				basename1.delete();
				deleteGraph( basename1 );
			}
		}
	}
		
	@Test
	public void testTransposition() throws IOException, IllegalArgumentException, SecurityException {
		for( int n: new int[] {7}) {
				for( int type = 0; type < 3; type++ ) {
				System.err.println( "Testing arc-labelled transposition type " + type + "..." );
				final ImmutableGraph g = ImmutableGraph.wrap( type == 0 ? ArrayListMutableGraph.newCompleteGraph( n, false ).immutableView() :
					type == 1 ? ArrayListMutableGraph.newCompleteBinaryIntree( n ).immutableView() :
						ArrayListMutableGraph.newCompleteBinaryOuttree( n ).immutableView() );
				final File basename = BVGraphTest.storeTempGraph( g );
				// -1 means gamma coding
				for( int width: WIDTHS ) {
					final String basenameLabel;
					
					if ( width == -1 ) basenameLabel = createGraphWithGammaLabels( basename, g );
					else if ( width < MAX_WIDTH_FOR_FIXED ) basenameLabel = createGraphWithFixedWidthLabels( basename, g, width );
					else basenameLabel = createGraphWithFixedWidthListLabels( basename, g, width - MAX_WIDTH_FOR_FIXED );

					for ( int batchSize: BATCH_SIZES ) {
						ArcLabelledImmutableGraph gt = Transform.transposeOffline( BitStreamArcLabelledImmutableGraph.loadOffline( basenameLabel ),
								batchSize, new File( System.getProperty("java.io.tmpdir") ), null );			
						
						ArcLabelledImmutableGraph gtt = Transform.transposeOffline( gt,
								batchSize, new File( System.getProperty("java.io.tmpdir") ), null );			
						System.err.println( "Testing with batch size " + batchSize + "..." );
						testLabels( gtt, width % MAX_WIDTH_FOR_FIXED );
					}
					
					new File( basenameLabel + ImmutableGraph.PROPERTIES_EXTENSION ).delete();
					new File( basenameLabel + BitStreamArcLabelledImmutableGraph.LABELS_EXTENSION ).delete();
					new File( basenameLabel + BitStreamArcLabelledImmutableGraph.LABEL_OFFSETS_EXTENSION ).delete();
				}
				basename.delete();
				deleteGraph( basename );
			}
		}
	}

}
