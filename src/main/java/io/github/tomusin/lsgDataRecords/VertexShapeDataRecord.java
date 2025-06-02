package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record VertexShapeDataRecord(BaseShapeDataRecord baseShapeDataRecord, int versionNumber, long vertexBinding) implements BufferDeserializable {
	
	public static VertexShapeDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		BaseShapeDataRecord baseShapeDataRecord = ReadNodesFromBufferUtils.readBaseShapeData(startIndex, buffer);
//		BaseShapeDataRecord baseShapeDataRecord = ReadNodesFromBufferUtils.readBaseShapeDataFromOlderVersionsOrShittyWriters(startIndex, buffer);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, baseShapeDataRecord.jtEndIndex());
		long vertexBinding = ReadFromBufferUtils.readUnsignedLong(buffer, baseShapeDataRecord.jtEndIndex() + 1);
		return new VertexShapeDataRecord(baseShapeDataRecord, versionNumber, vertexBinding);
	}
	
	@Override
	public int jtEndIndex() {
		return baseShapeDataRecord.jtEndIndex() + 9;
	}
	
}
