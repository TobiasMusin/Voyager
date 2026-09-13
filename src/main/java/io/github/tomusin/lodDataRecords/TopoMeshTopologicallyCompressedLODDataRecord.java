package io.github.tomusin.lodDataRecords;


import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 96, Figure 91
public record TopoMeshTopologicallyCompressedLODDataRecord(
		TopoMeshLODDataRecord topoMeshLODDataRecord,
		int versionNumber,
		TopologicallyCompressedRepDataRecord topologicallyCompressedRepDataRecord
		) implements BufferDeserializable {

	public static TopoMeshTopologicallyCompressedLODDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		TopoMeshLODDataRecord topoMeshLODDataRecord = TopoMeshLODDataRecord.fromByteBuffer(buffer, startIndex + 1);
		int topoCompRepDataByteOffset = topoMeshLODDataRecord.jtEndIndex();
		if (hasEmbeddedLogicalElementHeader(buffer, topoCompRepDataByteOffset)) {
			topoCompRepDataByteOffset += 25;
		}
		
		TopologicallyCompressedRepDataRecord topologicallyCompressedRepDataRecord = 
			TopologicallyCompressedRepDataRecord.fromByteBuffer(buffer, topoCompRepDataByteOffset);
		return new TopoMeshTopologicallyCompressedLODDataRecord(topoMeshLODDataRecord, versionNumber, topologicallyCompressedRepDataRecord);
	}

	private static boolean hasEmbeddedLogicalElementHeader(BitByteBuffer buffer, int startIndex) {
		return startIndex + 25 <= buffer.capacity()
				&& buffer.getInt(startIndex) == 0xBE4CF830
				&& buffer.getShort(startIndex + 4) == (short) 0x4FBC
				&& buffer.getShort(startIndex + 6) == (short) 0x5F9B
				&& buffer.getUnsignedByte(startIndex + 8) == 0xB9
				&& buffer.getUnsignedByte(startIndex + 9) == 0x26
				&& buffer.getUnsignedByte(startIndex + 10) == 0x92
				&& buffer.getUnsignedByte(startIndex + 11) == 0x78
				&& buffer.getUnsignedByte(startIndex + 12) == 0xD2
				&& buffer.getUnsignedByte(startIndex + 13) == 0xE1
				&& buffer.getUnsignedByte(startIndex + 14) == 0x09
				&& buffer.getUnsignedByte(startIndex + 15) == 0x01
				&& ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 16) == 0
				&& ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 17) != 0;
	}
	
	@Override
	public int jtEndIndex() {
		return topologicallyCompressedRepDataRecord.jtEndIndex();
	}

}