package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopoMeshCompressedLODDataRecordv95(
		TopoMeshLODDataRecordv95 topoMeshLODDataRecord,
		int versionNumber,
		TopoMeshCompressedRepDataRecord topoMeshCompressedRepDataRecord
		) {
	public static TopoMeshCompressedLODDataRecordv95 fromByteBuffer(ByteBuffer buffer, int startIndex) {
		TopoMeshLODDataRecordv95 topoMeshLODDataRecord = TopoMeshLODDataRecordv95.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, topoMeshLODDataRecord.jtEndIndex());
		TopoMeshCompressedRepDataRecord topoMeshCompressedRepDataRecord = TopoMeshCompressedRepDataRecord.fromByteBuffer(buffer, topoMeshLODDataRecord.jtEndIndex() + 1);
		return new TopoMeshCompressedLODDataRecordv95(topoMeshLODDataRecord, versionNumber, topoMeshCompressedRepDataRecord);
	}
}
