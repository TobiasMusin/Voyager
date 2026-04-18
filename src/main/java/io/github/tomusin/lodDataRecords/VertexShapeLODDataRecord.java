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
		int baseEndIndex = baseShapeLODData.jtEndIndex();
		// CRITICAL FIX: BaseShapeLODDataRecord now correctly consumes only 1 byte instead of 6
		// So we need to account for the 5-byte correction in our offset calculations
		// The mystery 5 bytes that were being skipped were actually part of this record's data!
		int versionByteOffset = baseEndIndex;
		int vertexBindingsOffset = baseEndIndex + 1;
		int topoStartOffset = baseEndIndex + 1 + 8;  // After versionNumber (1 byte) + vertexBindings (8 bytes)
		
		int versionNumber = buffer.get(versionByteOffset);	
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, vertexBindingsOffset);
		
		Logger.info("VertexShapeLODDataRecord.fromByteBuffer:");
		Logger.info("  startIndex={}, baseShapeLODData.jtEndIndex()={}", startIndex, baseEndIndex);
		Logger.info("  versionByteOffset={}, vertexBindingsOffset={}", versionByteOffset, vertexBindingsOffset);
		Logger.info("  topoStartOffset={}", topoStartOffset);
		Logger.info("  shapeIsTriStripSetShapeNodeElement={}", shapeIsTriStripSetShapeNodeElement);
		Logger.info("  versionNumber={}, vertexBindings={}", versionNumber, vertexBindings);
		
		TopoMeshTopologicallyCompressedLODDataRecord topoMeshTopologicallyCompressedLODDataRecord = null;
		TopoMeshCompressedLODDataRecord topoMeshCompressedLODDataRecord = null;
	
		if (shapeIsTriStripSetShapeNodeElement) {
			Logger.info("  Reading TopoMeshTopologicallyCompressedLODDataRecord at offset {}", topoStartOffset);
			topoMeshTopologicallyCompressedLODDataRecord = TopoMeshTopologicallyCompressedLODDataRecord.fromByteBuffer(buffer, topoStartOffset);
		} else {
			Logger.info("  Reading TopoMeshCompressedLODDataRecord at offset {}", topoStartOffset);
			topoMeshCompressedLODDataRecord = TopoMeshCompressedLODDataRecord.fromByteBuffer(buffer, topoStartOffset);
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
