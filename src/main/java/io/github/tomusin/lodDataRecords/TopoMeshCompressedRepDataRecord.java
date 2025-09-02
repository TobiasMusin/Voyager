package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopoMeshCompressedRepDataRecord(
		long numberOfFaceGroupListIndices,
		long numberOfPrimitiveListIndices,
		long numberOfVertexListIndices,
		VecI32 faceGroupListIndices,
		VecI32 primitiveListIndices,
		VecI32 vertexListIndices,
		int fgpvListIndicesHash,
		long vertexBindings
		//quantizationParameters
		// numberOfVertexRecords
		//... See page 94 Figure 89
		) {
	public static TopoMeshCompressedRepDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		
		for (int i = -10; i < 10; i++) {
			long numberOfFaceGroupListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + i);
			long numberOfPrimitiveListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 4 + i);
			long numberOfVertexListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 8 + i);
			System.out.println("-------------------");
			System.out.println("numberOfFaceGroupListIndices: " + numberOfFaceGroupListIndices + " at index: " + i);
			System.out.println("numberOfPrimitiveListIndices: " + numberOfPrimitiveListIndices + " at index: " + i);
			System.out.println("numberOfVertexListIndices: " + numberOfVertexListIndices + " at index: " + i);
			System.out.println("-------------------");
		}
		
		long numberOfFaceGroupListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex);
		long numberOfPrimitiveListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 4);
		long numberOfVertexListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 8);
		VecI32 faceGroupListIndices = VecI32.fromByteBuffer(buffer, startIndex + 12);
		VecI32 primitiveListIndices = VecI32.fromByteBuffer(buffer, faceGroupListIndices.jtEndIndex());
		VecI32 vertexListIndices = VecI32.fromByteBuffer(buffer, primitiveListIndices.jtEndIndex());
		int fgpvListIndicesHash = buffer.getInt(vertexListIndices.jtEndIndex());
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, vertexListIndices.jtEndIndex() + 4);
		return new TopoMeshCompressedRepDataRecord(numberOfFaceGroupListIndices, numberOfPrimitiveListIndices, numberOfVertexListIndices, faceGroupListIndices, primitiveListIndices, vertexListIndices, fgpvListIndicesHash, vertexBindings);
	}
}
