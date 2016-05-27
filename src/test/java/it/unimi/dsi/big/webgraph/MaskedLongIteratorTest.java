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

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.util.XorShift64StarRandom;

import org.junit.Test;

public class MaskedLongIteratorTest {

	public void test( final int length, final int numberOfZeroes ) {
		final XorShift64StarRandom random = new XorShift64StarRandom( 0 );
		// Reads the length and number of 0s
		final long x[] = new long[ length ];
		boolean keep[] = new boolean[ length ];
		LongArrayList res = new LongArrayList();
		LongArrayList blocks = new LongArrayList();
		int i, j; 
		long p = 0;
		boolean dep;

		// Generate
		for ( i = 0; i < length; i++ ) p = x[ i ] = p + ( random.nextLong() & 0x7FFFFFFFFFFFFFFFL ) % 1000;
		for ( i = 0; i < length-numberOfZeroes; i++ ) keep[ i ] = true;
		for ( i = 0; i < length; i++ ) {
			j = i + (int)( Math.random() * ( length - i ) );
			dep = keep[ i ]; keep[ i ] = keep[ j ]; keep[ j ] = dep;
		}

		// Compute result
		for ( i = 0; i < length; i++ ) if ( keep[ i ] ) res.add( x[ i ] );
		res.trim();
		long result[] = res.elements();

		// Prepare blocks
		boolean lookAt = true;
		int curr = 0;
		for ( i = 0; i < length; i++ ) {
			if ( keep[ i ] == lookAt ) curr++;
			else {
				blocks.add( curr );
				lookAt = !lookAt;
				curr = 1;
			}
		}
		blocks.trim();
		final long bs[] = blocks.elements();

		// Output 
		System.out.println( "GENERATED:" );
		for ( i = 0; i < length; i++ ) {
			if ( keep[ i ] ) System.out.print( "*" );
			System.out.print( x[ i ] + "  " );
		}
		System.out.println( "\nBLOCKS:" );
		for ( i = 0; i < bs.length; i++ )
			System.out.print( bs[ i ] + "  " );
		System.out.println( "\nEXPECTED RESULT:" );
		for ( i = 0; i < result.length; i++ ) 
			System.out.print( result[ i ] + "  " );
		System.out.println();

		LazyLongIterator maskedIterator = new MaskedLongIterator( bs, LazyLongIterators.lazy( new LongArrayList( x ).iterator() ) );

		for ( i = 0; i < result.length; i++ ) assertEquals( i + ": ", result[ i ], maskedIterator.nextLong() );
		assertEquals( -1, maskedIterator.nextLong() );

		// Test skips
		maskedIterator = new MaskedLongIterator( bs, LazyLongIterators.lazy( new LongArrayList( x ).iterator() ) );
		LongIterator results = LongIterators.wrap( result );
		
		for ( i = 0; i < result.length; i++ ) {
			int toSkip = random.nextInt( 5 );
			assertEquals( results.skip( toSkip ), maskedIterator.skip( toSkip ) );
			if ( results.hasNext() ) assertEquals( i + ": ", results.nextLong(), maskedIterator.nextLong() );
		}
		assertEquals( -1, maskedIterator.nextLong() );

	}
	
	@Test
	public void test() {
		for( int i = 0; i < 20; i++ )
			for( int j = 0; j < 20; j++ )
				test( i, j );
	}
}
