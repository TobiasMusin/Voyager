package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;
import java.util.Arrays;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record VecI32(int count, int[] valueArray, int jtEndIndex) {
	
	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VecI32 other)) return false;
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
        return "VecI32[count=" + count + ", valueArray=" + Arrays.toString(valueArray) + "]";
    }
    
    public static VecI32 fromByteBuffer(BitByteBuffer buffer, int startIndex) {
    	int count = buffer.getInt(startIndex);
    	int[] valueArray = new int[count];
    	for (int i = 0; i < count; i++) {
    		valueArray[i] = buffer.getInt(startIndex + 1 + i * 4);
    	}
    	return new VecI32(count, valueArray, startIndex + 1 + count * 4);
    }
}
