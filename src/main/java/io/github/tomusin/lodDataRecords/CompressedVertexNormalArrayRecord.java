package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

/**
 * Compressed vertex normal array.
 * Uses byte-aligned Int32CDP (not VecU32) to maintain consistent byte offsets with the rest of the pipeline.
 */
public record CompressedVertexNormalArrayRecord(
		int normalCount,
		int numberComponents,
		int quantizationBits,
		VecI32[] binaryVertexNormals,
		VecI32[] deeringNormalCodes,
		long vertexNormalHash,
		int jtEndIndex
		) implements BufferDeserializable {

	public static CompressedVertexNormalArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int normalCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		int quantizationBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5);
		
		Logger.debug("CompressedVertexNormalArray: normalCount={}, numberComponents={}, quantizationBits={}", 
				normalCount, numberComponents, quantizationBits);
		
		// Sanity check to prevent OOM from wrong offsets
		if (normalCount < 0 || normalCount > 1_000_000 || numberComponents < 0 || numberComponents > 4) {
			Logger.error("CompressedVertexNormalArray: invalid normalCount={} or numberComponents={} at offset {}", 
					normalCount, numberComponents, startIndex);
			return new CompressedVertexNormalArrayRecord(0, 0, 0, null, null, 0, startIndex + 6);
		}
		VecI32[] binaryVertexNormals = null;
		VecI32[] deeringNormalCodes = null;
		int nextVectorStartIndex = startIndex + 6;
		if (quantizationBits == 0) {
			binaryVertexNormals = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexNormals[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextVectorStartIndex);
				nextVectorStartIndex = binaryVertexNormals[i].jtEndIndex();
			}
		} else if (quantizationBits > 0) {
			deeringNormalCodes = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				deeringNormalCodes[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, nextVectorStartIndex);
				nextVectorStartIndex = deeringNormalCodes[i].jtEndIndex();
			}
		} else {
			Logger.error("Something went wrong when checking the number of QuantBits. Maybe you are at the wrong buffer index.");
		}
		long vertexNormalHash = ReadFromBufferUtils.readUnsignedInt(buffer, nextVectorStartIndex);
		return new CompressedVertexNormalArrayRecord(normalCount, numberComponents, quantizationBits, binaryVertexNormals, deeringNormalCodes, vertexNormalHash, nextVectorStartIndex + 4);
	}
}
