package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
    
    public static VecU32 fromByteBuffer(ByteBuffer buffer, int startIndex) {
    	int count = buffer.getInt(startIndex);
    	long[] valueArray = new long[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1 + i * 4);
    	}
    	return new VecU32(count, valueArray, startIndex + 1 + count * 4);
    }
    
    public static VecU32 fromByteBuffer(ByteBuffer buffer, int startIndex, int count) {
    	long[] valueArray = new long[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + i * 4);
    	}
    	return new VecU32(count, valueArray, startIndex + 1 + count * 4);
    }
}
