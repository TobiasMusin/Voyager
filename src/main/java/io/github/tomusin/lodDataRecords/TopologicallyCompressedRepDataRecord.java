package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record TopologicallyCompressedRepDataRecord(
		VecI32[] faceDegrees,
		VecI32 vertexValences,
		VecI32 vertexGroups,
		VecI32 vertexFlags,
		VecI32[] faceAttributeMasks,
		VecI32 faceAttributeMask8,
		VecU32 highDegreeFaceAttributeMasks,
		VecI32 splitFaceSyms,
		VecI32 splitFacePositions,
		long compositeHash,
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords
		) implements BufferDeserializable {

	public static TopologicallyCompressedRepDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		VecI32[] faceDegrees = new VecI32[8];
		for (int i = 0; i < 8; i++) {
			faceDegrees[i] = VecI32.fromByteBuffer(buffer, startIndex);
			startIndex = faceDegrees[i].jtEndIndex();
		}
		VecI32 vertexValences = VecI32.fromByteBuffer(buffer, startIndex);
		VecI32 vertexGroups = VecI32.fromByteBuffer(buffer, vertexValences.jtEndIndex());
		VecI32 vertexFlags = VecI32.fromByteBuffer(buffer, vertexGroups.jtEndIndex());
		VecI32[] faceAttributeMasks = new VecI32[8];
		startIndex = vertexFlags.jtEndIndex();
		for (int i = 0; i < 8; i++) {
			faceAttributeMasks[i] = VecI32.fromByteBuffer(buffer, startIndex);
			startIndex = faceAttributeMasks[i].jtEndIndex();
		}
		VecI32 faceAttributeMask8 = VecI32.fromByteBuffer(buffer, startIndex);
		VecU32 highDegreeFaceAttributeMasks = VecU32.fromByteBuffer(buffer, faceAttributeMask8.jtEndIndex());
		VecI32 splitFaceSyms = VecI32.fromByteBuffer(buffer, highDegreeFaceAttributeMasks.jtEndIndex());
		VecI32 splitFacePositions = VecI32.fromByteBuffer(buffer, splitFaceSyms.jtEndIndex());
		long compositeHash = ReadFromBufferUtils.readUnsignedInt(buffer, splitFacePositions.jtEndIndex());
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords = TopologicallyCompressedVertexRecordsRecord.fromByteBuffer(buffer, splitFacePositions.jtEndIndex() + 4);
		return new TopologicallyCompressedRepDataRecord(faceDegrees, vertexValences, vertexGroups, vertexFlags, faceAttributeMasks, faceAttributeMask8, highDegreeFaceAttributeMasks, splitFaceSyms, splitFacePositions, compositeHash, topologicallyCompressedVertexRecords);
	}
	@Override
	public int jtEndIndex() {
		return topologicallyCompressedVertexRecords.jtEndIndex();
	}

}
