package io.github.tomusin.voyager.utils;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.lsgDataRecords.BaseShapeDataRecord;
import io.github.tomusin.lsgDataRecords.GroupNodeDataRecord;
import io.github.tomusin.lsgElements.PartitionNodeElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.BasePropertyAtomDataRecord;
import io.github.tomusin.voyager.datastructures.BBoxF32;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.datastructures.NodeCountRangeRecord;
import io.github.tomusin.voyager.datastructures.PolygonCountRangeRecord;
import io.github.tomusin.voyager.datastructures.VertexCountRangeRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils.MbStringResult;

public class ReadNodesFromBufferUtils {
	public static PartitionNodeElementRecord readPartitionNode(BitByteBuffer buffer, int startIndex) {
	    // Read base node data
	    BaseNodeDataRecord baseNodeDataRecord = readBaseNodeData(startIndex, buffer);

	    // Read group node data
	    int groupNodeStart = baseNodeDataRecord.jtEndIndex();
	    GroupNodeDataRecord groupNodeDataRecord = readGroupNodeData(groupNodeStart, buffer, baseNodeDataRecord);

	    // Read partition flags
	    int partitionFlagsStart = groupNodeDataRecord.jtEndIndex();
	    int partitionFlags = buffer.getInt(partitionFlagsStart);

	    // Read MbString (file name)
	    int mbStringStart = partitionFlagsStart + 1;
	    MbStringResult mbStringResult = ReadFromBufferUtils.readMbString(buffer, mbStringStart);
	    String fileName = mbStringResult.value();
	    int afterString = mbStringResult.nextIndex();

	    // Read BBoxF32
	    BBoxF32 untransformedBBox = ReadFromBufferUtils.readBBoxF32(buffer, afterString);

	    // Read area
	    int areaIndex = afterString + 6 * 4;
	    float area = ReadFromBufferUtils.readF32(buffer, areaIndex);

	    // Read vertex, node, and polygon counts
	    int countsStart = areaIndex + 4;
	    int minVertexCount = buffer.getInt(countsStart);
	    int maxVertexCount = buffer.getInt(countsStart + 4);
	    int minNodeCount = buffer.getInt(countsStart + 8);
	    int maxNodeCount = buffer.getInt(countsStart + 12);
	    int minPolygonCount = buffer.getInt(countsStart + 16);
	    int maxPolygonCount = buffer.getInt(countsStart + 20);

	    // Final offset after all fields
	    int jtEndIndex = countsStart + 24;

	    return new PartitionNodeElementRecord(
	        baseNodeDataRecord,
	        groupNodeDataRecord,
	        partitionFlags,
	        fileName,
	        untransformedBBox,
	        area,
	        minVertexCount,
	        maxVertexCount,
	        minNodeCount,
	        maxNodeCount,
	        minPolygonCount,
	        maxPolygonCount,
	        jtEndIndex
	    );
	}
	
