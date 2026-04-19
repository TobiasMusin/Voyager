package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

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
		Logger.info("CompressedVertexCoordinateArray: uniqueVertexCount={}, numberComponents={}, startIndex={}", uniqueVertexCount, numberComponents, startIndex);
		PointQuantizerDataRecord pointQuantizerData = PointQuantizerDataRecord.fromByteBuffer(buffer, startIndex + 5);
		Logger.info("  PointQuantizer X: min={}, max={}, bits={}", pointQuantizerData.xUniformQuantizerData().min(), pointQuantizerData.xUniformQuantizerData().max(), pointQuantizerData.xUniformQuantizerData().numberOfBits());
		Logger.info("  PointQuantizer Y: min={}, max={}, bits={}", pointQuantizerData.yUniformQuantizerData().min(), pointQuantizerData.yUniformQuantizerData().max(), pointQuantizerData.yUniformQuantizerData().numberOfBits());
		Logger.info("  PointQuantizer Z: min={}, max={}, bits={}", pointQuantizerData.zUniformQuantizerData().min(), pointQuantizerData.zUniformQuantizerData().max(), pointQuantizerData.zUniformQuantizerData().numberOfBits());
		Logger.info("  PointQuantizer ends at byte offset {}", pointQuantizerData.jtEndIndex());
		
		int quantBits = pointQuantizerData.xUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.yUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.zUniformQuantizerData().numberOfBits();
		
		Logger.info("  quantBits X: {}, Y: {}, Z: {}, total: {}", pointQuantizerData.xUniformQuantizerData().numberOfBits(), pointQuantizerData.yUniformQuantizerData().numberOfBits(), pointQuantizerData.zUniformQuantizerData().numberOfBits(), quantBits);
		
		VecI32[] vertexCoordCords = null;
		VecI32[] binaryVertexCoords = null;
		
		int nextOffset = pointQuantizerData.zUniformQuantizerData().jtEndIndex();
		Logger.info("  quantBits={}, nextOffset(bytes)={}", quantBits, nextOffset);
		if (quantBits > 0) {
			vertexCoordCords = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				vertexCoordCords[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextOffset);
				nextOffset = vertexCoordCords[i].jtEndIndex();
				Logger.info("  vertexCoordCords[{}]: count={}, nextOffset={}", i, vertexCoordCords[i].count(), nextOffset);
			}
		} else if (quantBits == 0) {
			binaryVertexCoords = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexCoords[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextOffset);
				nextOffset = binaryVertexCoords[i].jtEndIndex();
				Logger.info("  binaryVertexCoords[{}]: count={}, nextOffset={}", i, binaryVertexCoords[i].count(), nextOffset);
			}
		} else {
			Logger.error("Something went wrong when checking the number of QuantBits.");
		}
		int vertexCoordinateHash = buffer.getInt(nextOffset);
		Logger.info("  vertexCoordinateHash=0x{}, endOffset={}", Integer.toHexString(vertexCoordinateHash), nextOffset + 4);
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
		// Diagnostic: find first invalid Z value
		if (result.length >= 3 && result[2] != null) {
			float zMin = pointQuantizerData.zUniformQuantizerData().min();
			float zMax = pointQuantizerData.zUniformQuantizerData().max();
			float margin = 0.01f; // Tight margin
			// Log last 5 Z values
			int zLen = result[2].length;
			for (int j = Math.max(0, zLen - 5); j < zLen; j++) {
				int raw = binaryVertexCoords != null ? binaryVertexCoords[2].valueArray()[j] : 0;
				Logger.info("  Z_LAST[{}]: float={}, raw=0x{} ({})", j, result[2][j], Integer.toHexString(raw), raw);
			}
			for (int i = 0; i < result[2].length; i++) {
				float z = result[2][i];
				if (Float.isNaN(z) || Float.isInfinite(z) || z < zMin - margin || z > zMax + margin) {
					Logger.info("FIRST_BAD_Z at index {}: z={}, raw_int=0x{}", i, z,
						binaryVertexCoords != null ? Integer.toHexString(binaryVertexCoords[2].valueArray()[i]) : "quantized");
					// Log surrounding values
					for (int j = Math.max(0, i - 5); j <= Math.min(result[2].length - 1, i + 5); j++) {
						int raw = binaryVertexCoords != null ? binaryVertexCoords[2].valueArray()[j] : 0;
						Logger.info("  Z[{}]: float={}, raw=0x{} ({})", j, result[2][j], Integer.toHexString(raw), raw);
					}
					break;
				}
			}
		}
		return result;
	}
}