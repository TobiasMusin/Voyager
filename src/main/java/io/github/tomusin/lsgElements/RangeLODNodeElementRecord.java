package io.github.tomusin.lsgElements;


import io.github.tomusin.lsgDataRecords.LODNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecF32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record RangeLODNodeElementRecord(
		LODNodeDataRecord lodNodeDataRecord,
		int versionNumber,
		VecF32 rangeLimits) implements BufferDeserializable {

	public static RangeLODNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		LODNodeDataRecord lodNodeDataRecord = LODNodeDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, lodNodeDataRecord.jtEndIndex());
		int count = buffer.getInt(lodNodeDataRecord.jtEndIndex() + 1);
		float[] valueArray = new float[count]; 
		for (int i = 0; i < count; i++) {
			valueArray[i] = buffer.getFloat(lodNodeDataRecord.jtEndIndex() + 5 + i * 4);
		}
		return new RangeLODNodeElementRecord(lodNodeDataRecord, versionNumber, new VecF32(count, valueArray));
	}
	@Override
	public int jtEndIndex() {
		return lodNodeDataRecord.jtEndIndex() + 1 + rangeLimits.count() * 4;
	}
}
