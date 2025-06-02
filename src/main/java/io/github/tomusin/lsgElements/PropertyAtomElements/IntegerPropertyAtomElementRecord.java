package io.github.tomusin.lsgElements.PropertyAtomElements;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record IntegerPropertyAtomElementRecord(
		BasePropertyAtomDataRecord basePropertyAtomData,
		int versionNumber,
		int value
		) implements BufferDeserializable {

	public static IntegerPropertyAtomElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		BasePropertyAtomDataRecord basePropertyAtomData = BasePropertyAtomDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, basePropertyAtomData.jtEndIndex());
		int value = buffer.getInt(basePropertyAtomData.jtEndIndex() + 1);
		return new IntegerPropertyAtomElementRecord(basePropertyAtomData, versionNumber, value);
	}
	@Override
	public int jtEndIndex() {
		return basePropertyAtomData.jtEndIndex() + 5;
	}
}
