package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

/**
 * Compressed vertex texture coordinate array.
 * quantizationBits == 0 → binary storage, quantizationBits > 0 → quantized with TextureQuantizerData.
 */
public record CompressedVertexTextureCoordinateArrayRecord(
		int textureCoordCount,
		int numberComponents,
		int quantizationBits,
		VecI32[] binaryTextureVertexCoords,
		TextureQuantizerDataRecord textureQuantizerData,
		VecI32[] textureCoordCodes,
		int vertexTextureCoordHash,
		int jtEndIndex
		) implements BufferDeserializable {

	public static CompressedVertexTextureCoordinateArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int textureCoordCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		int quantizationBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5);

		Logger.debug("CompressedVertexTextureCoordinateArray: count={}, components={}, quantBits={}",
				textureCoordCount, numberComponents, quantizationBits);

		if (textureCoordCount < 0 || textureCoordCount > 1_000_000 || numberComponents < 0 || numberComponents > 4) {
			Logger.error("CompressedVertexTextureCoordinateArray: invalid count={} or components={} at offset {}",
					textureCoordCount, numberComponents, startIndex);
			return new CompressedVertexTextureCoordinateArrayRecord(0, 0, 0, null, null, null, 0, startIndex + 6);
		}

		VecI32[] binaryTextureVertexCoords = null;
		TextureQuantizerDataRecord textureQuantizerData = null;
		VecI32[] textureCoordCodes = null;
		int offset = startIndex + 6;

		if (quantizationBits == 0) {
			binaryTextureVertexCoords = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryTextureVertexCoords[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
				offset = binaryTextureVertexCoords[i].jtEndIndex();
			}
		} else if (quantizationBits > 0) {
			textureQuantizerData = TextureQuantizerDataRecord.fromByteBuffer(buffer, offset, numberComponents);
			offset = textureQuantizerData.jtEndIndex();
			textureCoordCodes = new VecI32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				textureCoordCodes[i] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
				offset = textureCoordCodes[i].jtEndIndex();
			}
		} else {
			Logger.error("CompressedVertexTextureCoordinateArray: invalid quantizationBits={}", quantizationBits);
		}

		int vertexTextureCoordHash = buffer.getInt(offset);
		return new CompressedVertexTextureCoordinateArrayRecord(textureCoordCount, numberComponents, quantizationBits,
				binaryTextureVertexCoords, textureQuantizerData, textureCoordCodes, vertexTextureCoordHash, offset + 4);
	}
}
