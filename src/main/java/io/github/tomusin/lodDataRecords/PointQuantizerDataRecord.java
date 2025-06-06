package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;

public record PointQuantizerDataRecord(
		UniformQuantizerDataRecord xUniformQuantizerData,
		UniformQuantizerDataRecord yUniformQuantizerData,
		UniformQuantizerDataRecord zUniformQuantizerData
		) implements BufferDeserializable {

	public static PointQuantizerDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		UniformQuantizerDataRecord xUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, startIndex, 'x');
		UniformQuantizerDataRecord yUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, startIndex, 'y');
		UniformQuantizerDataRecord zUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, startIndex, 'z');
		return new PointQuantizerDataRecord(xUniformQuantizerData, yUniformQuantizerData, zUniformQuantizerData);
	}
	@Override
	public int jtEndIndex() {
		return zUniformQuantizerData.jtEndIndex();
	}

}
