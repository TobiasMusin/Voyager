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
		// Per JT spec the Compressed Vertex Flag Array is a single Int32CDP — no separate leading count field.
		// The count is embedded in the CDP header itself.
		VecI32 vertexFlags = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, startIndex);
		Logger.debug("CompressedVertexFlagArray: vertexFlagCount={} (from CDP)", vertexFlags.count());
		return new CompressedVertexFlagArrayRecord(vertexFlags.count(), vertexFlags, vertexFlags.jtEndIndex());
	}
}
