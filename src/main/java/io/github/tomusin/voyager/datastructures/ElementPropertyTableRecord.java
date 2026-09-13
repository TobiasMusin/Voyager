package io.github.tomusin.voyager.datastructures;

import java.util.HashMap;
import java.util.Map;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record ElementPropertyTableRecord(Map<Integer, Integer> elementPropertiesMap, int jtEndIndex) {
	public static ElementPropertyTableRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		Map<Integer, Integer> elementPropertiesMap = new HashMap<>();
		int currentIndex = startIndex;
		int keyPropertyAtomObjectID = buffer.getInt(currentIndex);
		while (keyPropertyAtomObjectID != 0) {
			int valuePropertyAtomObjectID = buffer.getInt(currentIndex + 4);
			elementPropertiesMap.put(keyPropertyAtomObjectID, valuePropertyAtomObjectID);
			currentIndex += 8;
			keyPropertyAtomObjectID = buffer.getInt(currentIndex);
		}
		return new ElementPropertyTableRecord(elementPropertiesMap, currentIndex + 4);
	}
}
