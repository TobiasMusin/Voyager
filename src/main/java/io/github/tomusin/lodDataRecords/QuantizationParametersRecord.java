package io.github.tomusin.lodDataRecords;


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
		int bitsPerVertex = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		int normalBitsFactor = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 1);
		int bitsPerTextureCoord = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 2);
		int bitsPerColor = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 3);
		int jtEndIndex = startIndex + 4;
		return new QuantizationParametersRecord(bitsPerVertex, normalBitsFactor, bitsPerTextureCoord, bitsPerColor, jtEndIndex);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}

}
