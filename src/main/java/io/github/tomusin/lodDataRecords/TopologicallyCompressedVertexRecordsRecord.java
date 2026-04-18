package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

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
		int jtEndIndex
		) implements BufferDeserializable {

	public static TopologicallyCompressedVertexRecordsRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex);
		Logger.info("vertexBindings = 0x{}", Long.toHexString(vertexBindings));
		
		QuantizationParametersRecord quantizationParameters = QuantizationParametersRecord.fromByteBuffer(buffer, startIndex + 8);
		int numberOfTopologicalVertices = buffer.getInt(quantizationParameters.jtEndIndex());
		Logger.info("numberOfTopologicalVertices = {}", numberOfTopologicalVertices);
		
		int currentOffset = quantizationParameters.jtEndIndex() + 4;
		
		CompressedVertexCoordinateArrayRecord compressedVertexCoordinateArray = null;
		CompressedVertexNormalArrayRecord compressedVertexNormalArray = null;
		int numberOfVertexAttributes = 0;
		
		if (numberOfTopologicalVertices > 0) {
			numberOfVertexAttributes = buffer.getInt(currentOffset);
			Logger.info("numberOfVertexAttributes = {}", numberOfVertexAttributes);
			currentOffset += 4;
			
			// Coordinates (Bits 0–2)
			boolean hasCoordinates = (vertexBindings & 0b111L) != 0;
			if (hasCoordinates) {
				Logger.info("Parsing CompressedVertexCoordinateArray at offset {}", currentOffset);
				compressedVertexCoordinateArray = CompressedVertexCoordinateArrayRecord.fromByteBuffer(buffer, currentOffset);
				currentOffset = compressedVertexCoordinateArray.jtEndIndex();
			}
			
			// Normals (Bit 3)
			boolean hasNormals = (vertexBindings & (1L << 3)) != 0;
			if (hasNormals) {
				try {
					Logger.info("Parsing CompressedVertexNormalArray at offset {}", currentOffset);
					compressedVertexNormalArray = CompressedVertexNormalArrayRecord.fromByteBuffer(buffer, currentOffset);
					currentOffset = compressedVertexNormalArray.jtEndIndex();
				} catch (Exception e) {
					Logger.warn("Failed to parse CompressedVertexNormalArray: {}", e.getMessage());
				}
			}
			
			// Colors (Bits 4–5) - skip for now
			boolean hasColors = (vertexBindings & ((1L << 4) | (1L << 5))) != 0;
			if (hasColors) {
				Logger.warn("Color bindings present but not yet implemented - parsing may be incorrect after this point");
			}
			
			// Texture Coordinates (Bits 8–39) - skip for now
			boolean hasTexCoords = false;
			for (int texSet = 0; texSet < 8; texSet++) {
				int baseBit = 8 + texSet * 4;
				if ((vertexBindings & (0b1111L << baseBit)) != 0) {
					hasTexCoords = true;
					break;
				}
			}
			if (hasTexCoords) {
				Logger.warn("Texture coordinate bindings present but not yet implemented");
			}
			
			// Vertex Flags (Bit 6) - skip for now
			if ((vertexBindings & (1L << 6)) != 0) {
				Logger.warn("Vertex flag bindings present but not yet implemented");
			}
		}
		
		Logger.info("TopologicallyCompressedVertexRecords ends at offset {}", currentOffset);
		return new TopologicallyCompressedVertexRecordsRecord(vertexBindings, quantizationParameters, 
				numberOfTopologicalVertices, numberOfVertexAttributes, 
				compressedVertexCoordinateArray, compressedVertexNormalArray, currentOffset);
	}

	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}
	
	public boolean hasCoordinateBindings() {
		return (vertexBindings & 0b111L) != 0;
	}
	
	public boolean hasNormalBindings() {
		return (vertexBindings & (1L << 3)) != 0;
	}
	
	public boolean hasColorBindings() {
		return (vertexBindings & ((1L << 4) | (1L << 5))) != 0;
	}
}