package io.github.tomusin.lodDataRecords;


import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopologicallyCompressedVertexRecordsRecord(
		long vertexBindings,
		QuantizationParametersRecord quantizationParameters,
		int numberOfTopologicalVertices,
		int numberOfVertexAttributes,
		CompressedVertexCoordinateArrayRecord compressedVertexCoordinateArray,
		CompressedVertexNormalArrayRecord compressedVertexNormalArray,
		CompressedVertexColourArrayRecord compressedVertexColourArray,
		CompressedVertexTextureCoordinateArrayRecord compressedVertexTextureCoordinateArray,
		CompressedVertexFlagArrayRecord compressedVertexFlagArray,
		CompressedAuxiliaryFieldsArrayRecord compressedAuxiliaryFieldsArray,
		int jtEndIndex
		) implements BufferDeserializable {

	public static TopologicallyCompressedVertexRecordsRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex);
		
		QuantizationParametersRecord quantizationParameters = QuantizationParametersRecord.fromByteBuffer(buffer, startIndex + 8);
		int numberOfTopologicalVertices = buffer.getInt(quantizationParameters.jtEndIndex());
		Logger.debug("TopologicallyCompressedVertexRecords: vertexBindings=0x{}, topoVertices={}", Long.toHexString(vertexBindings), numberOfTopologicalVertices);
		
		int currentOffset = quantizationParameters.jtEndIndex() + 4;
		
		CompressedVertexCoordinateArrayRecord compressedVertexCoordinateArray = null;
		CompressedVertexNormalArrayRecord compressedVertexNormalArray = null;
		CompressedVertexColourArrayRecord compressedVertexColourArray = null;
		CompressedVertexTextureCoordinateArrayRecord compressedVertexTextureCoordinateArray = null;
		CompressedVertexFlagArrayRecord compressedVertexFlagArray = null;
		CompressedAuxiliaryFieldsArrayRecord compressedAuxiliaryFieldsArray = null;
		int numberOfVertexAttributes = 0;
		
		if (numberOfTopologicalVertices > 0) {
			numberOfVertexAttributes = buffer.getInt(currentOffset);
			currentOffset += 4;
			
			// Coordinates (Bits 0–2)
			if (hasCoordinateBindings(vertexBindings)) {
				compressedVertexCoordinateArray = CompressedVertexCoordinateArrayRecord.fromByteBuffer(buffer, currentOffset);
				currentOffset = compressedVertexCoordinateArray.jtEndIndex();
			}
			
			// Normals (Bit 3)
			if (hasNormalBindings(vertexBindings)) {
				try {
					compressedVertexNormalArray = CompressedVertexNormalArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedVertexNormalArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedVertexNormalArray: {}", e.getMessage());
				}
			}
			
			// Colours (Bits 4–5)
			if (hasColourBindings(vertexBindings)) {
				try {
					compressedVertexColourArray = CompressedVertexColourArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedVertexColourArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedVertexColourArray: {}", e.getMessage());
				}
			}
			
			// Texture Coordinates (Bits 8–39)
			if (hasTextureCoordinateBindings(vertexBindings)) {
				try {
					compressedVertexTextureCoordinateArray = CompressedVertexTextureCoordinateArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedVertexTextureCoordinateArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedVertexTextureCoordinateArray: {}", e.getMessage());
				}
			}
			
			// Vertex Flags (Bit 6)
			if (hasVertexFlagBindings(vertexBindings)) {
				try {
					compressedVertexFlagArray = CompressedVertexFlagArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedVertexFlagArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedVertexFlagArray: {}", e.getMessage());
				}
			}
			
			// Auxiliary Fields (Bit 7)
			if (hasAuxFieldBindings(vertexBindings)) {
				try {
					compressedAuxiliaryFieldsArray = CompressedAuxiliaryFieldsArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedAuxiliaryFieldsArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedAuxiliaryFieldsArray: {}", e.getMessage());
				}
			}
		}
		
		return new TopologicallyCompressedVertexRecordsRecord(vertexBindings, quantizationParameters, 
				numberOfTopologicalVertices, numberOfVertexAttributes, 
				compressedVertexCoordinateArray, compressedVertexNormalArray,
				compressedVertexColourArray, compressedVertexTextureCoordinateArray,
				compressedVertexFlagArray, compressedAuxiliaryFieldsArray, currentOffset);
	}

	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
	
	public boolean hasCoordinateBindings() {
		return hasCoordinateBindings(vertexBindings);
	}
	
	public boolean hasNormalBindings() {
		return hasNormalBindings(vertexBindings);
	}
	
	public boolean hasColorBindings() {
		return hasColourBindings(vertexBindings);
	}
	
	public boolean hasColourBindings() {
		return hasColourBindings(vertexBindings);
	}
	
	public boolean hasTextureCoordinateBindings() {
		return hasTextureCoordinateBindings(vertexBindings);
	}
	
	public boolean hasVertexFlagBindings() {
		return hasVertexFlagBindings(vertexBindings);
	}
	
	public boolean hasAuxFieldBindings() {
		return hasAuxFieldBindings(vertexBindings);
	}
	
	// Static helpers for use in fromByteBuffer before record is constructed
	private static boolean hasCoordinateBindings(long vb) {
		return (vb & 0b111L) != 0;
	}
	
	private static boolean hasNormalBindings(long vb) {
		return (vb & (1L << 3)) != 0;
	}
	
	private static boolean hasColourBindings(long vb) {
		return (vb & ((1L << 4) | (1L << 5))) != 0;
	}
	
	private static boolean hasTextureCoordinateBindings(long vb) {
		for (int texSet = 0; texSet < 8; texSet++) {
			int baseBit = 8 + texSet * 4;
			if ((vb & (0b1111L << baseBit)) != 0) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean hasVertexFlagBindings(long vb) {
		return (vb & (1L << 6)) != 0;
	}
	
	private static boolean hasAuxFieldBindings(long vb) {
		return (vb & (1L << 7)) != 0;
	}
}