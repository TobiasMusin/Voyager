package io.github.tomusin.lodDataRecords;

import io.github.tomusin.voyager.utils.BitByteBuffer;

// Page 92, Figure 86
public record BaseShapeLODDataRecord(byte versionNumber, int jtEndIndex) {
	public static BaseShapeLODDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		byte version = buffer.get(startIndex);
		return new BaseShapeLODDataRecord(version, startIndex + 1);
	}
}