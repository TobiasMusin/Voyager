package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record BaseAttributeDataFieldsV1Record(int stateFlags, long fieldInhibitFlags, long fieldFinalFlags, int jtEndIndex) {
	public static BaseAttributeDataFieldsV1Record fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int stateFlags = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		long fieldInhibitFlags = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1);
		long fieldFinalFlags = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 5);
		return new BaseAttributeDataFieldsV1Record(stateFlags, fieldInhibitFlags, fieldFinalFlags, startIndex + 9);
	}
}
