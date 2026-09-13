package io.github.tomusin.lsgElements.PropertyAtomElements;


import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.DatePropertyValueRecord;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record DatePropertyAtomElementRecord(
		BasePropertyAtomDataRecord basePropertyAtomData,
		int versionNumber,
		DatePropertyValueRecord datePropertyValueRecord
		) implements BufferDeserializable {

	public static DatePropertyAtomElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BasePropertyAtomDataRecord basePropertyAtomData = BasePropertyAtomDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, basePropertyAtomData.jtEndIndex());
		DatePropertyValueRecord datePropertyValueRecord = DatePropertyValueRecord.fromBuffer(buffer, basePropertyAtomData.jtEndIndex() + 1);
		return new DatePropertyAtomElementRecord(basePropertyAtomData, versionNumber, datePropertyValueRecord);
	}
	@Override
	public int jtEndIndex() {
		return datePropertyValueRecord.jtEndIndex();
	}

}
