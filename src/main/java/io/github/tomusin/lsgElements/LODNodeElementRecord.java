package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.LODNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;

public record LODNodeElementRecord(LODNodeDataRecord lodNodeDataRecord) implements BufferDeserializable {

	public static LODNodeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		return new LODNodeElementRecord(LODNodeDataRecord.fromByteBuffer(buffer, startIndex));
	}
	
	@Override
	public int jtEndIndex() {
		return lodNodeDataRecord.jtEndIndex();
	}

}
