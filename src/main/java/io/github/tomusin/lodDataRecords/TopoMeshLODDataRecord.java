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
		
		org.tinylog.Logger.info("TopoMeshLODDataRecord.fromByteBuffer:");
		org.tinylog.Logger.info("  startIndex={}, versionNumber={}, vertexRecordsObjectID={}", startIndex, versionNumber, vertexRecordsObjectID);
		org.tinylog.Logger.info("  Consumed: 1 (version) + 4 (objectID) = 5 bytes");
		org.tinylog.Logger.info("  Next offset: {} + 5 = {}", startIndex, startIndex + 5);
		
		return new TopoMeshLODDataRecord(versionNumber, vertexRecordsObjectID, startIndex + 5);
	}
}
