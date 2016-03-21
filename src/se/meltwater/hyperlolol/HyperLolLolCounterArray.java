package se.meltwater.hyperlolol;

/*
 * DSI utilities
 *
 * Copyright (C) 2010-2016 Paolo Boldi and Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.io.Serializable;
import java.util.Arrays;

/**
 * An increment-only dynamic version of HyperLogLogCounterArray.
 * Its purpose is to be able to use a HyperLogLogCounterArray when
 * the number of nodes is unknown.
 * It overrides the registers of HyperLogLogCounterArray.
 * The extended functionality is not thread-safe.

 * An array of approximate sets each represented using a HyperLogLog counter.
 *
 * <p>HyperLogLog counters represent the number of elements of a set in an approximate way. They have been
 * introduced by Philippe Flajolet, &Eacute;ric Fusy, Olivier Gandouet, and Fre&eacute;de&eacute;ric Meunier in
 * &ldquo;HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm&rdquo;,
 * <em>Proceedings of the 13th conference on analysis of algorithm (AofA 07)</em>, pages
 * 127&minus;146, 2007. They are an improvement over the basic idea of <em>loglog counting</em>, introduced by
 * Marianne Durand and Philippe Flajolet in &ldquo;Loglog counting of large cardinalities&rdquo;,
 * <i>ESA 2003, 11th Annual European Symposium</i>, volume 2832 of Lecture Notes in Computer Science, pages 605&minus;617, Springer, 2003.
 *
 * <p>Each counter is composed by {@link #m} registers, and each register is made of {@link #registerSize} bits.
 * The first number depends on the desired relative standard deviation, and its logarithm can be computed using {@link #log2NumberOfRegisters(double)},
 * whereas the second number depends on an upper bound on the number of distinct elements to be counted, and it can be computed
 * using {@link #registerSize(long)}.
 *
 * <p>Actually, this class implements an <em>array</em> of counters. Each counter is completely independent, but they all use the same hash function.
 * The reason for this design is that in our intended applications hundred of millions of counters are common, and the JVM overhead to create such a number of objects
 * would be unbearable. This class allocates an array of {@link LongArrayBitVector}s, each containing {@link #CHUNK_SIZE} registers,
 * and can thus handle billions of billions of registers efficiently (in turn, this means being able to
 * handle an array of millions of billions of high-precision counters).
 *
 * <p>When creating an instance, you can choose the size of the array (i.e., the number of counters) and the desired relative standard deviation
 * (either {@linkplain #HyperLolLolCounterArray(long, long, double) explicitly} or
 * {@linkplain #HyperLolLolCounterArray(long, long, int) choosing the number of registers per counter}).
 * Then, you can {@linkplain #add(long, long) add an element to a counter}. At any time, you can
 * {@linkplain #count(long) count} count (approximately) the number of distinct elements that have been added to a counter.
 *
 * <p>If you need to reuse this class multiple times, you can {@linkplain #clear() clear all registers}, possibly {@linkplain #clear(long) setting a new seed}.
 * The seed is used to compute the hash function used by the HyperLogLog counters.
 *
 *
 * <h2>Utility methods</h2>
 *
 * <p>This class provides a number of utility methods that make it possible to {@linkplain #getCounter(long, long[]) extract a counter as an array of longs},
 * {@linkplain #setCounter(long[], long) set the contents of a counter given an array of longs}, and
 * {@linkplain #max(long[], long[]) maximize quickly two counters given as arrays of longs}.
 *
 * @author Paolo Boldi
 * @author Sebastiano Vigna
 * @author Simon Lindhén
 * @author Johan Nilsson Hansen
 */

