package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

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
		int versionNumber = buffer.get(baseShapeLODData.jtEndIndex());	
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, baseShapeLODData.jtEndIndex() + 1);
		
		Logger.info("VertexShapeLODDataRecord.fromByteBuffer:");
		Logger.info("  startIndex={}, baseShapeLODData.jtEndIndex()={}", startIndex, baseShapeLODData.jtEndIndex());
		Logger.info("  shapeIsTriStripSetShapeNodeElement={}", shapeIsTriStripSetShapeNodeElement);
		Logger.info("  versionNumber={}, vertexBindings={}", versionNumber, vertexBindings);
		
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord = null;
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord = null;
	
		if (shapeIsTriStripSetShapeNodeElement) {
			Logger.info("  Reading TopoMeshTopologicallyCompressedLODDataRecord at offset {}", baseShapeLODData.jtEndIndex() + 9);
			topoMeshTopologicallyCompressedLODDataRecord = TopoMeshTopologicallyCompressedLODDataRecord.fromByteBuffer(buffer, baseShapeLODData.jtEndIndex() + 9);
		} else {
			Logger.info("  Reading TopoMeshCompressedLODDataRecord at offset {}", baseShapeLODData.jtEndIndex() + 9);
			topoMeshCompressedLODDataRecord = TopoMeshCompressedLODDataRecord.fromByteBuffer(buffer, baseShapeLODData.jtEndIndex() + 9);
			Logger.info("  TopoMeshCompressedLODDataRecord ended at offset {}", topoMeshCompressedLODDataRecord.jtEndIndex());
			Logger.info("  TopologicallyCompressedRepDataRecord should start at offset {}", topoMeshCompressedLODDataRecord.jtEndIndex());
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
