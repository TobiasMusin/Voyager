package io.github.tomusin.lodDataRecords;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 90, Figure 85
public record VertexShapeLODDataRecord(
		BaseShapeLODDataRecord baseShapeLODData, 
		int versionNumber, 
		long vertexBindings, 
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord,
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord
		) implements BufferDeserializable {

	public static VertexShapeLODDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex, boolean shapeIsTriStripSetShapeNodeElement) {
		BaseShapeLODDataRecord baseShapeLODData = BaseShapeLODDataRecord.fromByteBuffer(buffer, startIndex);
		int baseEndIndex = baseShapeLODData.jtEndIndex();
		int versionByteOffset = baseEndIndex;
		int vertexBindingsOffset = baseEndIndex + 1;
		int topoStartOffset = baseEndIndex + 1 + 8;
		
		int versionNumber = buffer.get(versionByteOffset);	
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, vertexBindingsOffset);
		
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord = null;
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord = null;
	
		if (shapeIsTriStripSetShapeNodeElement) {
			topoMeshTopologicallyCompressedLODDataRecord = TopoMeshTopologicallyCompressedLODDataRecord.fromByteBuffer(buffer, topoStartOffset);
		} else {
			topoMeshCompressedLODDataRecord = TopoMeshCompressedLODDataRecord.fromByteBuffer(buffer, topoStartOffset);
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