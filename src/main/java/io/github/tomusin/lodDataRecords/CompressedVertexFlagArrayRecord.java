package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;

/**
 * Compressed vertex flag array — a single Int32CDP vector of per-vertex flags.
 */
public record CompressedVertexFlagArrayRecord(
		int vertexFlagCount,
		VecI32 vertexFlags,
		int jtEndIndex
		) implements BufferDeserializable {

	public static CompressedVertexFlagArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int vertexFlagCount = buffer.getInt(startIndex);
		VecI32 vertexFlags = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, startIndex + 4);
		Logger.debug("CompressedVertexFlagArray: declared count={}, decoded count={}", vertexFlagCount, vertexFlags.count());
		return new CompressedVertexFlagArrayRecord(vertexFlagCount, vertexFlags, vertexFlags.jtEndIndex());
	}
}
