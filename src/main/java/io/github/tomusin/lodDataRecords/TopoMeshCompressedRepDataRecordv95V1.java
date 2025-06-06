package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopoMeshCompressedRepDataRecordv95V1(
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
	public static TopoMeshCompressedRepDataRecordv95V1 fromByteBuffer(ByteBuffer buffer, int startIndex, boolean polyLineShape) {
		long numberOfFaceGroupListIndices = 0;
		if (polyLineShape) {
			numberOfFaceGroupListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex);
			startIndex += 4;
		}
		
		long numberOfPrimitiveListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex);
		long numberOfVertexListIndices = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 4);
		VecI32 faceGroupListIndices = null;
		startIndex = startIndex + 8;
		if (polyLineShape) {
			faceGroupListIndices = VecI32.fromByteBuffer(buffer, startIndex + 12);
			startIndex = faceGroupListIndices.jtEndIndex();
		}
		VecI32 primitiveListIndices = VecI32.fromByteBuffer(buffer, faceGroupListIndices.jtEndIndex());
		VecI32 vertexListIndices = VecI32.fromByteBuffer(buffer, primitiveListIndices.jtEndIndex());
		int fgpvListIndicesHash = buffer.getInt(vertexListIndices.jtEndIndex());
		long vertexBindings = ReadFromBufferUtils.readUnsignedLong(buffer, vertexListIndices.jtEndIndex() + 4);
		return new TopoMeshCompressedRepDataRecordv95V1(numberOfFaceGroupListIndices, numberOfPrimitiveListIndices, numberOfVertexListIndices, faceGroupListIndices, primitiveListIndices, vertexListIndices, fgpvListIndicesHash, vertexBindings);
	}
}
