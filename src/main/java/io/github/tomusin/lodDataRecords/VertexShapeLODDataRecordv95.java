package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 90, Figure 85
public record VertexShapeLODDataRecordv95(
		int versionNumber, 
		long vertexBindings, 
		TopoMeshCompressedLODDataRecordv95 topoMeshCompressedLODDataRecord,
		TopoMeshTopologicallyCompressedLODDataRecordv95 topoMeshTopologicallyCompressedLODDataRecord
		) implements BufferDeserializable {

	public static VertexShapeLODDataRecordv95 fromByteBuffer(ByteBuffer buffer, int startIndex, boolean shapeIsTriStripSetShapeNodeElement) {
		int versionNumber = buffer.getShort(startIndex);
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex + 2);
		TopoMeshCompressedLODDataRecordv95 topoMeshCompressedLODDataRecord = null;
		TopoMeshTopologicallyCompressedLODDataRecordv95 topoMeshTopologicallyCompressedLODDataRecord = null;
		try {
			topoMeshCompressedLODDataRecord = TopoMeshCompressedLODDataRecordv95.fromByteBuffer(buffer, startIndex + 10);
			System.out.println("topoMeshCompressedLODDataRecord: " + topoMeshCompressedLODDataRecord);
		} catch (Exception e) {
			
		}
		try {
			topoMeshTopologicallyCompressedLODDataRecord = TopoMeshTopologicallyCompressedLODDataRecordv95.fromByteBuffer(buffer, startIndex + 10);
			System.out.println("topoMeshTopologicallyCompressedLODDataRecord: " + topoMeshTopologicallyCompressedLODDataRecord);
		} catch (Exception e) {
			
		}
		return new VertexShapeLODDataRecordv95(versionNumber, vertexBindings, topoMeshCompressedLODDataRecord, topoMeshTopologicallyCompressedLODDataRecord);
	}
	
	@Override
	public int jtEndIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	
}
