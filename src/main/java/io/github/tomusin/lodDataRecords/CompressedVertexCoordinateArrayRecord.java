package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record CompressedVertexCoordinateArrayRecord(
		int uniqueVertexCount,
		int numberComponents,
		PointQuantizerDataRecord pointQuantizerData,
		VecI32[] binaryVertexCoords,
		VecI32[] vertexCoordCords,
		int vertexCoordinateHash,
		int jtEndIndex
		) implements BufferDeserializable {
	
	public static CompressedVertexCoordinateArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int uniqueVertexCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		Logger.debug("CompressedVertexCoordinateArray: uniqueVertexCount={}, numberComponents={}, startIndex={}", uniqueVertexCount, numberComponents, startIndex);
		PointQuantizerDataRecord pointQuantizerData = PointQuantizerDataRecord.fromByteBuffer(buffer, startIndex + 5);
		
		int quantBits = pointQuantizerData.xUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.yUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.zUniformQuantizerData().numberOfBits();
		
		VecI32[] vertexCoordCords = null;
		VecI32[] binaryVertexCoords = null;
		
		int nextOffset = pointQuantizerData.zUniformQuantizerData().jtEndIndex();
		if (quantBits > 0) {
			vertexCoordCords = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				vertexCoordCords[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextOffset);
				nextOffset = vertexCoordCords[i].jtEndIndex();
			}
		} else if (quantBits == 0) {
			binaryVertexCoords = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexCoords[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextOffset);
				nextOffset = binaryVertexCoords[i].jtEndIndex();
			}
		} else {
			Logger.error("Invalid quantBits value: {}", quantBits);
		}
		int vertexCoordinateHash = buffer.getInt(nextOffset);
		return new CompressedVertexCoordinateArrayRecord(uniqueVertexCount, numberComponents, pointQuantizerData, binaryVertexCoords, vertexCoordCords, vertexCoordinateHash, nextOffset + 4);
	}
	
	/**
	 * Dequantizes the vertex coordinate codes back to float coordinates.
	 * Returns a float[numberComponents][uniqueVertexCount] array with the actual coordinate values.
	 * For quantized data: value = min + code * (max - min) / (2^bits - 1)
	 * For binary data: reinterprets the stored I32 values as IEEE 754 floats.
	 */
	public float[][] dequantize() {
		float[][] result = new float[numberComponents][];
		
		UniformQuantizerDataRecord[] quantizers = {
			pointQuantizerData.xUniformQuantizerData(),
			pointQuantizerData.yUniformQuantizerData(),
			pointQuantizerData.zUniformQuantizerData()
		};
		
		for (int c = 0; c < numberComponents; c++) {
			UniformQuantizerDataRecord q = quantizers[c];
			
			if (vertexCoordCords != null) {
				// Quantized path
				int[] codes = vertexCoordCords[c].valueArray();
				result[c] = new float[codes.length];
				float min = q.min();
				float max = q.max();
				int bits = q.numberOfBits();
				double maxCode = (1L << bits) - 1;
				for (int i = 0; i < codes.length; i++) {
					result[c][i] = (float) (min + (codes[i] & 0xFFFFFFFFL) * (max - min) / maxCode);
				}
			} else if (binaryVertexCoords != null) {
				// Binary (unquantized) path - codes are IEEE 754 float bits
				int[] codes = binaryVertexCoords[c].valueArray();
				result[c] = new float[codes.length];
				for (int i = 0; i < codes.length; i++) {
					result[c][i] = Float.intBitsToFloat(codes[i]);
				}
			}
		}
		return result;
	}
}