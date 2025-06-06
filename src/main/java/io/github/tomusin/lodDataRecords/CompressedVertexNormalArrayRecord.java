package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record CompressedVertexNormalArrayRecord(
		int normalCount,
		int numberComponents,
		int quantizationBits,
		VecU32[] binaryVertexNormals,
		VecU32[] deeringNormalCodes,
		long vertexNormalHash,
		int jtEndIndex
		) implements BufferDeserializable {

	public static CompressedVertexNormalArrayRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		int normalCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		int quantizationBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5);
		VecU32[] binaryVertexNormals = null;
		VecU32[] deeringNormalCodes = null;
		int nextVectorStartIndex = startIndex + 6;
		if (quantizationBits == 0) {
			binaryVertexNormals = new VecU32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexNormals[i] = VecU32.fromByteBuffer(buffer, nextVectorStartIndex);
				nextVectorStartIndex = binaryVertexNormals[i].jtEndIndex();
			}
		} else if (quantizationBits > 0) {
			deeringNormalCodes = new VecU32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				deeringNormalCodes[i] = VecU32.fromByteBuffer(buffer, nextVectorStartIndex);
				nextVectorStartIndex = deeringNormalCodes[i].jtEndIndex();
			}
		} else {
			Logger.error("Something went wrong when checking the number of QuantBits. Maybe you are at the wrong buffer index.");
		}
		long vertexNormalHash = ReadFromBufferUtils.readUnsignedInt(buffer, nextVectorStartIndex);
		return new CompressedVertexNormalArrayRecord(normalCount, numberComponents, quantizationBits, binaryVertexNormals, deeringNormalCodes, vertexNormalHash, nextVectorStartIndex + 4);
	}
}
