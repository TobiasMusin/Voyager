package io.github.tomusin.lodDataRecords;


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