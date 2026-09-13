package io.github.tomusin.voyager.datastructures;

import java.util.HashMap;
import java.util.Map;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record PropertyTableRecord(short versionNumber, int elementPropertyTableCount, Map<Integer, ElementPropertyTableRecord> elementPropertyTableRecordMap) {

	public static PropertyTableRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		short versionNumber = buffer.getShort(startIndex);
		int elementPropertyTableCount = buffer.getInt(startIndex + 2);
		Map<Integer, ElementPropertyTableRecord> elementPropertyTableRecordMap = new HashMap<>();
		int currentIndex = startIndex + 6;
		for (int i = 0; i < elementPropertyTableCount; i++) {
			int elementObjectID = buffer.getInt(currentIndex);
			ElementPropertyTableRecord elementPropertyTableRecord = ElementPropertyTableRecord.fromByteBuffer(buffer, currentIndex + 4);
			currentIndex = elementPropertyTableRecord.jtEndIndex();
			elementPropertyTableRecordMap.put(elementObjectID, elementPropertyTableRecord);
		}

		return new PropertyTableRecord(versionNumber, elementPropertyTableCount, elementPropertyTableRecordMap);
	}
}
