package io.github.tomusin.lodDataRecords;


import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

public record PointQuantizerDataRecord(
		UniformQuantizerDataRecord xUniformQuantizerData,
		UniformQuantizerDataRecord yUniformQuantizerData,
		UniformQuantizerDataRecord zUniformQuantizerData
		) implements BufferDeserializable {

	public static PointQuantizerDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		UniformQuantizerDataRecord xUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, startIndex, 'x');
		UniformQuantizerDataRecord yUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, xUniformQuantizerData.jtEndIndex(), 'y');
		UniformQuantizerDataRecord zUniformQuantizerData = UniformQuantizerDataRecord.fromByteBuffer(buffer, yUniformQuantizerData.jtEndIndex(), 'z');
		return new PointQuantizerDataRecord(xUniformQuantizerData, yUniformQuantizerData, zUniformQuantizerData);
	}
	@Override
	public int jtEndIndex() {
		return zUniformQuantizerData.jtEndIndex();
	}

}
