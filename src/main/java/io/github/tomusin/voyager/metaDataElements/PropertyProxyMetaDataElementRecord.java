package io.github.tomusin.voyager.metaDataElements;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.DatePropertyValueRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils.MbStringResult;

public record PropertyProxyMetaDataElementRecord(
		int versionNumber,
		String mbString,
		int propertyValueType,
		String stringPropertyValue,
		int intPropertyValue,
		float floatPropertyValue,
		DatePropertyValueRecord date,
		int jtEndIndex
		) implements BufferDeserializable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PropertyProxyMetaDataElementRecord.class);

	public static PropertyProxyMetaDataElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		int propertyValueType = 0;
		
		String stringPropertyValue = null;
		int intPropertyValue = 0;
		float floatPropertyValue = 0;
		DatePropertyValueRecord date = null;
		
		int versionNumber = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		MbStringResult mbStringResult = ReadFromBufferUtils.readMbString(buffer, startIndex + 1);
		String mbString = mbStringResult.value();
		int jtEndIndex = mbStringResult.nextIndex();
		if (!mbString.isBlank()) {
			propertyValueType = ReadFromBufferUtils.readUnsignedByte(buffer, jtEndIndex);
			switch (propertyValueType) {
			case 1:
				mbStringResult =  ReadFromBufferUtils.readMbString(buffer, jtEndIndex + 1);
				stringPropertyValue = mbStringResult.value();
				jtEndIndex = mbStringResult.nextIndex();
				break;
			case 2:
				intPropertyValue = buffer.getInt(jtEndIndex + 1);
				jtEndIndex += 5;
				break;
			case 3:
				floatPropertyValue = buffer.getFloat(jtEndIndex + 1);
				jtEndIndex += 5;
				break;
			case 4:
				date = DatePropertyValueRecord.fromBuffer(buffer, jtEndIndex + 1);
				jtEndIndex = date.jtEndIndex();
				break;
			default:
				LOGGER.error("PropertyValueType in PropertyProxyMetaDataElementRecord should be set to one of the following values: { 1, 2, 3, 4} as the mbString ({}) is not empty. BUt it has the valie: {}", mbString, propertyValueType);
			}
		}
		return new PropertyProxyMetaDataElementRecord(versionNumber, mbString, propertyValueType, stringPropertyValue, intPropertyValue, floatPropertyValue, date, jtEndIndex);
	}
}
