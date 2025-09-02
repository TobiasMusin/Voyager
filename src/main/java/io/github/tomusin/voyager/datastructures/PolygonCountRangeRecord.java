package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record PolygonCountRangeRecord(int minCount, int maxCount, int jtEndIndex) implements BufferDeserializable {

	public static PolygonCountRangeRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return new PolygonCountRangeRecord(buffer.getInt(startIndex), buffer.getInt(startIndex + 4), startIndex + 8);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
}
