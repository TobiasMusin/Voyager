package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record UniformQuantizerDataRecord(
		float min,
		float max,
		int numberOfBits,
		char type,
		int jtEndIndex
		) implements BufferDeserializable {
	
	public static UniformQuantizerDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex, char type) {
		float min = buffer.getFloat(startIndex);
		float max = buffer.getFloat(startIndex + 4);
		int numberOfBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 8);
		int jtEndIndex = startIndex + 9;
		return new UniformQuantizerDataRecord(min, max, numberOfBits, type, jtEndIndex);
	}
}
