package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.lsgDataRecords.GroupNodeDataRecord;
import io.github.tomusin.lsgDataRecords.MetaDataNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record MetaDataNodeElementRecord(
		MetaDataNodeDataRecord metaDataNodeDataRecord,
		int jtEndIndex
		) implements BufferDeserializable {

	 public static MetaDataNodeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		 BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
		 GroupNodeDataRecord groupNodeDataRecord = ReadNodesFromBufferUtils.readGroupNodeData(startIndex, buffer, baseNodeDataRecord);
		 int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, groupNodeDataRecord.jtEndIndex());
		 return new MetaDataNodeElementRecord(new MetaDataNodeDataRecord(groupNodeDataRecord, versionNumber), groupNodeDataRecord.jtEndIndex() + 1);
	 }
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}

}
