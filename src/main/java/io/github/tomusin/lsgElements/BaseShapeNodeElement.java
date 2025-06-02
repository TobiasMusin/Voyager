package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseShapeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record BaseShapeNodeElement(BaseShapeDataRecord baseShapeDataRecord) implements BufferDeserializable {
	public static BaseShapeNodeElement fromByteBuffer(ByteBuffer buffer, int startIndex) {
//		BaseShapeDataRecord baseShapeDataRecord = ReadNodesFromBufferUtils.readBaseShapeDataFromOlderVersionsOrShittyWriters(startIndex, buffer);
		BaseShapeDataRecord baseShapeDataRecord = ReadNodesFromBufferUtils.readBaseShapeData(startIndex, buffer);
		return new BaseShapeNodeElement(baseShapeDataRecord);
	}

	@Override
	public int jtEndIndex() {
		return baseShapeDataRecord.jtEndIndex();
	}
}
