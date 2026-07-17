package io.github.tomusin.lodDataRecords;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

/**
 * Compressed auxiliary fields array. Each field has a GUID, type, quantization info,
 * and either binary or quantized data storage depending on quantizationBits.
 */
public record CompressedAuxiliaryFieldsArrayRecord(
		int numberOfAuxiliaryFields,
		AuxiliaryField[] fields,
		int jtEndIndex
		) implements BufferDeserializable {

	/**
	 * Represents a single auxiliary field entry.
	 */
	public record AuxiliaryField(
			byte[] guid,
			int fieldType,
			int unusedField,
			int quantizationBits,
			int numberOfSteps,
			VecI32[] binaryDataLsw,
			VecI32[] binaryDataMsw,
			UniformQuantizerDataRecord quantizer,
			VecI32[] quantizedDataLsw,
			VecI32[] quantizedDataMsw,
			int auxiliaryDataHash
	) {
		/**
		 * Returns the number of components for this field type.
		 */
		public int getNumberOfComponents() {
			if (fieldType < 1 || fieldType > 46) return 1;
			// Types 1-36: groups of 4 → (fieldType-1)%4 + 1 components
			if (fieldType <= 36) return ((fieldType - 1) % 4) + 1;
			// 37=2x2(4), 38=3x3(9), 39=4x4(16)
			if (fieldType == 37) return 4;
			if (fieldType == 38) return 9;
			if (fieldType == 39) return 16;
			// 40-43: F64 1-4 components
			if (fieldType <= 43) return fieldType - 39;
			// 44=2x2(4), 45=3x3(9), 46=4x4(16)
			if (fieldType == 44) return 4;
			if (fieldType == 45) return 9;
			if (fieldType == 46) return 16;
			return 1;
		}

		/**
		 * Returns true if this field type is 64-bit (F64, U64, I64).
		 */
		public boolean is64Bit() {
			return (fieldType >= 25 && fieldType <= 32) || (fieldType >= 40 && fieldType <= 46);
		}
	}

	public static CompressedAuxiliaryFieldsArrayRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		int numberOfAuxiliaryFields = buffer.getInt(startIndex);
		Logger.debug("CompressedAuxiliaryFieldsArray: numberOfAuxiliaryFields={}", numberOfAuxiliaryFields);

		if (numberOfAuxiliaryFields < 0 || numberOfAuxiliaryFields > 1000) {
			Logger.error("CompressedAuxiliaryFieldsArray: invalid count={} at offset {}", numberOfAuxiliaryFields, startIndex);
			return new CompressedAuxiliaryFieldsArrayRecord(0, new AuxiliaryField[0], startIndex + 4);
		}

		AuxiliaryField[] fields = new AuxiliaryField[numberOfAuxiliaryFields];
		int offset = startIndex + 4;

		for (int f = 0; f < numberOfAuxiliaryFields; f++) {
			// GUID: 16 bytes
			byte[] guid = new byte[16];
			for (int i = 0; i < 16; i++) {
				guid[i] = (byte) ReadFromBufferUtils.readUnsignedByte(buffer, offset + i);
			}
			offset += 16;

			int fieldType = ReadFromBufferUtils.readUnsignedByte(buffer, offset);
			int unusedField = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 1);
			int quantizationBits = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 2);
			int numberOfSteps = ReadFromBufferUtils.readUnsignedByte(buffer, offset + 3);
			offset += 4;

			Logger.debug("AuxField[{}]: type={}, quantBits={}, steps={}", f, fieldType, quantizationBits, numberOfSteps);

			// Determine if 64-bit type
			boolean is64Bit = (fieldType >= 25 && fieldType <= 32) || (fieldType >= 40 && fieldType <= 46);

			VecI32[] binaryDataLsw = null;
			VecI32[] binaryDataMsw = null;
			UniformQuantizerDataRecord quantizer = null;
			VecI32[] quantizedDataLsw = null;
			VecI32[] quantizedDataMsw = null;
			int auxiliaryDataHash = 0;

			if (quantizationBits == 0) {
				// Binary storage
				binaryDataLsw = new VecI32[numberOfSteps];
				if (is64Bit) binaryDataMsw = new VecI32[numberOfSteps];

				for (int s = 0; s < numberOfSteps; s++) {
					binaryDataLsw[s] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
					offset = binaryDataLsw[s].jtEndIndex();

					if (is64Bit) {
						binaryDataMsw[s] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
						offset = binaryDataMsw[s].jtEndIndex();
					}
				}
			} else if (quantizationBits > 1) {
				// Quantized storage
				quantizer = UniformQuantizerDataRecord.fromByteBuffer(buffer, offset, 'q');
				offset = quantizer.jtEndIndex();

				boolean isF64 = (fieldType >= 40 && fieldType <= 46);
				quantizedDataLsw = new VecI32[numberOfSteps];
				if (isF64) quantizedDataMsw = new VecI32[numberOfSteps];

				for (int s = 0; s < numberOfSteps; s++) {
					quantizedDataLsw[s] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
					offset = quantizedDataLsw[s].jtEndIndex();

					if (isF64) {
						quantizedDataMsw[s] = TopologicallyCompressedRepDataRecord.readInt32CDP(buffer, offset);
						offset = quantizedDataMsw[s].jtEndIndex();
					}
				}

				auxiliaryDataHash = buffer.getInt(offset);
				offset += 4;
			} else {
				Logger.warn("AuxField[{}]: unexpected quantizationBits={}", f, quantizationBits);
			}

			fields[f] = new AuxiliaryField(guid, fieldType, unusedField, quantizationBits, numberOfSteps,
					binaryDataLsw, binaryDataMsw, quantizer, quantizedDataLsw, quantizedDataMsw, auxiliaryDataHash);
		}

		return new CompressedAuxiliaryFieldsArrayRecord(numberOfAuxiliaryFields, fields, offset);
	}
}
