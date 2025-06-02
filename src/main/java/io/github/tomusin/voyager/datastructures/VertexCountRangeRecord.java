package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

public record VertexCountRangeRecord(int minCount, int maxCount, int jtEndIndex) implements BufferDeserializable {

	public static VertexCountRangeRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		return new VertexCountRangeRecord(buffer.getInt(startIndex), buffer.getInt(startIndex + 4), startIndex + 8);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
}
