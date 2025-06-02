package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseAttributeDataFieldsV2Record;
import io.github.tomusin.lsgDataRecords.BaseAttributeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.RGBARecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record MaterialAttributeElementRecord(
		BaseAttributeDataRecord baseAttributeDataRecord, 
		byte versionNumber,
		int dataFlags,
		RGBARecord ambientColor,
		RGBARecord diffuseColorAndAlpha,
		RGBARecord specularColor,
		RGBARecord emissionColor,
		float shininess,
		float reflectivity,
		float bumpiness,
		BaseAttributeDataFieldsV2Record baseAttributeDataFieldsV2Record
		) implements BufferDeserializable {
	
	public static MaterialAttributeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		BaseAttributeDataRecord baseAttributeDataRecord =  BaseAttributeDataRecord.fromByteBuffer(buffer, startIndex);
		byte versionNumber = buffer.get(baseAttributeDataRecord.jtEndIndex());
		int dataFlags = ReadFromBufferUtils.readUnsignedShort(buffer, baseAttributeDataRecord.jtEndIndex() + 1);
		RGBARecord ambientColor = RGBARecord.fromByteBuffer(buffer, baseAttributeDataRecord.jtEndIndex() + 3);
		RGBARecord diffuseColorAndAlpha = RGBARecord.fromByteBuffer(buffer, ambientColor.jtEndIndex());
		RGBARecord specularColor = RGBARecord.fromByteBuffer(buffer, diffuseColorAndAlpha.jtEndIndex());
		RGBARecord emissionColor = RGBARecord.fromByteBuffer(buffer, specularColor.jtEndIndex());
		float shininess = buffer.getFloat(emissionColor.jtEndIndex());
		float reflectivity = buffer.getFloat(emissionColor.jtEndIndex() + 4);
		float bumpiness = buffer.getFloat(emissionColor.jtEndIndex() + 8);
		BaseAttributeDataFieldsV2Record baseAttributeDataFieldsV2Record = null;
		int jtEndIndex = emissionColor.jtEndIndex() + 12;
		if (versionNumber >= 2) {
			baseAttributeDataFieldsV2Record = 	BaseAttributeDataFieldsV2Record.fromByteBuffer(buffer, jtEndIndex);	
			jtEndIndex = baseAttributeDataFieldsV2Record.jtEndIndex();
		}
		return new MaterialAttributeElementRecord(
				baseAttributeDataRecord, 
				versionNumber, 
				dataFlags, 
				ambientColor, 
				diffuseColorAndAlpha, 
				specularColor, 
				emissionColor, 
				shininess, 
				reflectivity, 
				bumpiness, 
				baseAttributeDataFieldsV2Record);
	}

	@Override
	public int jtEndIndex() {
		if (baseAttributeDataFieldsV2Record != null) {
			return baseAttributeDataFieldsV2Record.jtEndIndex();
		} else {
			return emissionColor.jtEndIndex() + 12;
		}
	}

}
