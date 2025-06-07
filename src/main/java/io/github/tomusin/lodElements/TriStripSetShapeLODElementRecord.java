package io.github.tomusin.lodElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lodDataRecords.VertexShapeLODDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

// Page 88, Figure 81
public record TriStripSetShapeLODElementRecord(VertexShapeLODDataRecord vertexShapeLODDataRecord, int versionNumber) implements BufferDeserializable {

	public static TriStripSetShapeLODElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		VertexShapeLODDataRecord vertexShapeLODDataRecord = VertexShapeLODDataRecord.fromByteBuffer(buffer, startIndex, true);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, vertexShapeLODDataRecord.jtEndIndex());
		return new TriStripSetShapeLODElementRecord(vertexShapeLODDataRecord, versionNumber);
	}
	@Override
	public int jtEndIndex() {
		return vertexShapeLODDataRecord.jtEndIndex() + 1;
	}

}
