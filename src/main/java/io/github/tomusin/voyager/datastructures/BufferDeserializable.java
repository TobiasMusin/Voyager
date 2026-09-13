package io.github.tomusin.voyager.datastructures;


import io.github.tomusin.voyager.utils.BitByteBuffer;

public interface BufferDeserializable {
    /**
     * Always pass the buffer and the offset
     */
    static BufferDeserializable fromByteBuffer(BitByteBuffer buffer, int startIndex) {
        throw new UnsupportedOperationException("Must be overridden");
    }

    int jtEndIndex(); // To expose the next offset
}

