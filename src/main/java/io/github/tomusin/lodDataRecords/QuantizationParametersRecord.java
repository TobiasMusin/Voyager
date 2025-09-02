package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record QuantizationParametersRecord(
		int bitsPerVertex,
		int normalBitsFactor,
		int bitsPerTextureCoord,
		int bitsPerColor,
		int jtEndIndex
		) implements BufferDeserializable {

	public static QuantizationParametersRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int bitsPerVertex = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 1);
		int normalBitsFactor = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 2);
		int bitsPerTextureCoord = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 3);
		int bitsPerColor = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		int jtEndIndex = startIndex + 5;
		return new QuantizationParametersRecord(bitsPerVertex, normalBitsFactor, bitsPerTextureCoord, bitsPerColor, jtEndIndex);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}

}
