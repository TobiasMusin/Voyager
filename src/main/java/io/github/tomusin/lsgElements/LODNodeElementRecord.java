package io.github.tomusin.lsgElements;


import io.github.tomusin.lsgDataRecords.LODNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

public record LODNodeElementRecord(LODNodeDataRecord lodNodeDataRecord) implements BufferDeserializable {

	public static LODNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return new LODNodeElementRecord(LODNodeDataRecord.fromByteBuffer(buffer, startIndex));
	}
	
	@Override
	public int jtEndIndex() {
		return lodNodeDataRecord.jtEndIndex();
	}

}
