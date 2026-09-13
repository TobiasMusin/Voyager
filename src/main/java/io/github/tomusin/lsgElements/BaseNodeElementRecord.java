package io.github.tomusin.lsgElements;


import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

public record BaseNodeElementRecord(BaseNodeDataRecord baseNodeDataRecord) implements BufferDeserializable {

	public static BaseNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return new BaseNodeElementRecord(BaseNodeDataRecord.fromByteBuffer(buffer, startIndex));
	}
	@Override
	public int jtEndIndex() {
		return baseNodeDataRecord.jtEndIndex();
	}

}
