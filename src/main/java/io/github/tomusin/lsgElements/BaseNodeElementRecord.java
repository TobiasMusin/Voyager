package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;

public record BaseNodeElementRecord(BaseNodeDataRecord baseNodeDataRecord) implements BufferDeserializable {

	public static BaseNodeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		return new BaseNodeElementRecord(BaseNodeDataRecord.fromByteBuffer(buffer, startIndex));
	}
	@Override
	public int jtEndIndex() {
		return baseNodeDataRecord.jtEndIndex();
	}

}
