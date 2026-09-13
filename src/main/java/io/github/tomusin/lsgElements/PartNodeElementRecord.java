package io.github.tomusin.lsgElements;


import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.lsgDataRecords.GroupNodeDataRecord;
import io.github.tomusin.lsgDataRecords.MetaDataNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record PartNodeElementRecord(
		MetaDataNodeDataRecord metaDataNodeDataRecord,
		int versionNumber,
		int emptyField,
		int jtEndIndex) implements BufferDeserializable {

	public static PartNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
		GroupNodeDataRecord groupNodeDataRecord = ReadNodesFromBufferUtils.readGroupNodeData(startIndex, buffer, baseNodeDataRecord);
		int metaDataVersionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, groupNodeDataRecord.jtEndIndex());
		MetaDataNodeElementRecord metaDataElementRecord = new MetaDataNodeElementRecord(new MetaDataNodeDataRecord(groupNodeDataRecord, metaDataVersionNumber), groupNodeDataRecord.jtEndIndex() + 1);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, metaDataElementRecord.jtEndIndex());
		int emptyField = buffer.getInt(metaDataElementRecord.jtEndIndex() + 1);
		int jtEndIndex = metaDataElementRecord.jtEndIndex() + 1 + 4;
		return new PartNodeElementRecord(metaDataElementRecord.metaDataNodeDataRecord(), versionNumber, emptyField, jtEndIndex);
	}

}
