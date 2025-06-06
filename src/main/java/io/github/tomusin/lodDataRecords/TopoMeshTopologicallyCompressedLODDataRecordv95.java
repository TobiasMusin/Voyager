package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 96, Figure 91
public record TopoMeshTopologicallyCompressedLODDataRecordv95(
		TopoMeshLODDataRecord topoMeshLODDataRecord,
		int versionNumber,
		TopologicallyCompressedRepDataRecord topologicallyCompressedRepDataRecord
		) implements BufferDeserializable {

	public static TopoMeshTopologicallyCompressedLODDataRecordv95 fromByteBuffer(ByteBuffer buffer, int startIndex) {
		TopoMeshLODDataRecord topoMeshLODDataRecord = TopoMeshLODDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, topoMeshLODDataRecord.jtEndIndex());
		TopologicallyCompressedRepDataRecord topologicallyCompressedRepDataRecord = TopologicallyCompressedRepDataRecord.fromByteBuffer(buffer, topoMeshLODDataRecord.jtEndIndex() + 1);
		return new TopoMeshTopologicallyCompressedLODDataRecordv95(topoMeshLODDataRecord, versionNumber, topologicallyCompressedRepDataRecord);
	}
	
	@Override
	public int jtEndIndex() {
		return topologicallyCompressedRepDataRecord.jtEndIndex();
	}

}
