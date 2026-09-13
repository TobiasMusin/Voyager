package io.github.tomusin.lsgElements.PropertyAtomElements;


import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils.MbStringResult;

public record StringPropertyAtomElementRecord(
		BasePropertyAtomDataRecord basePropertyAtomDataRecord,
		int versionNumber,
		String mbString,
		int jtEndIndex) implements BufferDeserializable {
	public static StringPropertyAtomElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BasePropertyAtomDataRecord basePropertyAtomDataRecord = ReadNodesFromBufferUtils.readBasePropertyAtomData(startIndex, buffer);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, basePropertyAtomDataRecord.jtEndIndex());
		MbStringResult mbString = ReadFromBufferUtils.readMbString(buffer, basePropertyAtomDataRecord.jtEndIndex() + 1);
		return new StringPropertyAtomElementRecord(basePropertyAtomDataRecord, versionNumber, mbString.value(), mbString.nextIndex());
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
}
