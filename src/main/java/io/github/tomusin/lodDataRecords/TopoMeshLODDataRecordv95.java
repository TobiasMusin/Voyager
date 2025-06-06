package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopoMeshLODDataRecordv95(
		int versionNumber,
		long vertexRecordsObjectID, int jtEndIndex) implements BufferDeserializable {

	// Page 93, Figure 88
	public static TopoMeshLODDataRecordv95 fromByteBuffer(ByteBuffer buffer, int startIndex) {
		int versionNumber = buffer.getShort(startIndex);
		long vertexRecordsObjectID = buffer.getInt(startIndex + 2);
		return new TopoMeshLODDataRecordv95(versionNumber, vertexRecordsObjectID, startIndex + 5);
	}
}
