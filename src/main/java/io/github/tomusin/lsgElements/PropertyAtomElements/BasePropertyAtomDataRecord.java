package io.github.tomusin.lsgElements.PropertyAtomElements;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record BasePropertyAtomDataRecord(int versionNumber, long stateFlags, int jtEndIndex) implements BufferDeserializable {

	public static BasePropertyAtomDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		return ReadNodesFromBufferUtils.readBasePropertyAtomData(startIndex, buffer);
	}
	@Override
	public int jtEndIndex() {
		return jtEndIndex;
	}

}
