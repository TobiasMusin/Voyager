package io.github.tomusin.lsgElements.PropertyAtomElements;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record FloatingPointPropertyAtomElementRecord(
		BasePropertyAtomDataRecord basePropertyAtomData,
		int versionNumber,
		float value
		) implements BufferDeserializable {

	public static FloatingPointPropertyAtomElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BasePropertyAtomDataRecord basePropertyAtomData = BasePropertyAtomDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, basePropertyAtomData.jtEndIndex());
		float value = buffer.getFloat(basePropertyAtomData.jtEndIndex() + 1);
		return new FloatingPointPropertyAtomElementRecord(basePropertyAtomData, versionNumber, value);
	}
	@Override
	public int jtEndIndex() {
		return basePropertyAtomData.jtEndIndex() + 5;
	}

}
