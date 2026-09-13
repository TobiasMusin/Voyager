package io.github.tomusin.lsgElements.PropertyAtomElements;


import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record LateLoadedPropertyAtomElementRecord(
		BasePropertyAtomDataRecord basePropertyAtomData,
		int versionNumber,
		String GUID,
		int segmentType,
		int payloadObjectID,
		int reserved
		) implements BufferDeserializable {

	public static LateLoadedPropertyAtomElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		BasePropertyAtomDataRecord basePropertyAtomData = BasePropertyAtomDataRecord.fromByteBuffer(buffer, startIndex);
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, basePropertyAtomData.jtEndIndex());
		String GUID = ReadFromBufferUtils.getGUID(buffer, basePropertyAtomData.jtEndIndex() + 1);
		int segmentType = buffer.getInt(basePropertyAtomData.jtEndIndex() + 17);
		int payloadObjectID = buffer.getInt(basePropertyAtomData.jtEndIndex() + 21);
		int reserved = buffer.getInt(basePropertyAtomData.jtEndIndex() + 25);
		return new LateLoadedPropertyAtomElementRecord(basePropertyAtomData, versionNumber, GUID, segmentType, payloadObjectID, reserved);
	}
	@Override
	public int jtEndIndex() {
		return basePropertyAtomData.jtEndIndex() + 29;
	}

}