public class HyperLolLolCounterArray implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private static final boolean ASSERTS = false;
    private static final boolean DEBUG = false;

    /** The logarithm of the maximum size in registers of a bit vector. */
    public final static int CHUNK_SHIFT = 30;
    /** The maximum size in registers of a bit vector. */
    public final static long CHUNK_SIZE = 1L << CHUNK_SHIFT;
    /** The mask used to obtain an register offset in a chunk. */
    public final static long CHUNK_MASK = CHUNK_SIZE - 1;

    /** The correct value for &alpha;, multiplied by {@link #m}<sup>2</sup> (see the paper). */
    private double alphaMM;
    /** The number of registers minus one. */
    protected final int mMinus1;
    /** An array of arrays of longs containing all registers. */
    protected long bits[][];
    /** {@link #registerSize}-bit views of {@link #bits}. */
    protected LongBigList registers[];
    /** The shift that selects the chunk corresponding to a counter. */
    protected final int counterShift;
    /** A seed for hashing. */
    protected long seed;
    /** The mask OR'd with the output of the hash function so that {@link Long#numberOfTrailingZeros(long)} does not return too large a value. */
    private long sentinelMask;
    /** Whether counters are aligned to longwords. */
    protected final boolean longwordAligned;
    /** A mask for the residual bits of a counter (the {@link #counterSize} <code>%</code> {@link Long#SIZE} lowest bits). */
    protected final long counterResidualMask;
    /** A mask containing a one in the most significant bit of each register (i.e., in positions of the form {@link #registerSize registerSize * (i + 1) - 1}). */
    protected long[] msbMask;
    /** A mask containing a one in the least significant bit of each register (i.e., in positions of the form {@link #registerSize registerSize * i}). */
    protected long[] lsbMask;
    /** The logarithm of the number of registers per counter (at most 30). */
    public final int log2m;
    /** The number of registers per counter. */
    public final int m;
    /** The size in bits of each register. */
    public final int registerSize;
    /** The size in bits of each counter ({@link #registerSize} <code>*</code> {@link #m}). */
    public final int counterSize;
    /** The size of a counter in longwords (ceiled if there are less then {@link Long#SIZE} registers per counter). */
    public final int counterLongwords;
    protected long limit;
    protected long size;
    private String exceptionString = "Exception in " + HyperLolLolCounterArray.class + ". ";
    private final float resizeFactor = 1.1f;


    public boolean hasSameRegisters(long index1, long index2) {
        final int register1 = (int)( jenkins( index1, seed ) & mMinus1 );
        final int register2 = (int)( jenkins( index2, seed ) & mMinus1 );

        return register1 == register2;
    }

    /**
     * Returns the logarithm of the number of registers per counter that are necessary to attain a
     * given relative standard deviation.
     *
     * @param rsd the relative standard deviation to be attained.
     * @return the logarithm of the number of registers that are necessary to attain relative standard deviation <code>rsd</code>.
     */
    public static int log2NumberOfRegisters( final double rsd ) {
        // 1.106 is valid for 16 registers or more.
        return (int)Math.ceil( Fast.log2( ( 1.106 / rsd ) * ( 1.106 / rsd ) ) );
    }

    /**
     * Returns the relative standard deviation corresponding to a given logarithm of the number of registers per counter.
     *
     * @param log2m the logarithm of the number of registers per counter (at most 30).
     * @return the resulting relative standard deviation.
     */
    public static double relativeStandardDeviation( final int log2m ) {
        return ( log2m == 4 ? 1.106 : log2m == 5 ? 1.070 : log2m == 6 ? 1.054 : log2m == 7 ? 1.046 : 1.04 ) / Math.sqrt( 1 << log2m );
    }

    /**
     * Returns the register size in bits, given an upper bound on the number of distinct elements.
     *
     * @param n an upper bound on the number of distinct elements.
     * @return the register size in bits.
     */

    public static int registerSize( final long n ) {
        return Math.max( 5, (int)Math.ceil( Math.log( Math.log( n ) / Math.log( 2 ) ) / Math.log( 2 ) ) );
    }

    /** Returns the chunk of a given counter.
     *
     * @param counter a counter.
     * @return its chunk.
     */
    public int chunk( final long counter ) {
        return (int)( counter >>> counterShift );
    }

    /** Returns the bit offset of a given counter in its chunk.
     *
     * @param counter a counter.
     * @return the starting bit of the given counter in its chunk.
     */
    public long offset( final long counter ) {
        return ( counter << log2m & CHUNK_MASK ) * registerSize;
    }

    /**
     * Creates a new array of counters.
     *
     * @param arraySize the number of counters.
     * @param n the expected number of elements.
     * @param rsd the relative standard deviation.
     */
    public HyperLolLolCounterArray(final long arraySize, final long n, final double rsd ) {
        this( arraySize, n, log2NumberOfRegisters( rsd ) );
    }

    /**
     * Creates a new array of counters.
     *
     * @param arraySize the number of counters.
     * @param n the expected number of elements.
     * @param log2m the logarithm of the number of registers per counter (at most 30).
     */
    public HyperLolLolCounterArray(final long arraySize, final long n, final int log2m ) {
        this( arraySize, n, log2m, Util.randomSeed() );
    }

    /**
     * Creates a new array of counters.
     *
     * @param arraySize the number of counters.
     * @param n the expected number of elements.
     * @param log2m the logarithm of the number of registers per counter (at most 30).
     * @param seed the seed used to compute the hash function.
     */
    public HyperLolLolCounterArray(final long arraySize, final long n, final int log2m, final long seed ) {
        if ( log2m > Integer.SIZE - 2 ) throw new IllegalArgumentException( "The logarithm of the number of register per counter (" + log2m + ") is too large" );
        this.m = 1 << ( this.log2m = log2m );
        this.mMinus1 = m - 1;
        this.registerSize = registerSize( n );
        this.counterSize = registerSize << log2m;
        size = arraySize;
        limit = arraySize == 0 ? 1 : arraySize;

        counterShift = CHUNK_SHIFT - log2m;
        sentinelMask = 1L << ( 1 << registerSize ) - 2;
        // System.err.println( arraySize + " " + m + " " + registerSize);
        final long sizeInRegisters = limit * m;
        final int numVectors = (int)( ( sizeInRegisters + CHUNK_MASK ) >>> CHUNK_SHIFT );

        initBitArrays(numVectors,sizeInRegisters);

        counterLongwords = ( counterSize + Long.SIZE - 1 ) / Long.SIZE;
        counterResidualMask = ( 1L << counterSize % Long.SIZE ) - 1;
        longwordAligned = counterSize % Long.SIZE == 0;

        // We initialise the masks for the broadword code in max().
        msbMask = new long[ registerSize ];
        lsbMask = new long[ registerSize ];
        for( int i = registerSize - 1; i < msbMask.length * Long.SIZE; i += registerSize )
            msbMask[ i / Long.SIZE ] |= 1L << i % Long.SIZE;
        for( int i = 0; i < lsbMask.length * Long.SIZE; i += registerSize )
            lsbMask[ i / Long.SIZE ] |= 1L << i % Long.SIZE;

        this.seed = seed;
        if ( DEBUG ) System.err.println( "Register size: " + registerSize + " log2m (b): " + log2m + " m: " + m );
        // See the paper.
        switch ( log2m ) {
            case 4:
                alphaMM = 0.673 * m * m; break;
            case 5:
                alphaMM = 0.697 * m * m; break;
            case 6:
                alphaMM = 0.709 * m * m; break;
            default:
                alphaMM = ( 0.7213 / ( 1 + 1.079 / m ) ) * m * m;
        }
    }

    /**
     * Allocates memory for the bit arrays {@code bits} {@code registers}
     * and initiates them with shared memory.
     *
     * @param numVectors The number of vectors required
     * @param sizeInRegisters The total size required (#counters * #registers)
     */
    private void initBitArrays(int numVectors, long sizeInRegisters) {
        bits = new long[ numVectors ][];
        registers = new LongBigList[ numVectors ];

        for( int i = 0; i < numVectors; i++ ) {
            final LongArrayBitVector bitVector = LongArrayBitVector.ofLength( registerSize * Math.min( CHUNK_SIZE, sizeInRegisters - ( (long)i << CHUNK_SHIFT ) ) );
            bits[ i ] = bitVector.bits();
            registers[ i ] = bitVector.asLongBigList( registerSize );
        }
    }

    @Override
    public Object clone(){
        HyperLolLolCounterArray clone;
        try {
            clone = (HyperLolLolCounterArray) super.clone();
            clone.initBitArrays((int)((limit*m + CHUNK_MASK) >>> CHUNK_SHIFT),limit*m);
            clone.msbMask = new long[registerSize];
            clone.lsbMask = new long[registerSize];
            clone.copyOldArraysIntoNew(bits);
            System.arraycopy(msbMask,0,clone.msbMask,0,msbMask.length);
            System.arraycopy(lsbMask,0,clone.lsbMask,0,lsbMask.length);
            return clone;
        }catch (Exception e){}
        return null;
    }

    /**
     * Requests that the HyperLolLol counter needs
     * {@code numberOfNewCounters} more counters.
     * Will increase the counter array if necessary.
     * @param numberOfNewCounters Number of new counters needed
     */
    public void addCounters(long numberOfNewCounters ) {
        if(numberOfNewCounters < 0) {
            throw new IllegalArgumentException(exceptionString + "Requested a negative number of new counters.");
        }

        if(size + numberOfNewCounters > limit) {
            increaseNumberOfCounters(numberOfNewCounters);
        }

        size += numberOfNewCounters;
    }

    /**
     * Calculates the new array size and calls for a resize.
     * Guarantees that at least the number of new counters
     * requested will fit into the array.
     * @param numberOfNewCounters Number of new counters needed
     */
    private void increaseNumberOfCounters(long numberOfNewCounters) {
        double resizePow = Math.ceil(Math.log((limit + numberOfNewCounters) / (float)limit ) * (1/Math.log(resizeFactor)));

        long newLimit = (long)(limit * Math.pow(resizeFactor, resizePow));
        resizeCounterArray(newLimit);
        limit = newLimit;
    }

    /**
     *
     * @param newArraySize Must be strictly larger than previous size
     * @throws IllegalArgumentException If size is smaller then previous
     */
    private void resizeCounterArray(long newArraySize) throws IllegalArgumentException {
        if(limit > newArraySize) {
            throw new IllegalArgumentException(exceptionString + "A smaller array size: " + newArraySize + " than previous " + limit + " was requested.");
        }

        final long sizeInRegisters = newArraySize * m;
        final int numVectors = getNumVectors(sizeInRegisters);

        long oldBits[][] = bits;

        this.msbMask = new long[this.registerSize];
        this.lsbMask = new long[this.registerSize];

        for(int i = this.registerSize - 1; i < this.msbMask.length * 64; i += this.registerSize) {
            this.msbMask[i / 64] |= 1L << i % 64;
        }

        for(int i = 0; i < this.lsbMask.length * 64; i += this.registerSize) {
            this.lsbMask[i / 64] |= 1L << i % 64;
        }

        initBitArrays(numVectors, sizeInRegisters);
        copyOldArraysIntoNew(oldBits);
    }

    /**
     * Calculates the number of vectors required to encode {@code sizeInRegisters}
     * @param sizeInRegisters The total size required (#counters * #registers)
     * @return the number of vectors required
     */
    private int getNumVectors(long sizeInRegisters) {
        return  (int)( ( sizeInRegisters + CHUNK_MASK ) >>> CHUNK_SHIFT );
    }


    /**
     * Copies {@code oldBits} into class variable {@code bits}
     * @param oldBits Bits to be copied
     */
    private void copyOldArraysIntoNew(long[][] oldBits) {
        for(int i = 0; i < oldBits.length; i++) {
            for(int j = 0 ; j < oldBits[i].length; j++) {
                bits[i][j] = oldBits[i][j];
            }
        }
    }

    public HyperLolLolCounterArray extract(LazyLongIterator indices, long numberOfIndices){
        HyperLolLolCounterArray extracted = new HyperLolLolCounterArray(numberOfIndices,numberOfIndices,log2m,seed);
        if(numberOfIndices == 0)
            return extracted;

        long[] temp = new long[extracted.counterLongwords];
        for (int i = 0; i < numberOfIndices ; i++) {
            long index = indices.nextLong();
            getCounter(index,temp);
            extracted.setCounter(temp,i);
        }
        return extracted;
    }

    public long getJenkinsSeed(){
        return seed;
    }

    public void clearCounter(long index){
        long[] list = bits[chunk(index)];
        long offset = offset(index);
        long remaining = registerSize*m;      // The remaining number of bits to be cleared
        long fromRight = offset % Long.SIZE;  // The offset in the current long from {the least significant bit}
        // that should be cleared. We see the least signifcant bit to be the "rightmost" bit
        long mask = (1L << fromRight) - 1L;   // All zeroes from the most significant bit up to the bit at fromRight.
        // The rest is ones.
        int word = (int) (offset / Long.SIZE);// The long to be edited
        while(remaining > 0) { // Still have bits to clear
            // mask currently contains all bits to the left of fromRight. If those zeroes are more than the number of
            // remaining bits we have to set some bits to one.
            if (remaining < Long.SIZE - fromRight)
                // We want to set all bits from the most significant bit up to bit remaining+fromRight to one.
                // To do this we create a mask with zeroes up to bit remaining+fromRight and the rest ones.
                // We then take the bitwise compliment of this mask and /or/ it with the original mask.
                mask |= ~((1L << (fromRight + remaining)) - 1L);
            list[word++] &= mask; // clear the zeroed bits in the current long
            remaining -= Long.SIZE-fromRight;
            mask = 0; // Only the first iteration will have a fromRight offset
            fromRight = 0;
        }

    }

    /**
     * Take the union of the elements of {@code index} of this counter and node {@code fromIndex}
     * from counter {@code from} and place it in {@code index} of this counter.
     * <b>WARNING: It is vital that both counters
     * have the same number of registers and the same register size. Make sure that the
     * counters are created with the same parameters to their constructors</b>
     * @param index
     * @param from
     * @throws IllegalArgumentException If the counters didn't have the same parameters
     */
    public void union(long index, HyperLolLolCounterArray from, long fromIndex) throws IllegalArgumentException{
        if(registerSize != from.registerSize || m != from.m ||
                offset(index) != from.offset(index) || chunk(index) != from.chunk(index)){
            throw new IllegalArgumentException("The counters to union between had different parameters " +
                    "which this function can't handle.");
        }
        long[] bitsToUnionTo = new long[counterLongwords];
        this.getCounter(index, bitsToUnionTo);
        long[] bitsToUnionFrom = new long[counterLongwords];
        from.getCounter(fromIndex, bitsToUnionFrom);
        max(bitsToUnionTo,bitsToUnionFrom); // union the counters

        setCounter(bitsToUnionTo,index);
    }

    /**
     * Adds all elements in {@code from} for all indices
     * @param from
     */
    public void union(HyperLolLolCounterArray from){
        for(int i=0; i < bits.length; i++)
            max(bits[i],from.bits[i]);
    }

    public long getUsedBytes(){
        long bytes = 0;
        for( long[] chunk : bits ) bytes += chunk.length * ( (long)Long.SIZE / Byte.SIZE );
        return bytes;
    }

    public void transferNodeFrom(long index, HyperLolLolCounterArray from){
        int chunk = chunk(index);
        transfer(from.bits[chunk],bits[chunk],index);
    }

    /** Clears all registers and sets a new seed (e.g., using {@link Util#randomSeed()}).
     *
     * @param seed the new seed used to compute the hash function
     */
    public void clear( final long seed ) {
        clear();
        this.seed = seed;
    }

    /** Clears all registers. */
    public void clear() {
        for( long[] a: bits ) Arrays.fill( a, 0 );
    }

    private final static long jenkins( final long x, final long seed ) {
        long a, b, c;

		/* Set up the internal state */
        a = seed + x;
        b = seed;
        c = 0x9e3779b97f4a7c13L; /* the golden ratio; an arbitrary value */

        a -= b; a -= c; a ^= (c >>> 43);
        b -= c; b -= a; b ^= (a << 9);
        c -= a; c -= b; c ^= (b >>> 8);
        a -= b; a -= c; a ^= (c >>> 38);
        b -= c; b -= a; b ^= (a << 23);
        c -= a; c -= b; c ^= (b >>> 5);
        a -= b; a -= c; a ^= (c >>> 35);
        b -= c; b -= a; b ^= (a << 49);
        c -= a; c -= b; c ^= (b >>> 11);
        a -= b; a -= c; a ^= (c >>> 12);
        b -= c; b -= a; b ^= (a << 18);
        c -= a; c -= b; c ^= (b >>> 22);

        return c;
    }


    /** Adds an element to a counter.
     *
     * @param k the index of the counter.
     * @param v the element to be added.
     */
    public void add( final long k, final long v ) {
        final long x = jenkins( v, seed );
        final int j = (int)( x & mMinus1 );
        final int r = Long.numberOfTrailingZeros( x >>> log2m | sentinelMask );
        if ( ASSERTS ) assert r < ( 1 << registerSize ) - 1;
        if ( ASSERTS ) assert r >= 0;
        final LongBigList l = registers[ chunk( k ) ];
        final long offset = ( ( k << log2m ) + j ) & CHUNK_MASK;
        l.set( offset, Math.max( r + 1, l.getLong( offset ) ) );
    }

    /** Returns the array of big lists of registers underlying this array of counters.
     *
     * <p>The main purpose of this method is debugging, as it makes comparing
     * the evolution of the state of two implementations easy.
     *
     * @return the array of big lists of registers underlying this array of counters.
     */

    public LongBigList[] registers() {
        return registers;
    }


    /** Estimates the number of distinct elements that have been added to a given counter so far.
     *
     * <p>This is an low-level method that should be used only after having understood in detail
     * the inner workings of this class.
     *
     * @param bits the bit array containing the counter.
     * @param offset the starting bit position of the counter in <code>bits</code>.
     * @return an approximation of the number of distinct elements that have been added to counter so far.
     */
    public double count( final long[] bits, final long offset ) {
        int remaining = (int)( Long.SIZE - offset % Long.SIZE );
        int word = (int)( offset / Long.SIZE );
        long curr = bits[ word ] >>> offset % Long.SIZE;

        final int registerSize = this.registerSize;
        final int mask = ( 1 << registerSize ) - 1;

        double s = 0;
        int zeroes = 0;
        long r;

        for ( int j = m; j-- != 0; ) {
            if ( remaining >= registerSize ) {
                r = curr & mask;
                curr >>>= registerSize;
                remaining -= registerSize;
            }
            else {
                r = ( curr | bits[ ++word ] << remaining ) & mask;
                curr = bits[ word ] >>> registerSize - remaining;
                remaining += Long.SIZE - registerSize;
            }

            // if ( ASSERTS ) assert r == registers[ chunk( k ) ].getLong( offset( k ) + j ) : "[" + j + "] " + r + "!=" + registers[ chunk( k ) ].getLong( offset( k ) + j );

            if ( r == 0 ) zeroes++;
            s += 1. / ( 1L << r );
        }

        s = alphaMM / s;
        if ( DEBUG ) System.err.println( "Zeroes: " + zeroes );
        if ( zeroes != 0 && s < 5. * m / 2 ) {
            if ( DEBUG ) System.err.println( "Small range correction" );
            return m * Math.log( (double)m / zeroes );
        }
        else return s;
    }

    /** Estimates the number of distinct elements that have been added to a given counter so far.
     *
     * @param k the index of the counter.
     * @return an approximation of the number of distinct elements that have been added to counter <code>k</code> so far.
     */
    public double count( final long k ) {
        return count( bits[ chunk( k ) ], offset( k ) );
    }


    /** Sets the contents of a counter of this {@link HyperLolLolCounterArray} using a provided array of longs.
     *
     * <p><strong>Warning</strong>: this is a low-level method. You must know what you're doing.
     *
     * @param source an array of at least {@link #counterLongwords} longs containing a counter.
     * @param chunkBits the array where the counter will be stored.
     * @param index the index of the counter that will be filled with the provided array.
     * @see #getCounter(long[], long, long[])
     */
    public final void setCounter( final long[] source, final long[] chunkBits, final long index ) {
        if ( longwordAligned ) System.arraycopy( source, 0, chunkBits, (int)( offset( index ) / Long.SIZE ), counterLongwords );
        else {
            // Offset in bits
            final long offset = offset( index );
            // Offsets in elements in the array
            final int longwordOffset = (int)( offset / Long.SIZE );
            // Offset in bits in the word of index longwordOffset
            final int bitOffset = (int)( offset % Long.SIZE );
            final int last = counterLongwords - 1;

            if ( bitOffset == 0 ) {
                for( int i = last; i-- != 0; ) chunkBits[ longwordOffset + i ] = source[ i ];
                chunkBits[ longwordOffset + last ] &= ~counterResidualMask;
                chunkBits[ longwordOffset + last ] |= source[ last ] & counterResidualMask;
            }
            else {
                chunkBits[ longwordOffset ] &= ( 1L << bitOffset ) - 1;
                chunkBits[ longwordOffset ] |= source[ 0 ] << bitOffset;

                for( int i = 1; i < last; i++ ) chunkBits[ longwordOffset + i ] = source[ i - 1 ] >>> Long.SIZE - bitOffset | source[ i ] << bitOffset;

                final int remaining = counterSize % Long.SIZE + bitOffset;

                final long mask = -1L >>> ( Long.SIZE - Math.min( Long.SIZE, remaining ) );
                chunkBits[ longwordOffset + last ] &= ~mask;
                chunkBits[ longwordOffset + last ] |= mask & ( source[ last - 1 ] >>> Long.SIZE - bitOffset | source[ last ] << bitOffset );

                // Note that it is impossible to enter in this conditional unless you use 7 or more bits per register, which is unlikely.
                if ( remaining > Long.SIZE ) {
                    final long mask2 = ( 1L << remaining - Long.SIZE ) - 1;
                    chunkBits[ longwordOffset + last + 1 ] &= ~mask2;
                    chunkBits[ longwordOffset + last + 1 ] |= mask2 & ( source[ last ] >>> Long.SIZE - bitOffset );
                }
            }

            if ( ASSERTS ) {
                final LongArrayBitVector l = LongArrayBitVector.wrap( chunkBits );
                for( int i = 0; i < counterSize; i++ ) assert l.getBoolean( offset + i ) == ( ( source[ i / Long.SIZE ] & ( 1L << i % Long.SIZE ) ) != 0 );
            }
        }
    }

    /** Sets the contents of a counter of this {@link HyperLolLolCounterArray} using a provided array of longs.
     *
     * <p><strong>Warning</strong>: this is a low-level method. You must know what you're doing.
     *
     * @param source an array of at least {@link #counterLongwords} longs containing a counter.
     * @param index the index of the counter that will be filled with the provided array.
     * @see #setCounter(long[], long[], long)
     */
    public void setCounter( final long[] source, final long index ) {
        setCounter( source, bits[ chunk( index ) ], index );
    }

    /** Extracts a counter from this {@link HyperLolLolCounterArray} and writes it into an array of longs.
     *
     * <p><strong>Warning</strong>: this is a low-level method. You must know what you're doing.
     *
     * @param chunkBits the array storing the counter.
     * @param index the index of the counter to be extracted.
     * @param dest an array of at least {@link #counterLongwords} longs where the counter of given index will be written.
     * @see #setCounter(long[], long[], long)
     */
    public final void getCounter( final long[] chunkBits, final long index, final long[] dest ) {
        if ( longwordAligned ) System.arraycopy( chunkBits, (int)( offset( index ) / Long.SIZE ), dest, 0, counterLongwords );
        else {
            // Offset in bits
            final long offset = offset( index );
            // Offsets in elements in the array
            final int longwordOffset = (int)( offset / Long.SIZE );
            // Offset in bits in the word of index longwordOffset
            final int bitOffset = (int)( offset % Long.SIZE );
            final int last = counterLongwords - 1;

            if ( bitOffset == 0 ) {
                for( int i = last; i-- != 0; )
                    dest[ i ] = chunkBits[ longwordOffset + i ];
                dest[ last ] = chunkBits[ longwordOffset + last ] & counterResidualMask;
            }
            else {
                for( int i = 0; i < last; i++ )
                    dest[ i ] = chunkBits[ longwordOffset + i ] >>> bitOffset | chunkBits[ longwordOffset + i + 1 ] << Long.SIZE - bitOffset;
                dest[ last ] = chunkBits[ longwordOffset + last ] >>> bitOffset & counterResidualMask;
            }
        }
    }


    /** Extracts a counter from this {@link HyperLolLolCounterArray} and writes it into an array of longs.
     *
     * <p><strong>Warning</strong>: this is a low-level method. You must know what you're doing.
     *
     * @param index the index of the counter to be extracted.
     * @param dest an array of at least {@link #counterLongwords} longs where the counter of given index will be written.
     * @see #getCounter(long[], long, long[])
     */
    public final void getCounter( final long index, final long[] dest ) {
        getCounter( bits[ chunk( index ) ], index, dest );
    }

    /** Transfers the content of a counter between two parallel array of longwords.
     *
     * <p><strong>Warning</strong>: this is a low-level method. You must know what you're doing.
     *
     * @param source the source array.
     * @param dest the destination array.
     * @param node the node number.
     */
    public final void transfer( final long[] source, final long[] dest, final long node ) {
        if ( longwordAligned ) {
            final int longwordOffset = (int)( offset( node ) / Long.SIZE );
            System.arraycopy( source, longwordOffset, dest, longwordOffset, counterLongwords );
        }
        else {
            // Offset in bits in the array
            final long offset = offset( node );
            // Offsets in elements in the array
            final int longwordOffset = (int)( offset / Long.SIZE );
            // Offset in bits in the word of index longwordOffset
            final int bitOffset = (int)( offset % Long.SIZE );
            final int last = counterLongwords - 1;

            if ( bitOffset == 0 ) {
                for( int i = last; i-- != 0; ) dest[ longwordOffset + i ] = source[ longwordOffset + i ];
                dest[ longwordOffset + last ] &= ~counterResidualMask;
                dest[ longwordOffset + last ] |= source[ longwordOffset + last ] & counterResidualMask;
            }
            else {
                final long mask = -1L << bitOffset;
                dest[ longwordOffset ] &= ~mask;
                dest[ longwordOffset ] |= source[ longwordOffset ] & mask;

                for( int i = 1; i < last; i++ ) dest[ longwordOffset + i ] = source[ longwordOffset + i ];

                final int remaining = ( counterSize + bitOffset ) % Long.SIZE;
                if ( remaining == 0 ) dest[ longwordOffset + last ] = source[ longwordOffset + last ];
                else {
                    final long mask2 = ( 1L << remaining ) - 1;
                    dest[ longwordOffset + last ] &= ~mask2;
                    dest[ longwordOffset + last ] |= mask2 & source[ longwordOffset + last ];
                }
            }

            if ( ASSERTS ) {
                LongArrayBitVector aa = LongArrayBitVector.wrap( source );
                LongArrayBitVector bb = LongArrayBitVector.wrap( dest );
                for( int i = 0; i < counterSize; i++ ) assert aa.getBoolean( offset + i ) == bb.getBoolean( offset + i );
            }
        }
    }


    /** Performs a multiple precision subtraction, leaving the result in the first operand.
     *
     * @param x an array of longs.
     * @param y an array of longs that will be subtracted from <code>x</code>.
     * @param l the length of <code>x</code> and <code>y</code>.
     */
    private static final void subtract( final long[] x, final long[] y, final int l ) {
        boolean borrow = false;

        for( int i = 0; i < l; i++ ) {
            if ( ! borrow || x[ i ]-- != 0 ) borrow = x[ i ] < y[ i ] ^ x[ i ] < 0 ^ y[ i ] < 0; // This expression returns the result of an unsigned strict comparison.
            x[ i ] -= y[ i ];
        }
    }


    /** Performs a multiple precision subtraction, leaving the result in the first operand.
     *
     * @param x an array of longs.
     * @param y an array of longs that will be subtracted from <code>x</code>.
     * @param l the length of <code>x</code> and <code>y</code>.
     */
    private static final void subtractWithModulus( final long[] x, final long[] y, final int l , final int mod) {
        boolean borrow = false;

        for( int i = 0; i < l; i++ ) {
            if ( ! borrow || x[ i ]-- != 0 ) borrow = x[ i ] < y[ i % mod ] ^ x[ i ] < 0 ^ y[ i % mod ] < 0; // This expression returns the result of an unsigned strict comparison.
            x[ i ] -= y[ i % mod ];
        }
    }

    /** Computes the register-by-register maximum of two counters.
     *
     * <p>This method will allocate two temporary arrays. To reduce object creation, use {@link #max(long[], long[], long[], long[])}.
     *
     * @param x a first array of at least {@link #counterLongwords} longs containing a counter.
     * @param y a second array of at least {@link #counterLongwords} longs containing a counter.
     */
    public final void max( final long[] x, final long[] y ) {
        max( x, y, new long[ x.length ], new long[ y.length ] );
    }

    /** Computes the register-by-register maximum of two counters.
     *
     * @param x a first array of at least {@link #counterLongwords} longs containing a counter.
     * @param y a second array of at least {@link #counterLongwords} longs containing a counter.
     * @param accumulator a support array of at least {@link #counterLongwords} longs.
     * @param mask a support array of at least {@link #counterLongwords} longs.
     */
    public final void max( final long[] x, final long[] y, final long[] accumulator, final long[] mask ) {
        final int l = x.length;
        final long[] msbMask = this.msbMask;

		/* We work in two phases. Let H_r (msbMask) by the mask with the
		 * highest bit of each register (of size r) set, and L_r (lsbMask)
		 * be the mask with the lowest bit of each register set.
		 * We describe the algorithm on a single word.
		 *
		 * If the first phase we perform an unsigned strict register-by-register
		 * comparison of x and y, using the formula
		 *
		 * z = (  ( ((y | H_r) - (x & ~H_r)) | (y ^ x) )^ (y | ~x)  ) & H_r
		 *
		 * Then, we generate a register-by-register mask of all ones or
		 * all zeroes, depending on the result of the comparison, using the
		 * formula
		 *
		 * ( ( (z >> r-1 | H_r) - L_r ) | H_r ) ^ z
		 *
		 * At that point, it is trivial to select from x and y the right values.
		 */

        // We load y | H_r into the accumulator.
        for( int i = l; i-- != 0; ) accumulator[ i ] = y[ i ] | msbMask[ i % registerSize ];
        // We subtract x & ~H_r, using mask as temporary storage
        for( int i = l; i-- != 0; ) mask[ i ] = x[ i ] & ~msbMask[ i % registerSize ];
        subtract( accumulator, mask, l );

        // We OR with x ^ y, XOR with ( x | ~y), and finally AND with H_r.
        for( int i = l; i-- != 0; ) accumulator[ i ] = ( ( accumulator[ i ] | ( y[ i ] ^ x[ i ] ) ) ^ ( y[ i ] | ~x[ i ] ) ) & msbMask[ i % registerSize ];

        if ( ASSERTS ) {
            final LongBigList a = LongArrayBitVector.wrap( x ).asLongBigList( registerSize );
            final LongBigList b = LongArrayBitVector.wrap( y ).asLongBigList( registerSize );
            for( int i = 0; i < m; i++ ) {
                long pos = ( i + 1 ) * (long)registerSize - 1;
                assert ( b.getLong( i ) < a.getLong( i ) ) == ( ( accumulator[ (int)( pos / Long.SIZE ) ] & 1L << pos % Long.SIZE ) != 0 );
            }
        }

        // We shift by registerSize - 1 places and put the result into mask.
        final int rMinus1 = registerSize - 1, longSizeMinusRMinus1 = Long.SIZE - rMinus1;
        for( int i = l - 1; i-- != 0; ) mask[ i ] = accumulator[ i ] >>> rMinus1 | accumulator[ i + 1 ] << longSizeMinusRMinus1 | msbMask[ i % registerSize ];
        mask[ l - 1 ] = accumulator[ l - 1 ] >>> rMinus1 | msbMask[ (l - 1) % registerSize ];

        // We subtract L_r from mask.
        subtractWithModulus( mask, lsbMask, l , registerSize);

        // We OR with H_r and XOR with the accumulator.
        for( int i = l; i-- != 0; ) mask[ i ] = ( mask[ i ] | msbMask[ i % registerSize ] ) ^ accumulator[ i ];

        if ( ASSERTS ) {
            final long[] t = x.clone();
            LongBigList a = LongArrayBitVector.wrap( t ).asLongBigList( registerSize );
            LongBigList b = LongArrayBitVector.wrap( y ).asLongBigList( registerSize );
            for( int i = 0; i < Long.SIZE * l / registerSize; i++ ) a.set( i, Math.max( a.getLong( i ), b.getLong( i ) ) );
            // Note: this must be kept in sync with the line computing the result.
            for( int i = l; i-- != 0; ) assert t[ i ] == ( ~mask[ i ] & x[ i ] | mask[ i ] & y[ i ] );
        }

        // Finally, we use mask to select the right bits from x and y and store the result.
        for( int i = l; i-- != 0; ) x[ i ] ^= ( x[ i ] ^ y[ i ] ) & mask[ i ];

    }
}
