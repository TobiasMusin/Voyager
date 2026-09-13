package io.github.tomusin.voyager.datastructures;

import java.util.Arrays;

import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record VecU32(int count, long[] valueArray, int jtEndIndex) {
	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VecU32 other)) return false;
        return count == other.count && Arrays.equals(valueArray, other.valueArray);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(count);
        result = 31 * result + Arrays.hashCode(valueArray);
        return result;
    }

    @Override
    public String toString() {
        return "VecU32[count=" + count + ", valueArray=" + Arrays.toString(valueArray) + "]";
    }
    
    public static VecU32 fromByteBuffer(BitByteBuffer buffer, int startIndex) {
    	// startIndex is now a BIT OFFSET (not byte offset) to handle non-aligned CDP data
    	int count = buffer.getIntAtBitPosition(startIndex);
    	if (count < 0 || count > 10_000_000) {
    		throw new IllegalArgumentException("VecU32.fromByteBuffer: unreasonable count=" + count + " at bit offset " + startIndex);
    	}
    	// Each value is 4 bytes; the count field itself is 32 bits (4 bytes from bit startIndex).
    	// Data must fit within the buffer (convert bit offset to byte offset for capacity check).
    	int byteStart = startIndex / 8;
    	if ((long) byteStart + 4 + (long) count * 4 > buffer.capacity()) {
    		throw new IllegalArgumentException("VecU32.fromByteBuffer: count=" + count + " at bit offset " + startIndex + " would exceed buffer capacity " + buffer.capacity());
    	}
    	long[] valueArray = new long[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = ReadFromBufferUtils.readUnsignedInt(buffer, (startIndex + 32 + i * 32) / 8);
    	}
    	return new VecU32(count, valueArray, startIndex + 32 + count * 32);
    }
    
    public static VecU32 fromByteBuffer(BitByteBuffer buffer, int startIndex, int count) {
    	long[] valueArray = new long[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + i * 4);
    	}
    	return new VecU32(count, valueArray, startIndex + count * 4);
    }
    
    /**
     * Read a VecU32 from byte-aligned buffer position (reads count from buffer first).
     * Uses little-endian byte order (normal JT file reads), NOT bit-stream order.
     */
    public static VecU32 fromByteBufferAligned(BitByteBuffer buffer, int byteStartIndex) {
    	int count = buffer.getInt(byteStartIndex);
    	if (count < 0 || count > 10_000_000) {
    		throw new IllegalArgumentException("VecU32.fromByteBufferAligned: unreasonable count=" + count + " at byte offset " + byteStartIndex);
    	}
    	// Each value is 4 bytes; the count field is 4 bytes — total must fit in remaining buffer.
    	if ((long) byteStartIndex + 4 + (long) count * 4 > buffer.capacity()) {
    		throw new IllegalArgumentException("VecU32.fromByteBufferAligned: count=" + count + " at byte offset " + byteStartIndex + " would exceed buffer capacity " + buffer.capacity());
    	}
    	long[] valueArray = new long[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = ReadFromBufferUtils.readUnsignedInt(buffer, byteStartIndex + 4 + i * 4);
    	}
    	return new VecU32(count, valueArray, byteStartIndex + 4 + count * 4);
    }
}
