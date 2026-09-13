package io.github.tomusin.voyager.datastructures;


import io.github.tomusin.voyager.utils.BitByteBuffer;

public record RGBARecord(float r, float g, float b, float a, int jtEndIndex) {
	public static RGBARecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		float r = buffer.getFloat(startIndex);
		float g = buffer.getFloat(startIndex + 4);
		float b = buffer.getFloat(startIndex + 8);
		float a = buffer.getFloat(startIndex + 12);
		int jtEndIndex = startIndex + 16;
		return new RGBARecord(r, g, b, a, jtEndIndex);
	}
}
