package io.github.tomusin.lsgDataRecords;


import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record LODNodeDataRecord(
		GroupNodeDataRecord groupNodeDataRecord,
		int versionNumber,
		int jtEndIndex) {
	
	public static LODNodeDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
		GroupNodeDataRecord groupNodeDataRecord = ReadNodesFromBufferUtils.readGroupNodeData(baseNodeDataRecord.jtEndIndex(), buffer, baseNodeDataRecord);
		return new LODNodeDataRecord(groupNodeDataRecord, ReadFromBufferUtils.readUnsignedByte(buffer, groupNodeDataRecord.jtEndIndex()), groupNodeDataRecord.jtEndIndex() + 1);
	}
}
