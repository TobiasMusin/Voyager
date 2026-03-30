package io.github.tomusin.voyager.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BitByteBuffer {
    private final ByteBuffer buffer;
    private int currentBitOffset = 0;

    public BitByteBuffer(ByteBuffer buffer) {
    	ByteOrder order = buffer.order();
        this.buffer = buffer.duplicate(); // safe copy for random access
        this.buffer.order(order);
        currentBitOffset = buffer.position() * 8;
    }
    
 // --- Delegated ByteBuffer methods (Signed) ---
    public byte get() {
        return buffer.get();
    }
    
    /** Get unsigned byte (returns int 0-255) */
    public int getUnsignedByte() {
        return buffer.get() & 0xFF;
    }
    
    public int getBitOffset() {
    	return currentBitOffset;
    }
    
    public void setBitOffset(int offset) {
    	currentBitOffset = offset;
    }

    public byte get(int index) {
        return buffer.get(index);
    }

    /** Get unsigned byte at index (returns int 0-255) */
    public int getUnsignedByte(int index) {
        return buffer.get(index) & 0xFF;
    }

    public int getInt() {
        return buffer.getInt();
    }

    /** Get unsigned int (returns long 0-4294967295) */
    public long getUnsignedInt() {
        return buffer.getInt() & 0xFFFFFFFFL;
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    /** Get unsigned int at index (returns long 0-4294967295) */
    public long getUnsignedInt(int index) {
        return buffer.getInt(index) & 0xFFFFFFFFL;
    }
    
    public long getLong() {
        return buffer.getLong();
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }
    
    public float getFloat() {
        return buffer.getFloat();
    }

    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    public short getShort() {
        return buffer.getShort();
    }

    /** Get unsigned short (returns int 0-65535) */
    public int getUnsignedShort() {
        return buffer.getShort() & 0xFFFF;
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    /** Get unsigned short at index (returns int 0-65535) */
    public int getUnsignedShort(int index) {
        return buffer.getShort(index) & 0xFFFF;
    }

    public void get(byte[] dst) {
        buffer.get(dst);
    }

    public void get(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
    }

    public BitByteBuffer duplicate() {
        return new BitByteBuffer(buffer.duplicate());
    }

    public int position() {
        return buffer.position();
    }

    public BitByteBuffer position(int newPosition) {
        buffer.position(newPosition);
        return this;
    }

    public int limit() {
        return buffer.limit();
    }

    public BitByteBuffer limit(int newLimit) {
        buffer.limit(newLimit);
        return this;
    }

    public int capacity() {
        return buffer.capacity();
    }

    /**
     * Indexed read of n bits starting at a byte index and bit offset (0 = MSB)
     * Bits are always read MSB-first (big-endian bit stream order).
     * This matches the C++ codec implementations (Bitlength.cpp, Arithmetic.cpp).
     * Returns signed int (range depends on nBits, e.g., -128 to 127 for 8 bits).
     * 
     * Bit numbering within a byte: 7 6 5 4 3 2 1 0 (MSB to LSB)
     * Read order: Always from high bit to low bit
     */
    public int readBitsAt(int byteIndex, int bitOffset, int nBits) {
        if (nBits < 0 || nBits > 32) throw new IllegalArgumentException("Can read up to 32 bits only");
        if (bitOffset < 0 || bitOffset > 7) throw new IllegalArgumentException("Bit offset must be 0..7");

        int value = 0;

        for (int i = 0; i < nBits; i++) {
            // Calculate absolute bit position in the stream
            int totalBitOffset = byteIndex * 8 + bitOffset + i;
            int currentByteIndex = totalBitOffset / 8;
            
            // Bit numbering: 7=MSB (leftmost), 0=LSB (rightmost)
            // Offset within byte: 0→7, 1→6, 2→5, ..., 7→0 (count from MSB)
            int bitInByte = 7 - (totalBitOffset % 8);

            byte b = buffer.get(currentByteIndex);

            // Extract bit at the calculated position (MSB-first reading)
            int bit = (b >> bitInByte) & 1;

            // Accumulate into result MSB-first (shift left, add new bit)
            value = (value << 1) | bit;
        }
        currentBitOffset = byteIndex * 8 + bitOffset + nBits;
        return value;
    }
    
    public int readBitsAt(int bitIndex, int nBits) {
    	 return readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
    }
    
    /** Read n bits as unsigned int (returns 0 to 2^nBits - 1) */
    public int readUnsignedBitsAt(int byteIndex, int bitOffset, int nBits) {
        int bits = readBitsAt(byteIndex, bitOffset, nBits);
        int mask = (1 << nBits) - 1;   // keeps only the lowest nBits
        return bits & mask;
    }
    
    public int readUnsignedBitsAt(int bitIndex, int nBits) {
        int bits = readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
        int mask = (1 << nBits) - 1;   // keeps only the lowest nBits
        return bits & mask;
    }

    /** Read n bits as unsigned long (returns 0 to 2^nBits - 1) */
    public long readUnsignedBitsAsLongAt(int byteIndex, int bitOffset, int nBits) {
        long bits = readBitsAt(byteIndex, bitOffset, nBits);
        long mask = (nBits == 64) ? ~0L : (1L << nBits) - 1; 
        return bits & mask;
    }

    public long readUnsignedBitsAsLongAt(int bitIndex, int nBits) {
        long bits = readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
        long mask = (nBits == 64) ? ~0L : (1L << nBits) - 1; 
        return bits & mask;
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position (signed) */
    public int getIntAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 32);
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position (signed) */
    public int getIntAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 32);
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position (unsigned) */
    public long getUnsignedIntAtBitPosition(int byteIndex, int bitOffset) {
    	int signedInt = readBitsAt(byteIndex, bitOffset, 32);
        return signedInt & 0xFFFFFFFFL;
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position (unsigned) */
    public long getUnsignedIntAtBitPosition(int bitOffset) {
    	int signedInt = readBitsAt(bitOffset / 8, bitOffset % 8, 32);
        return signedInt & 0xFFFFFFFFL;
    }

    /** Convenience method: read a full short (16 bits) from any bit position (signed) */
    public int getShortAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 16);
    }
    
    /** Convenience method: read a full short (16 bits) from any bit position (signed) */
    public int getShortAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 16);
    }
    
    /** Convenience method: read a full short (16 bits) from any bit position (unsigned, returns 0-65535) */
    public int getUnsignedShortAtBitPosition(int byteIndex, int bitOffset) {
        int bits = readBitsAt(byteIndex, bitOffset, 16);
        return bits & 0xFFFF;
    }
    
    /** Convenience method: read a full short (16 bits) from any bit position (unsigned, returns 0-65535) */
    public int getUnsignedShortAtBitPosition(int bitOffset) {
        int bits = readBitsAt(bitOffset / 8, bitOffset % 8, 16);
        return bits & 0xFFFF;
    }

    /** Convenience method: read a full byte (8 bits) from any bit position (unsigned, returns 0-255) */
    public int getByteAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 8) & 0xFF;
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position (unsigned, returns 0-255) */
    public int getByteAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 8) & 0xFF;
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position (signed, with sign extension to -128 to 127) */
    public int getSignedByteAtBitPosition(int byteIndex, int bitOffset) {
        int bits = readBitsAt(byteIndex, bitOffset, 8);
        // Sign extend: if bit 7 is set, fill bits 8-31 with 1s
        return (byte) bits;  // Cast to byte forces sign extension when promoted back to int
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position (signed, with sign extension to -128 to 127) */
    public int getSignedByteAtBitPosition(int bitOffset) {
        int bits = readBitsAt(bitOffset / 8, bitOffset % 8, 8);
        // Sign extend: if bit 7 is set, fill bits 8-31 with 1s
        return (byte) bits;  // Cast to byte forces sign extension when promoted back to int
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position (unsigned, returns 0-255) - alias for getByteAtBitPosition */
    public int getUnsignedByteAtBitPosition(int byteIndex, int bitOffset) {
        return getByteAtBitPosition(byteIndex, bitOffset);
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position (unsigned, returns 0-255) - alias for getByteAtBitPosition */
    public int getUnsignedByteAtBitPosition(int bitOffset) {
        return getByteAtBitPosition(bitOffset);
    }

	public int remaining() {
		return buffer.remaining();
	}

}
