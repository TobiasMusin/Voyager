package io.github.tomusin.lsgElements;

import io.github.tomusin.lsgDataRecords.BaseAttributeDataFieldsV2Record;
import io.github.tomusin.lsgDataRecords.BaseAttributeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

/** JT 10.6 section 5.4.5 Line Style Attribute Element. */
public record LineStyleAttributeElementRecord(
        BaseAttributeDataRecord baseAttributeDataRecord,
        byte versionNumber,
        int dataFlags,
        float lineWidth,
        BaseAttributeDataFieldsV2Record baseAttributeDataFieldsV2Record,
        int jtEndIndex) implements BufferDeserializable {

    public static LineStyleAttributeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
        BaseAttributeDataRecord baseAttribute = BaseAttributeDataRecord.fromByteBuffer(buffer, startIndex);
        int dataStart = baseAttribute.jtEndIndex();
        byte version = buffer.get(dataStart);
        int flags = buffer.getUnsignedByte(dataStart + 1);
        float width = buffer.getFloat(dataStart + 2);
        int endIndex = dataStart + 6;
        BaseAttributeDataFieldsV2Record v2Fields = null;
        if (baseAttribute.versionNumber() >= 2) {
            v2Fields = BaseAttributeDataFieldsV2Record.fromByteBuffer(buffer, endIndex);
            endIndex = v2Fields.jtEndIndex();
        }
        return new LineStyleAttributeElementRecord(baseAttribute, version, flags, width, v2Fields, endIndex);
    }
}