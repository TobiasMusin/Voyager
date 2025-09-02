package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record CompressedVertexCoordinateArrayRecord(
		int uniqueVertexCount,
		int numberComponents,
		PointQuantizerDataRecord pointQuantizerData,
		VecU32[] binaryVertexCoords,
		VecU32[] vertexCoordCords,
		int vertexCoordinateHash,
		int jtEndIndex
		) implements BufferDeserializable {
	
	public static CompressedVertexCoordinateArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int uniqueVertexCount = buffer.getInt(startIndex);
		int numberComponents = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4);
		PointQuantizerDataRecord pointQuantizerData = PointQuantizerDataRecord.fromByteBuffer(buffer, startIndex + 5);
		// From the documentation:
		// The above predicates “QuantBits = 0” and “QuantBits > 0” refer to the value of the field U8: Number Of 
		// Bits stored in the three components of Point Quantizer Data. All three of these fields are required to be 
		// equal
		
		// For now we just assume they are equal and only make a 'lazy' check if they are greater or equal 0
		int quantBits = pointQuantizerData.xUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.yUniformQuantizerData().numberOfBits() 
				+ pointQuantizerData.zUniformQuantizerData().numberOfBits();
		
		VecU32[] vertexCoordCords = null;
		VecU32[] binaryVertexCoords = null;
		
		int nextVectorStartIndex = pointQuantizerData.zUniformQuantizerData().jtEndIndex();
		if (quantBits > 0) {
			vertexCoordCords = new VecU32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				vertexCoordCords[i] = VecU32.fromByteBuffer(buffer, nextVectorStartIndex);
				nextVectorStartIndex = vertexCoordCords[i].jtEndIndex();
			}
		} else if (quantBits == 0) {
			binaryVertexCoords = new VecU32[numberComponents];
			for (int i = 0; i < numberComponents; i++) {
				binaryVertexCoords[i] = VecU32.fromByteBuffer(buffer, nextVectorStartIndex);
				nextVectorStartIndex = binaryVertexCoords[i].jtEndIndex();
			}
		} else {
			Logger.error("Something went wrong when checking the number of QuantBits. Maybe you are at the wrong buffer index.");
		}
		int vertexCoordinateHash = buffer.getInt(nextVectorStartIndex);
		return new CompressedVertexCoordinateArrayRecord(uniqueVertexCount, numberComponents, pointQuantizerData, binaryVertexCoords, vertexCoordCords, vertexCoordinateHash, nextVectorStartIndex + 4);
	}
}
