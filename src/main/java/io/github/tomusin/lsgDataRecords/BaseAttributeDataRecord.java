package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record BaseAttributeDataRecord(byte versionNumber, BaseAttributeDataFieldsV1Record baseAttributeDataFieldsV1Record, int jtEndIndex) {

	public static BaseAttributeDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		byte versionNumber = buffer.get(startIndex);
		BaseAttributeDataFieldsV1Record baseAttributeDataFieldsV1Record = BaseAttributeDataFieldsV1Record.fromByteBuffer(buffer, startIndex + 1);
		return new BaseAttributeDataRecord(versionNumber, baseAttributeDataFieldsV1Record, baseAttributeDataFieldsV1Record.jtEndIndex());
	}
}
