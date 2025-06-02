package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;

public record BaseAttributeDataRecord(byte versionNumber, BaseAttributeDataFieldsV1Record baseAttributeDataFieldsV1Record, int jtEndIndex) {

	public static BaseAttributeDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		byte versionNumber = buffer.get(startIndex);
		BaseAttributeDataFieldsV1Record baseAttributeDataFieldsV1Record = BaseAttributeDataFieldsV1Record.fromByteBuffer(buffer, startIndex + 1);
		return new BaseAttributeDataRecord(versionNumber, baseAttributeDataFieldsV1Record, baseAttributeDataFieldsV1Record.jtEndIndex());
	}
}
