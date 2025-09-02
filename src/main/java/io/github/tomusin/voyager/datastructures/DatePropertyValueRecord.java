package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;

public record DatePropertyValueRecord(
		short year,
		short month,
		short day,
		short hour,
		short minute,
		short second,
		int jtEndIndex
		) {
	public static DatePropertyValueRecord fromBuffer(BitByteBuffer buffer, int startIndex) {
		short year = buffer.getShort(startIndex);
		short month = buffer.getShort(startIndex + 2);
		short day = buffer.getShort(startIndex + 4);
		short hour = buffer.getShort(startIndex + 6);
		short minute = buffer.getShort(startIndex + 8);
		short second = buffer.getShort(startIndex + 10);
		int jtEndIndex = startIndex + 12;
		return new DatePropertyValueRecord(year, month, day, hour, minute, second, jtEndIndex);
	}
}
