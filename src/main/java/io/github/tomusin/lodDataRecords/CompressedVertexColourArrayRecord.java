package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

/**
 * Compressed vertex colour array. Structure mirrors CompressedVertexCoordinateArray
 * but uses ColourQuantizerData instead of PointQuantizerData.
 */
public record CompressedVertexColourArrayRecord(
		int colourCount,
		int numberComponents,
		int quantizationBits,
		VecI32[] binaryVertexColours,
		ColourQuantizerDataRecord colourQuantizerData,
		VecI32[] colourCodes,
		int vertexColourHash,
		int jtEndIndex
		) implements BufferDeserializable {

	public static CompressedVertexColourArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int colourCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		int quantizationBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5);

		Logger.debug("CompressedVertexColourArray: colourCount={}, numberComponents={}, quantizationBits={}",
				colourCount, numberComponents, quantizationBits);

		if (colourCount < 0 || colourCount > 1_000_000 || numberComponents < 0 || numberComponents > 4) {
			Logger.error("CompressedVertexColourArray: invalid colourCount={} or numberComponents={} at offset {}",
					colourCount, numberComponents, startIndex);
			return new CompressedVertexColourArrayRecord(0, 0, 0, null, null, null, 0, startIndex + 6);
		}

		VecI32[] binaryVertexColours = null;
		ColourQuantizerDataRecord colourQuantizerData = null;
		VecI32[] colourCodes = null;
		int offset = startIndex + 6;

		if (quantizationBits == 0) {
			// Binary colour storage
			binaryVertexColours = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexColours[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
				offset = binaryVertexColours[i].jtEndIndex();
			}
		} else if (quantizationBits > 0) {
			// Quantized colour storage
			colourQuantizerData = ColourQuantizerDataRecord.fromByteBuffer(buffer, offset);
			offset = colourQuantizerData.jtEndIndex();
			colourCodes = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				colourCodes[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
				offset = colourCodes[i].jtEndIndex();
			}
		} else {
			Logger.error("CompressedVertexColourArray: invalid quantizationBits={}", quantizationBits);
		}

		int vertexColourHash = buffer.getInt(offset);
		return new CompressedVertexColourArrayRecord(colourCount, numberComponents, quantizationBits,
				binaryVertexColours, colourQuantizerData, colourCodes, vertexColourHash, offset + 4);
	}
}
