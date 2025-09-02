package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record VertexCountRangeRecord(int minCount, int maxCount, int jtEndIndex) implements BufferDeserializable {

	public static VertexCountRangeRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return new VertexCountRangeRecord(buffer.getInt(startIndex), buffer.getInt(startIndex + 4), startIndex + 8);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
}
