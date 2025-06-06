package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 92, Figure 87
public record TopoMeshCompressedLODDataRecord(
		TopoMeshLODDataRecord topoMeshLODDataRecord,
		int versionNumber,
		TopoMeshCompressedRepDataRecord topoMeshCompressedRepDataRecord
		) implements BufferDeserializable {
	public static TopoMeshCompressedLODDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		TopoMeshLODDataRecord topoMeshLODDataRecord = TopoMeshLODDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = buffer.getShort(topoMeshLODDataRecord.jtEndIndex());
		TopoMeshCompressedRepDataRecord topoMeshCompressedRepDataRecord = TopoMeshCompressedRepDataRecord.fromByteBuffer(buffer, topoMeshLODDataRecord.jtEndIndex() + 1);
		return new TopoMeshCompressedLODDataRecord(topoMeshLODDataRecord, versionNumber, topoMeshCompressedRepDataRecord);
	}

	@Override
	public int jtEndIndex() {
		// TODO Auto-generated method stub
		return 0; // For now
	}
}
