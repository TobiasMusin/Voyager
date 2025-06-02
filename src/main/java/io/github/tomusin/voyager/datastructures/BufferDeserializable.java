package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

public interface BufferDeserializable {
    /**
     * Always pass the buffer and the offset
     */
    static BufferDeserializable fromByteBuffer(ByteBuffer buffer, int startIndex) {
        throw new UnsupportedOperationException("Must be overridden");
    }

    int jtEndIndex(); // To expose the next offset
}

