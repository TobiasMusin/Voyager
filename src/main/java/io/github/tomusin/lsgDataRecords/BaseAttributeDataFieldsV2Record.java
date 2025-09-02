package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record BaseAttributeDataFieldsV2Record(long paletteIndex, int jtEndIndex) {
	
	public static BaseAttributeDataFieldsV2Record fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		long paletteIndex = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex);
		return new BaseAttributeDataFieldsV2Record(paletteIndex, startIndex + 4);
	}
}
