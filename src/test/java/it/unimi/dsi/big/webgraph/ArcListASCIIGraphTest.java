package it.unimi.dsi.big.webgraph;

/*		 
 * Copyright (C) 2007-2015 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

public class ArcListASCIIGraphTest extends WebGraphTestCase {

	@Test
	public void testLoadOnce() throws UnsupportedEncodingException, IOException {
	
		ArcListASCIIGraph g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "0 2\n0 1\n1 0\n1 2\n2 0\n2 1".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( ArrayListMutableGraph.newCompleteGraph( 3, false ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );

		g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "0 1\n0 2\n1  0\n1 \t 2\n2 0\n2 1".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( ArrayListMutableGraph.newCompleteGraph( 3, false ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );

		g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "2 0\n2 1".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( new ArrayListMutableGraph( 3, new int[][] {{2,0},{2,1}} ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );

		g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "1 2".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( new ArrayListMutableGraph( 3, new int[][] {{1,2}} ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );

		g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "2 1".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( new ArrayListMutableGraph( 3, new int[][] {{2,1}} ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );
	
		g = ArcListASCIIGraph.loadOnce( new FastByteArrayInputStream( "0 1\n2 1".getBytes( "ASCII" ) ) );
		assertEquals( ImmutableGraph.wrap( new ArrayListMutableGraph( 3, new int[][] {{0,1},{2,1}} ).immutableView() ), ImmutableGraph.wrap( new ArrayListMutableGraph( ImmutableGraph.wrap( g ) ).immutableView() ) );
	}
}
