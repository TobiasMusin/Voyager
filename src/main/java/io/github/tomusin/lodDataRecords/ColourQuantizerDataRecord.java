package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

/**
 * Colour quantizer data. If hsvFlag == 1, HSV quantization with fixed ranges is used.
 * Otherwise, independent RGBA uniform quantizers are used.
 */
public record ColourQuantizerDataRecord(
		int hsvFlag,
		int numberOfHueBits,
		int numberOfSaturationBits,
		int numberOfValueBits,
		int numberOfAlphaBits,
		UniformQuantizerDataRecord redQuantizer,
		UniformQuantizerDataRecord greenQuantizer,
		UniformQuantizerDataRecord blueQuantizer,
		UniformQuantizerDataRecord alphaQuantizer,
		int jtEndIndex
		) implements BufferDeserializable {

	public static ColourQuantizerDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int hsvFlag = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex);
		int offset = startIndex + 1;

		int numberOfHueBits = 0, numberOfSaturationBits = 0, numberOfValueBits = 0, numberOfAlphaBits = 0;
		UniformQuantizerDataRecord redQ = null, greenQ = null, blueQ = null, alphaQ = null;

		if (hsvFlag == 1) {
			// HSV quantization with fixed ranges
			numberOfHueBits = ReadFromBufferUtils.readUnsignedByte(buffer, offset);
			numberOfSaturationBits = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 1);
			numberOfValueBits = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 2);
			numberOfAlphaBits = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 3);
			offset += 4;
			Logger.debug("ColourQuantizerData: HSV mode, hueBits={}, satBits={}, valBits={}, alphaBits={}",
					numberOfHueBits, numberOfSaturationBits, numberOfValueBits, numberOfAlphaBits);
		} else {
			// RGBA uniform quantizers
			redQ = UniformQuantizerDataRecord.fromByteBuffer(buffer, offset, 'r');
			greenQ = UniformQuantizerDataRecord.fromByteBuffer(buffer, redQ.jtEndIndex(), 'g');
			blueQ = UniformQuantizerDataRecord.fromByteBuffer(buffer, greenQ.jtEndIndex(), 'b');
			alphaQ = UniformQuantizerDataRecord.fromByteBuffer(buffer, blueQ.jtEndIndex(), 'a');
			offset = alphaQ.jtEndIndex();
			Logger.debug("ColourQuantizerData: RGBA mode");
		}

		return new ColourQuantizerDataRecord(hsvFlag, numberOfHueBits, numberOfSaturationBits,
				numberOfValueBits, numberOfAlphaBits, redQ, greenQ, blueQ, alphaQ, offset);
	}
}
