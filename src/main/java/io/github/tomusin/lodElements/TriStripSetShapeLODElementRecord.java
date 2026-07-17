package io.github.tomusin.lodElements;

import io.github.tomusin.lodDataRecords.VertexShapeLODDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 88, Figure 81
public record TriStripSetShapeLODElementRecord(VertexShapeLODDataRecord vertexShapeLODDataRecord, int versionNumber) implements BufferDeserializable {

	public static TriStripSetShapeLODElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		// startIndex is already past the LogicalElementHeader (ShapeLOD0DataSegment passes jtEndIndex of the header)
		VertexShapeLODDataRecord vertexShapeLODDataRecord = VertexShapeLODDataRecord.fromByteBuffer(buffer, startIndex, true);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, vertexShapeLODDataRecord.jtEndIndex());
		return new TriStripSetShapeLODElementRecord(vertexShapeLODDataRecord, versionNumber);
	}
	@Override
	public int jtEndIndex() {
		return vertexShapeLODDataRecord.jtEndIndex() + 1;
	}

}
