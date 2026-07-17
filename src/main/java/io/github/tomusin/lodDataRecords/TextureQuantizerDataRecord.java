package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;

/**
 * Texture quantizer data — one UniformQuantizerData per texture component.
 */
public record TextureQuantizerDataRecord(
		UniformQuantizerDataRecord[] componentQuantizers,
		int jtEndIndex
		) implements BufferDeserializable {

	public static TextureQuantizerDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex, int numberComponents) {
		UniformQuantizerDataRecord[] quantizers = new UniformQuantizerDataRecord[numberComponents];
		int offset = startIndex;
		for (int i = 0; i < numberComponents; i++) {
			quantizers[i] = UniformQuantizerDataRecord.fromByteBuffer(buffer, offset, (char) ('u' + i));
			offset = quantizers[i].jtEndIndex();
		}
		Logger.debug("TextureQuantizerData: {} components parsed", numberComponents);
		return new TextureQuantizerDataRecord(quantizers, offset);
	}
}
