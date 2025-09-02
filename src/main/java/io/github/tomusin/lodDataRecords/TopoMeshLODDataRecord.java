package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopoMeshLODDataRecord(
		int versionNumber,
		long vertexRecordsObjectID, int jtEndIndex) implements BufferDeserializable {

	// Page 93, Figure 88
	public static TopoMeshLODDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		long vertexRecordsObjectID = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1);
		return new TopoMeshLODDataRecord(versionNumber, vertexRecordsObjectID, startIndex + 5);
	}
}
