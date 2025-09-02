package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.VertexShapeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

public record TriStripSetShapeNodeElementRecord(VertexShapeDataRecord vertexShapeDataRecord) implements BufferDeserializable {

	// This as of now seems to rresult in wrong values
	public static TriStripSetShapeNodeElementRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return new TriStripSetShapeNodeElementRecord(VertexShapeDataRecord.fromByteBuffer(buffer, startIndex));
	}
	@Override
	public int jtEndIndex() {
		return vertexShapeDataRecord.jtEndIndex();
	}
	
}
