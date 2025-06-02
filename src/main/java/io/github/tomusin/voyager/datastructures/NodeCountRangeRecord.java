package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

public record NodeCountRangeRecord(int minCount, int maxCount, int jtEndIndex) implements BufferDeserializable {

	public static NodeCountRangeRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		return new NodeCountRangeRecord(buffer.getInt(startIndex), buffer.getInt(startIndex + 4), startIndex + 8);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
}
