package io.github.tomusin.voyager.datastructures;

import java.util.Arrays;

public record VecF32(int count, float[] valueArray) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VecF32 other)) return false;
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
        return "VecF32[count=" + count + ", valueArray=" + Arrays.toString(valueArray) + "]";
    }
}

