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
    
 // --- Delegated ByteBuffer methods ---
    public byte get() {
        return buffer.get();
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

    public int getInt() {
        return buffer.getInt();
    }

    public int getInt(int index) {
        return buffer.getInt(index);
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

    public short getShort(int index) {
        return buffer.getShort(index);
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
     * Returns as int (up to 32 bits)
     */
    public int readBitsAt(int byteIndex, int bitOffset, int nBits) {
        if (nBits < 0 || nBits > 32) throw new IllegalArgumentException("Can read up to 32 bits only");
        if (bitOffset < 0 || bitOffset > 7) throw new IllegalArgumentException("Bit offset must be 0..7");

        int value = 0;

        for (int i = 0; i < nBits; i++) {
            int currentBitIndex = bitOffset + i;
            int currentByteIndex = byteIndex + (currentBitIndex / 8);
            int bitInByte = currentBitIndex % 8;

            byte b = buffer.get(currentByteIndex);

            // Extract bit, always LSB-first inside the byte
            int bit = (b >> bitInByte) & 1;

            if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
                // Put lowest bit we read into lowest position
                value |= (bit << i);
            } else {
                // Put first bit read at highest remaining position
                value = (value << 1) | bit;
            }
        }
        currentBitOffset = byteIndex * 8 + bitOffset + nBits;
        return value;
    }
    
    public int readBitsAt(int bitIndex, int nBits) {
    	 return readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
    }
    
    public int readUnsignedBitsAt(int bitIndex, int nBits) {
        int bits = readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
        int mask = (1 << nBits) - 1;   // keeps only the lowest nBits
        return bits & mask;
    }

    public long readUnsignedBitsAsLongAt(int bitIndex, int nBits) {
        long bits = readBitsAt(bitIndex / 8, bitIndex % 8, nBits);
        long mask = (nBits == 64) ? ~0L : (1L << nBits) - 1; 
        return bits & mask;
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position */
    public int getIntAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 32);
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position */
    public int getIntAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 32);
    }
    
    /** Convenience method: read a full int (32 bits) from any bit position */
    public long getUnsignedIntAtBitPosition(int bitOffset) {
    	int signedInt = readBitsAt(bitOffset / 8, bitOffset % 8, 32);
        return signedInt & 0xFFFFFFFFL;
    }


    /** Convenience method: read a full short (16 bits) from any bit position */
    public int getShortAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 16);
    }
    
    /** Convenience method: read a full short (16 bits) from any bit position */
    public int getShortAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 16);
    }

    /** Convenience method: read a full byte (8 bits) from any bit position */
    public int getByteAtBitPosition(int byteIndex, int bitOffset) {
        return readBitsAt(byteIndex, bitOffset, 8);
    }
    
    /** Convenience method: read a full byte (8 bits) from any bit position */
    public int getByteAtBitPosition(int bitOffset) {
        return readBitsAt(bitOffset / 8, bitOffset % 8, 8);
    }

	public int remaining() {
		return buffer.remaining();
	}

}
