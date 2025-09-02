package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopologicallyCompressedVertexRecordsRecord(
		long vertexBindings,
		QuantizationParametersRecord quantizationParameters,
		int numberOfTopoligicalVertices,
		int numberOfVertexAttributes,
		CompressedVertexCoordinateArrayRecord compressedVertexCoordinateArray,
		CompressedVertexNormalArrayRecord compressedVertexNormalArray
		// CompressedVertexColorArrayRecord compressedVertexColorArray
		// CompressedVertexTextureCoordinateArray[] compressedVertexTextureCoordinateArray
		// CompressedVertexFlagArray compressedVertexFlagArray
		// CompressedAuxiliaryFields Array 
		) implements BufferDeserializable {

	public static TopologicallyCompressedVertexRecordsRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex);
		QuantizationParametersRecord quantizationParameters = QuantizationParametersRecord.fromByteBuffer(buffer, startIndex + 8);
		int numberOfTopoligicalVertices = buffer.getInt(quantizationParameters.jtEndIndex());
		int numberOfVertexAttributes = buffer.getInt(quantizationParameters.jtEndIndex() + 4);
		CompressedVertexCoordinateArrayRecord compressedVertexCoordinateArray = CompressedVertexCoordinateArrayRecord.fromByteBuffer(buffer, quantizationParameters.jtEndIndex() + 8);
		CompressedVertexNormalArrayRecord compressedVertexNormalArray = CompressedVertexNormalArrayRecord.fromByteBuffer(buffer, compressedVertexCoordinateArray.jtEndIndex());
		handleVertexBindings(vertexBindings);
		return new TopologicallyCompressedVertexRecordsRecord(vertexBindings, quantizationParameters, numberOfTopoligicalVertices, numberOfVertexAttributes, compressedVertexCoordinateArray, compressedVertexNormalArray);
	}
	@Override
	public int jtEndIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public static void handleVertexBindings(long bindings) {
        // Coordinates (Bits 1–3)
        if ((bindings & 0b111L) != 0) {
            handleCoordinates();
        }

        // Normals (Bit 4)
        if ((bindings & (1L << 3)) != 0) {
            handleNormals();
        }

        // Colors (Bits 5–6)
        if ((bindings & ((1L << 4) | (1L << 5))) != 0) {
            handleColors();
        }

        // Flags (Bit 7)
        if ((bindings & (1L << 6)) != 0) {
            handleVertexFlags();
        }

        // Texture Coordinates (Bits 9–40 → 8 groups, each 4 bits)
        boolean anyTexCoords = false;
        for (int texSet = 0; texSet < 8; texSet++) {
            int baseBit = 8 + texSet * 4;
            long mask = (0b1111L << baseBit);
            if ((bindings & mask) != 0) {
                anyTexCoords = true;
                break;
            }
        }
        if (anyTexCoords) {
            handleTextureCoordinates();
        }

        // Auxiliary Field (Bit 64 → index 63)
        if ((bindings & (1L << 63)) != 0) {
            handleAuxiliaryField();
        }
    }

    // === Stub methods for different types ===

    private static void handleCoordinates() {
        System.out.println("Coordinates present");
    }

    private static void handleNormals() {
        System.out.println("Normals present");
    }

    private static void handleColors() {
        System.out.println("Colors present");
    }

    private static void handleVertexFlags() {
        System.out.println("Vertex flags present");
    }

    private static void handleTextureCoordinates() {
        System.out.println("Texture coordinates present");
    }

    private static void handleAuxiliaryField() {
        System.out.println("Auxiliary field present");
    }

}
