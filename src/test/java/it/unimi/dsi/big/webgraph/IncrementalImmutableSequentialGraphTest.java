package it.unimi.dsi.big.webgraph;

/*		 
 * Copyright (C) 2013-2015 Sebastiano Vigna 
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

import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.examples.ErdosRenyiGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class IncrementalImmutableSequentialGraphTest extends WebGraphTestCase { 

	@Test
	public void testErdosRenyi() throws IOException, InterruptedException, ExecutionException {
		final String basename = File.createTempFile( IncrementalImmutableSequentialGraph.class.getSimpleName() + "-", "-temp" ).toString();
		for( int size: new int[] { 10, 100, 1000, 10000 } ) {
			final ImmutableGraph g = ImmutableGraph.wrap( new ArrayListMutableGraph( new ErdosRenyiGraph( size, .001, 0, false ) ).immutableView() );	
			final IncrementalImmutableSequentialGraph incrementalImmutableSequentialGraph = new IncrementalImmutableSequentialGraph();
			final Future<Void> future = Executors.newSingleThreadExecutor().submit( new Callable<Void>() {
				@Override
				public Void call() throws IOException {
					BVGraph.store( incrementalImmutableSequentialGraph, basename );
					return null;
				}
			} );
		
			for( NodeIterator nodeIterator = g.nodeIterator(); nodeIterator.hasNext(); ) {
				nodeIterator.nextLong();
				incrementalImmutableSequentialGraph.add( nodeIterator.successorBigArray(), 0, nodeIterator.outdegree() );
			}
			
			incrementalImmutableSequentialGraph.add( IncrementalImmutableSequentialGraph.END_OF_GRAPH );
			
			future.get();
			assertEquals( g, ImmutableGraph.load( basename ) );			
		}
		
		deleteGraph( basename );
	}

}
