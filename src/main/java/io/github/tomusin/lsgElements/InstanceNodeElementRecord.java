package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record InstanceNodeElementRecord(
		BaseNodeDataRecord baseNodeDataRecord, 
		int versionNumber,
		int childNodeObjectID) implements BufferDeserializable {
	 
	public static InstanceNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, baseNodeDataRecord.jtEndIndex());
		int childNodeObjectID = buffer.getInt(baseNodeDataRecord.jtEndIndex() + 1);
		return new InstanceNodeElementRecord(baseNodeDataRecord, versionNumber, childNodeObjectID);
	 }

	@Override
	public int jtEndIndex() {
		return baseNodeDataRecord.jtEndIndex() + 1 + 4;
	}
}
