package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 90, Figure 85
public record VertexShapeLODDataRecord(
		BaseShapeLODDataRecord baseShapeLODData, 
		int versionNumber, 
		long vertexBindings, 
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord,
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord
		) implements BufferDeserializable {

	public static VertexShapeLODDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex, boolean shapeIsTriStripSetShapeNodeElement) {
		BaseShapeLODDataRecord baseShapeLODData = BaseShapeLODDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = buffer.get(baseShapeLODData.jtEndIndex());
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, baseShapeLODData.jtEndIndex() + 1);
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord = null; // Replace with proper Record
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord = null;
		
		if (shapeIsTriStripSetShapeNodeElement) {
			topoMeshTopologicallyCompressedLODDataRecord = TopoMeshTopologicallyCompressedLODDataRecord.fromByteBuffer(buffer, baseShapeLODData.jtEndIndex() + 9);
		}
		
		return new VertexShapeLODDataRecord(baseShapeLODData, versionNumber, vertexBindings, topoMeshCompressedLODDataRecord, topoMeshTopologicallyCompressedLODDataRecord);
	}
	
	@Override
	public int jtEndIndex() {
		if (topoMeshTopologicallyCompressedLODDataRecord != null)
	        return topoMeshTopologicallyCompressedLODDataRecord.jtEndIndex();
	    if (topoMeshCompressedLODDataRecord != null)
	        return topoMeshCompressedLODDataRecord.jtEndIndex();
	    return baseShapeLODData.jtEndIndex() + 1 + 8;
	}
	
}