	public static BaseShapeDataRecord readBaseShapeDataFromOlderVersionsOrShittyWriters(int startIndex, BitByteBuffer buffer) {
		BaseNodeDataRecord baseNodeDataRecord = readBaseNodeData(startIndex, buffer);
//		int versionNumber = buffer.getShort(baseNodeDataRecord.jtEndIndex());
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer,  baseNodeDataRecord.jtEndIndex());
		BBoxF32 reservedField = ReadFromBufferUtils.readBBoxF32(buffer, baseNodeDataRecord.jtEndIndex() + 2);
		BBoxF32 bboxF32 = ReadFromBufferUtils.readBBoxF32(buffer, baseNodeDataRecord.jtEndIndex() + 2);
		float area = buffer.getFloat(baseNodeDataRecord.jtEndIndex() + 1 + 6 * 8);
		VertexCountRangeRecord vertexCountRangeRecord = VertexCountRangeRecord.fromByteBuffer(buffer, baseNodeDataRecord.jtEndIndex() + 1 + 6 * 8 + 4);
		NodeCountRangeRecord nodeCountRangeRecord = NodeCountRangeRecord.fromByteBuffer(buffer, vertexCountRangeRecord.jtEndIndex());
		PolygonCountRangeRecord polygonCountRangeRecord = PolygonCountRangeRecord.fromByteBuffer(buffer, nodeCountRangeRecord.jtEndIndex());
		long size = buffer.getInt(polygonCountRangeRecord.jtEndIndex());
		float compressionLevel = buffer.getFloat(polygonCountRangeRecord.jtEndIndex() + 4);
		return new BaseShapeDataRecord(baseNodeDataRecord, versionNumber, bboxF32, area, vertexCountRangeRecord, nodeCountRangeRecord, polygonCountRangeRecord, size, compressionLevel, polygonCountRangeRecord.jtEndIndex() + 8);
	}
	
	public static BaseShapeDataRecord readBaseShapeData(int startIndex, BitByteBuffer buffer) {
		BaseNodeDataRecord baseNodeDataRecord = readBaseNodeData(startIndex , buffer);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer,  baseNodeDataRecord.jtEndIndex());
		BBoxF32 bboxF32 = ReadFromBufferUtils.readBBoxF32(buffer, baseNodeDataRecord.jtEndIndex() + 1);
		float area = buffer.getFloat(baseNodeDataRecord.jtEndIndex() + 1 + 6 * 4);
		VertexCountRangeRecord vertexCountRangeRecord = VertexCountRangeRecord.fromByteBuffer(buffer, baseNodeDataRecord.jtEndIndex() + 1 + 6 * 4 + 4);
		NodeCountRangeRecord nodeCountRangeRecord = NodeCountRangeRecord.fromByteBuffer(buffer, vertexCountRangeRecord.jtEndIndex());
		PolygonCountRangeRecord polygonCountRangeRecord = PolygonCountRangeRecord.fromByteBuffer(buffer, nodeCountRangeRecord.jtEndIndex());
		long size = ReadFromBufferUtils.readUnsignedInt(buffer, polygonCountRangeRecord.jtEndIndex());
		float compressionLevel = buffer.getFloat(polygonCountRangeRecord.jtEndIndex() + 4);
		return new BaseShapeDataRecord(baseNodeDataRecord, versionNumber, bboxF32, area, vertexCountRangeRecord, nodeCountRangeRecord, polygonCountRangeRecord, size, compressionLevel, polygonCountRangeRecord.jtEndIndex() + 8);
	}
	
	public static BaseNodeDataRecord readBaseNodeData(int startIndex, BitByteBuffer buffer) {
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		long nodeFlags = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1);
		int attributeAmount = buffer.getInt(startIndex + 1 + 4);
		Set<Integer> attributeIDSet = new HashSet<>();
		for (int i = 0; i < attributeAmount; i++) {
			attributeIDSet.add(buffer.getInt(startIndex + 1 + 8 + i * 4));
		}
		return new BaseNodeDataRecord(versionNumber, nodeFlags, attributeAmount, attributeIDSet, startIndex, startIndex + 1 + 4 + 4 + attributeAmount * 4);
	}
	
	public static BasePropertyAtomDataRecord readBasePropertyAtomData(int startIndex, BitByteBuffer buffer) {
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		long stateFlags = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1);
		return new BasePropertyAtomDataRecord(versionNumber, stateFlags, startIndex + 5);
	}
	
	public static GroupNodeDataRecord readGroupNodeData(int startIndex, BitByteBuffer buffer, BaseNodeDataRecord baseNodeData) {
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		int childCount = buffer.getInt(startIndex + 1);
		Set<Integer> childIDSet = new HashSet<>();
		for (int i = 0; i < childCount; i++) {
			childIDSet.add(buffer.getInt(startIndex + 1 + 4 + i * 4));
		}
		if (childCount != childIDSet.size()) {
			System.out.println("ALARRM...!");
		}
		GroupNodeDataRecord groupNodeDataRecord = new GroupNodeDataRecord(baseNodeData, versionNumber, childCount, childIDSet, startIndex, (startIndex + 1 + 4 + childCount * 4));
		return groupNodeDataRecord;
	}
	
	public static LogicalElementHeaderRecord readLogicalElementHeader(BitByteBuffer buffer, int startIndex) {
	    int elementLength = buffer.getInt(startIndex);
	    String objectTypeID = ReadFromBufferUtils.getGUID(buffer, startIndex + 4);

	    byte objectBaseTypeByte = buffer.get(startIndex + 16 + 4);
	    int objectBaseType = Byte.toUnsignedInt(objectBaseTypeByte);

	    int objectID = buffer.getInt(startIndex + 16 + 4 + 1);

	    return new LogicalElementHeaderRecord(elementLength, objectTypeID, objectBaseType, objectID, startIndex + 16 + 4 + 1 + 4);
	}
	
	public static LogicalElementHeaderRecord readLogicalElementHeader(ByteBuffer buffer, int startIndex) {
	    int elementLength = buffer.getInt(startIndex);
	    String objectTypeID = ReadFromBufferUtils.getGUID(buffer, startIndex + 4);

	    byte objectBaseTypeByte = buffer.get(startIndex + 16 + 4);
	    int objectBaseType = Byte.toUnsignedInt(objectBaseTypeByte);

	    int objectID = buffer.getInt(startIndex + 16 + 4 + 1);

	    return new LogicalElementHeaderRecord(elementLength, objectTypeID, objectBaseType, objectID, startIndex + 16 + 4 + 1 + 4);
	}

}
