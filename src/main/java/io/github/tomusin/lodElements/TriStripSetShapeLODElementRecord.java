package io.github.tomusin.lodElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lodDataRecords.VertexShapeLODDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

// Page 88, Figure 81
public record TriStripSetShapeLODElementRecord(VertexShapeLODDataRecord vertexShapeLODDataRecord, int versionNumber) implements BufferDeserializable {

	public static TriStripSetShapeLODElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		// Do we need a LogicalElementHeader here? (p. 88/96 Figure 81)
		// It doesn't make sense to me right now, especially the values I get, but it seems to fix the buffer index misalignment later on
		LogicalElementHeaderRecord logicalElementHeaderRecord = ReadNodesFromBufferUtils.readLogicalElementHeader(buffer, startIndex);
		VertexShapeLODDataRecord vertexShapeLODDataRecord = VertexShapeLODDataRecord.fromByteBuffer(buffer, logicalElementHeaderRecord.jtEndIndex(), true);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, vertexShapeLODDataRecord.jtEndIndex());
		return new TriStripSetShapeLODElementRecord(vertexShapeLODDataRecord, versionNumber);
	}
	@Override
	public int jtEndIndex() {
		return vertexShapeLODDataRecord.jtEndIndex() + 1;
	}

}
