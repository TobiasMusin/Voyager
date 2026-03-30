package io.github.tomusin.lodDataRecords;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.BitReader;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 97, Figure 92
public record TopologicallyCompressedRepDataRecord(VecI32[] faceDegrees, VecI32 vertexValences, VecI32 vertexGroups,
		VecI32 vertexFlags, VecI32[] faceAttributeMasks, VecI32 faceAttributeMask8, VecU32 highDegreeFaceAttributeMasks,
		VecI32 splitFaceSyms, VecI32 splitFacePositions, long compositeHash,
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords)
		implements BufferDeserializable {

	static int bitsizeSigned(int x) {
		x ^= (x >> 31); // make positive
		return 33 - Integer.numberOfLeadingZeros(x);
	}
	
	static int bitsize(int value) {
	    if (value == 0) return 0;
	    return 32 - Integer.numberOfLeadingZeros(value);
	}
	
	/**
	 * Predictor types used for residual unpacking.
	 * Reference: codecDriverClass.cpp lines 17-22
	 */
	enum PredictorType {
		PredLag1,   // (0) Predicts as last value, uses subtraction/addition
		PredXor1,   // (1) Predicts as last value, uses XOR
		PredNULL    // (2) No prediction
	}
	
	/**
	 * Unpacks residuals into actual values using lag-1 prediction.
	 * 
	 * Reference: codecDriverClass.cpp - unpackResiduals() method (lines 37-64)
	 * 
	 * The codec decoding produces residuals (differences from predicted values).
	 * This method reconstructs the actual values by:
	 * 1. Using first 4 values as primers (raw values, no prediction)
	 * 2. For values 5+: reconstructing as (residual + predicted_value)
	 * 3. Predicted value = previous value (lag-1 prediction)
	 * 
	 * @param residuals Array of residual values from codec
	 * @param predictorType Type of prediction to apply
	 */
	private static void unpackResiduals(int[] residuals, PredictorType predictorType) {
		final int len = residuals.length;
		
		// First 4 values are primers - use as-is, no prediction applied
		// Values at indices 0, 1, 2, 3 are raw values
		
		// For indices 4+ (5th value onwards), apply prediction
		for (int i = 4; i < len; i++) {
			// Get predicted value from previous value (lag-1 prediction)
			int predicted = residuals[i - 1];
			
			if (predictorType == PredictorType.PredXor1) {
				// XOR-based prediction: value = residual XOR predicted
				residuals[i] = residuals[i] ^ predicted;
			} else {
				// Standard prediction: value = residual + predicted
				residuals[i] = residuals[i] + predicted;
			}
		}
	}

	/**
	 * Reads a CDP (Compressed Data Package) encoded Int32 vector from the buffer.
	 * 
	 * CDP Format (validated against C++ reference implementation):
	 * - Bits 0-31: Value count (I32) - number of int32 values encoded
	 * - Bits 32-39: CODEC type (U8) - which codec was used
	 * - Bits 40-71: Code text length (I32) - number of bits in encoded data
	 * - Bits 72+: Encoded data (codec-specific)
	 * 
	 * @param buffer The bit-level buffer to read from
	 * @param startIndex Bit offset to start reading (must be exact bit position)
	 * @return VecI32 with decoded values and jtEndIndex as exact bit position (not aligned)
	 */
	/**
	 * Scans forward from startIndex looking for a valid CDP header.
	 * Useful for finding the actual start of CDP data if offset is off.
	 */
	private static int findValidCDPOffset(BitByteBuffer buffer, int startIndex, int maxScanBytes) {
		Logger.info("Scanning for valid CDP header starting at byte offset {}", startIndex);
		
		int scanStart = Math.max(0, startIndex - maxScanBytes);
		int scanEnd = Math.min(buffer.capacity() - 9, startIndex + maxScanBytes);
		
		Logger.info("  Scan range: {} to {} ({} bytes)", scanStart, scanEnd, scanEnd - scanStart);
		
		for (int offset = scanStart; offset < scanEnd; offset++) {
			int valueCount = buffer.getInt(offset);
			int codecType = buffer.get(offset + 4) & 0xFF;
			int codeTextLengthBits = buffer.getInt(offset + 5);
			
			// Validate: check if this looks like a valid CDP header
			// For NULL codec: codeTextLengthBits should be valueCount * 32
			// For other codecs: codeTextLengthBits should be reasonable
			boolean looksValid = false;
			
			if (codecType >= 0 && codecType <= 5 && valueCount >= 1 && valueCount <= 100000) {
				if (codecType == 0) {
					// NULL codec: must have exactly valueCount * 32 bits
					if (codeTextLengthBits == valueCount * 32) {
						looksValid = true;
					}
				} else {
					// Other codecs: compressed, so should be less than valueCount * 32
					if (codeTextLengthBits > 0 && codeTextLengthBits < valueCount * 32 && codeTextLengthBits <= buffer.capacity() * 8) {
						looksValid = true;
					}
				}
			}
			
			if (looksValid) {
				Logger.info("  ✓ Found VALID CDP at offset {}: valueCount={}, codecType={}, codeTextLength={}, distanceFromExpectedOffset={}", 
				           offset, valueCount, codecType, codeTextLengthBits, offset - startIndex);
				return offset;
			}
		}
		
		Logger.warn("  ✗ No valid CDP header found in range");
		return startIndex;  // Fall back to original offset
	}

	private static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex) {
		// ========== CDP FORMAT (Byte-aligned) ==========
		// startIndex is in BYTES (must be byte-aligned)
		// CDP structure:
		// Bytes 0-3:   valueCount (I32)
		// Bytes 4-4:   codecType (U8)
		// Bytes 5-8:   codeTextLength (I32)
		// Bytes 9+:    Encoded data
		
		Logger.info("readInt32CDP called with startIndex={} bytes", startIndex);
		Logger.info("  Buffer capacity: {} bytes", buffer.capacity());
		
		// Bounds check
		if (startIndex < 0 || startIndex + 9 > buffer.capacity()) {
			Logger.error("readInt32CDP: startIndex {} is out of bounds (buffer capacity {})", 
			            startIndex, buffer.capacity());
			return new VecI32(0, new int[0], startIndex);
		}
		
		// ========== HEADER PARSING (byte-aligned) ==========
		// Bytes 0-3: Count of decoded values (signed 32-bit)
		int valueCount = buffer.getInt(startIndex);
		
		// Bytes 4: CODEC type (unsigned 8-bit)
		int codecType = buffer.get(startIndex + 4) & 0xFF;
		
		// Bytes 5-8: Length of encoded data in bits (signed 32-bit)
		int codeTextLengthBits = buffer.getInt(startIndex + 5);
		
		Logger.info("CDP Header parsing at byte offset {}:", startIndex);
		Logger.info("  Raw bytes: 0x{:02X}{:02X}{:02X}{:02X} 0x{:02X} 0x{:02X}{:02X}{:02X}{:02X}", 
		           buffer.get(startIndex) & 0xFF,
		           buffer.get(startIndex + 1) & 0xFF,
		           buffer.get(startIndex + 2) & 0xFF,
		           buffer.get(startIndex + 3) & 0xFF,
		           buffer.get(startIndex + 4) & 0xFF,
		           buffer.get(startIndex + 5) & 0xFF,
		           buffer.get(startIndex + 6) & 0xFF,
		           buffer.get(startIndex + 7) & 0xFF,
		           buffer.get(startIndex + 8) & 0xFF);
		Logger.info("  valueCount={}, codecType={}, codeTextLengthBits={}", 
		           valueCount, codecType, codeTextLengthBits);
		
		// ========== VALIDATION & DEBUGGING ==========
		// Check for unrealistic code text length (common indicator of wrong offset)
		if (codeTextLengthBits > buffer.capacity() * 8) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}", startIndex);
			Logger.error("  codeTextLengthBits {} exceeds buffer capacity {} bits", 
			            codeTextLengthBits, buffer.capacity() * 8);
			Logger.warn("  Attempting to find valid CDP offset with EXTENDED SCAN...");
			int validOffset = findValidCDPOffset(buffer, startIndex, 50);  // Scan ±250 bytes
			if (validOffset != startIndex) {
				Logger.warn("  Retrying with offset {}", validOffset);
				return readInt32CDP(buffer, validOffset);  // Recursive call with corrected offset
			}
			return new VecI32(0, new int[0], startIndex + 9);
		}
		
		// Check for NULL codec with invalid codeTextLength
		if (codecType == 0 && codeTextLengthBits != valueCount * 32) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}", startIndex);
			Logger.error("  NULL codec requires codeTextLengthBits={} (valueCount={} * 32)", 
			            valueCount * 32, valueCount);
			Logger.error("  But got codeTextLengthBits={}", codeTextLengthBits);
			Logger.warn("  Attempting to find valid CDP offset with EXTENDED SCAN...");
			int validOffset = findValidCDPOffset(buffer, startIndex, 500);  // Scan ±250 bytes
			if (validOffset != startIndex) {
				Logger.warn("  Retrying with offset {}", validOffset);
				return readInt32CDP(buffer, validOffset);  // Recursive call with corrected offset
			}
			return new VecI32(0, new int[0], startIndex + 9);
		}
		
		// Check for unrealistic value counts (indicates wrong offset)
		if (valueCount < 0 || valueCount > 1000000) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}", startIndex);
			Logger.error("  Invalid valueCount: {} (expected 0-1000000)", valueCount);
			Logger.error("  CodecType: {} (0x{:02X})", codecType, codecType & 0xFF);
			Logger.error("  CodeTextLengthBits: {}", codeTextLengthBits);
			Logger.error("  Possible causes:");
			Logger.error("    - Wrong byte offset passed");
			Logger.error("    - Buffer too small or offset out of bounds");
			Logger.warn("  Attempting to find valid CDP offset with EXTENDED SCAN...");
			int validOffset = findValidCDPOffset(buffer, startIndex, 500);  // Scan ±250 bytes
			if (validOffset != startIndex) {
				Logger.warn("  Retrying with offset {}", validOffset);
				return readInt32CDP(buffer, validOffset);  // Recursive call with corrected offset
			}
			
			// Return empty result instead of crashing (9 byte header + 0 data = 9 bytes)
			return new VecI32(0, new int[0], startIndex + 9);
		}
		
		// Check for invalid codec type
		if (codecType < 0 || codecType > 5) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}", startIndex);
			Logger.error("  Invalid codecType: {} (must be 0-5)", codecType);
			Logger.error("  ValueCount: {}", valueCount);
			Logger.error("  CodeTextLengthBits: {}", codeTextLengthBits);
			Logger.error("  Possible causes: Wrong byte offset or corrupted data");
			
			// Return empty result instead of crashing (9 byte header)
			return new VecI32(0, new int[0], startIndex + 9);
		}
		
		// Check for negative code text length (common sign error)
		if (codeTextLengthBits < 0) {
			Logger.warn("CDP WARNING at byte offset {}", startIndex);
			Logger.warn("  Negative codeTextLengthBits: {} (treating as 0)", codeTextLengthBits);
			codeTextLengthBits = 0;
		}
		
		Logger.debug("CDP Header: valueCount={}, codec={}, codeTextLengthBits={} at byteOffset={}", 
		             valueCount, codecType, codeTextLengthBits, startIndex);
		
		// CDP structure in bytes:
		// Bytes 0-3:   valueCount (4 bytes)
		// Byte 4:      codecType (1 byte)
		// Bytes 5-8:   codeTextLength (4 bytes)
		// Total header: 9 bytes
		// Bytes 9+:    Encoded data (variable, size in bits: codeTextLengthBits)
		
		int cdpHeaderBytes = 9;
		int encodedDataStartByte = startIndex + cdpHeaderBytes;
		int encodedDataStartBit = encodedDataStartByte * 8;
		int endBit = encodedDataStartBit + codeTextLengthBits;
		int endByte = (endBit + 7) / 8;  // Round up to next byte boundary
		
		int[] decodedValues = new int[(int) valueCount];
		
		// ========== CODEC DISPATCH ==========
		switch (codecType) {
		case 0 -> decodeNullCodec(buffer, encodedDataStartBit, codeTextLengthBits, 
		                            decodedValues, (int) valueCount);
		case 1 -> decodeBitlengthCodec(buffer, encodedDataStartBit, codeTextLengthBits,
		                                decodedValues, (int) valueCount);
		case 3 -> decodeArithmeticCodec(buffer, encodedDataStartBit, codeTextLengthBits,
		                                 decodedValues, (int) valueCount);
		case 2 -> System.err.println("WARNING: CODEC 2 (Illegal) not implemented");
		case 4 -> System.err.println("WARNING: CODEC 4 (Chopper) not yet implemented");
		case 5 -> System.err.println("WARNING: CODEC 5 (Move-to-Front) not yet implemented");
		default -> System.err.println("ERROR: Unknown CODEC type: " + codecType);
		}
		
		// ========== RESIDUAL UNPACKING ==========
		// Reference: codecDriverClass.cpp - unpackResiduals()
		// The codec decoding returns residuals that must be reconstructed using prediction.
		// This matches the C++ CodecDriver::unpackResiduals() logic.
		if (valueCount > 0) {
			unpackResiduals(decodedValues, PredictorType.PredLag1);
		}
		
		// Return with byte position (consistent with rest of codebase)
		// Callers expect byte offsets and will multiply by 8 if needed for bit operations
		return new VecI32((int) valueCount, decodedValues, endByte);
	}
	
	/**
	 * Decodes Null CODEC (codec type 0) - Reference: Arithmetic.cpp, nullCodec pattern
	 * In Null CODEC, values are stored completely unencoded as raw 32-bit signed integers.
	 * This is used when compression would be ineffective.
	 */
	private static void decodeNullCodec(BitByteBuffer buffer, int startBit, int lengthBits,
	                                     int[] outValues, int valueCount) {
		Logger.debug("Decoding Null CODEC: {} values, {} bits encoded data", valueCount, lengthBits);
		
		// Null codec stores raw 32-bit signed integers sequentially
		// Each value occupies exactly 32 bits
		for (int i = 0; i < valueCount; i++) {
			outValues[i] = buffer.getIntAtBitPosition(startBit + i * 32);
		}
	}
	
	/**
	 * Decodes Bitlength CODEC (codec type 1) - Reference: Bitlength.cpp (lines 13-200+)
	 * 
	 * Bitlength codec uses variable-width integer encoding based on the value range.
	 * Algorithm:
	 * 1. If range is small: use fixed-width encoding for all values
	 * 2. If range is large: use variable-width fields with adaptive field width
	 * 
	 * Values are encoded relative to a mean value to reduce bit requirements.
	 */
	private static void decodeBitlengthCodec(BitByteBuffer buffer, int startBit, int lengthBits,
	                                          int[] outValues, int valueCount) {
		Logger.debug("Decoding Bitlength CODEC: {} values, {} bits encoded data", valueCount, lengthBits);
		
		// Validate valueCount is reasonable
		if (valueCount <= 0) {
			Logger.error("Bitlength CODEC: Invalid valueCount {}", valueCount);
			return;
		}
		
		if (valueCount > 1000000) {
			Logger.error("Bitlength CODEC: Unrealistic valueCount {} (probably wrong offset)", valueCount);
			return;
		}
		
		try {
			BitReader reader = new BitReader(buffer, startBit);
			
			// Read format tag (bit 0) - determines fixed vs variable width
			int formatTag = reader.readBit();
			
			if (formatTag == 0) {
				// ===== FIXED-WIDTH FORMAT =====
				// Used when the value range is small enough that fixed encoding is more efficient
				// Min/max are encoded using nibbler encoding (4-bit groups with continuation bits)
				int minSymbol = readNibblerValue(reader);
				int maxSymbol = readNibblerValue(reader);
				
				// Calculate bits needed to represent range
				int valSpanBits = bitsize(maxSymbol - minSymbol);
				
				// Read all values as fixed-width offsets from minSymbol
				for (int i = 0; i < valueCount; i++) {
					int value = reader.readBits(valSpanBits);
					outValues[i] = value + minSymbol;
				}
			} else {
				// ===== VARIABLE-WIDTH FORMAT =====
				// Used when values have varying magnitude, reducing total bits needed
				// Mean value is encoded using nibbler encoding
				int meanValue = readNibblerValue(reader);
				
				// Decode variable-width encoded values
				// Field width (bits per value) adapts as values are decoded
				int currentFieldWidth = 0;
				final int cBlkValBits = 4;   // Bits per field-width delta (from C++ reference)
				final int maxFieldIncr = (1 << (cBlkValBits - 1)) - 1;  // Max positive delta
				final int maxFieldDecr = -(1 << (cBlkValBits - 1));     // Max negative delta
				
				for (int i = 0; i < valueCount; i++) {
					// Read field width adjustment using signed 4-bit value
					int widthDelta = reader.readSignedBits(cBlkValBits);
					currentFieldWidth += widthDelta;
					
					// Validate field width is non-negative
					if (currentFieldWidth < 0) {
						Logger.warn("Bitlength CODEC: Invalid negative field width: {} at index {}", currentFieldWidth, i);
						currentFieldWidth = 0;
					}
					
					if (currentFieldWidth > 32) {
						Logger.warn("Bitlength CODEC: Field width too large: {} at index {}", currentFieldWidth, i);
						currentFieldWidth = 32;
					}
					
					// Read the value with current field width
					int encodedValue = reader.readSignedBits(currentFieldWidth);
					outValues[i] = encodedValue + meanValue;
				}
			}
		} catch (Exception e) {
			Logger.error("Bitlength CODEC decoding failed: {}", e.getMessage());
			Logger.error("  valueCount: {}, lengthBits: {}, startBit: {}", valueCount, lengthBits, startBit);
		}
	}
	
	/**
	 * Decodes Arithmetic CODEC (codec type 3) - Reference: Arithmetic.cpp (lines 155-230)
	 * 
	 * Arithmetic coding encodes a stream of values as a single fractional number.
	 * Decoding is the inverse process: extract values sequentially from the bit stream.
	 * 
	 * NOTE: Full arithmetic decoding requires a probability context that defines
	 * symbol probabilities. This simplified version uses uniform probabilities.
	 * For production use, implement proper probability context from C++ reference.
	 */
	private static void decodeArithmeticCodec(BitByteBuffer buffer, int startBit, int lengthBits,
	                                           int[] outValues, int valueCount) {
		Logger.debug("Decoding Arithmetic CODEC: {} values, {} bits encoded data", valueCount, lengthBits);
		
		// Initialize arithmetic decoder state (from C++ ArithmeticCodec::decode)
		ArithmeticBitReader reader = new ArithmeticBitReader(buffer, startBit, lengthBits);
		
		// Decode values using uniform probability distribution (simplified)
		// Full implementation would use probability context from JT10 file format
		for (int i = 0; i < valueCount; i++) {
			// This is a placeholder - actual implementation needs probability context
			// For now, read raw bits (incorrect but structure is in place)
			if (i < valueCount) {
				outValues[i] = reader.decodeSymbol();
			}
		}
	}
	
	/**
	 * Reads a nibbler-encoded value (4-bit groups with continuation bit).
	 * From C++ reference Bitlength.cpp: nibblerEmit/nibbler decoding
	 * Format: [4-bit nibble][1-bit continue][4-bit nibble][1-bit continue]...
	 */
	private static int readNibblerValue(BitReader reader) {
		int result = 0;
		int shift = 0;
		while (true) {
			int nibble = reader.readBits(4);
			result |= (nibble << shift);
			int contBit = reader.readBits(1);  // 1 = more nibbles, 0 = end
			if (contBit == 0) break;
			shift += 4;
		}
		return result;
	}
	
	/**
	 * Helper class for bit-level reading with proper bit offset tracking.
	 * Used by Bitlength decoder to read individual bits and multi-bit values.
	 */
	private static class BitReader {
		private BitByteBuffer buffer;
		private int bitPosition;
		
		BitReader(BitByteBuffer buffer, int startBit) {
			this.buffer = buffer;
			this.bitPosition = startBit;
		}
		
		/**
		 * Reads up to 32 unsigned bits
		 */
		int readBits(int count) {
			if (count == 0) return 0;
			if (count > 32) throw new IllegalArgumentException("Cannot read more than 32 bits");
			
			// Read from buffer and mask off the required bits
			int value = buffer.getIntAtBitPosition(bitPosition);
			// Shift right to get the high bits and mask off excess
			value = (value >>> (32 - count)) & ((1 << count) - 1);
			bitPosition += count;
			return value;
		}
		
		/**
		 * Reads up to 32 signed bits with sign extension
		 */
		int readSignedBits(int count) {
			if (count == 0) return 0;
			int value = readBits(count);
			// Sign-extend if necessary
			if ((value & (1 << (count - 1))) != 0) {
				value |= (-1 << count);
			}
			return value;
		}
		
		/**
		 * Reads single bit (0 or 1)
		 */
		int readBit() {
			int bytePos = bitPosition / 8;
			int bitPos = bitPosition % 8;
			byte b = buffer.get(bytePos);
			int bit = (b >> (7 - bitPos)) & 1;
			bitPosition++;
			return bit;
		}
	}
	
	/**
	 * Helper class for Arithmetic codec decoding.
	 * Reference: Arithmetic.cpp ArithmeticCodec class
	 */
	private static class ArithmeticBitReader {
		private BitReader bitReader;
		private int code;
		private int low;
		private int high;
		private int scale;  // For probability scaling
		
		ArithmeticBitReader(BitByteBuffer buffer, int startBit, int lengthBits) {
			this.bitReader = new BitReader(buffer, startBit);
			
			// Initialize decoder state (from C++ reference line ~200)
			this.low = 0x0000;
			this.high = 0xffff;
			this.scale = this.high - this.low + 1;
			
			// Prime code register with first 16 bits
			this.code = bitReader.readBits(16);
		}
		
		/**
		 * Decode a single symbol using simplified uniform probability
		 * Full implementation would require probability context
		 */
		int decodeSymbol() {
			// Placeholder: return a reasonable default
			// In full implementation, this would use probability context
			// to scale the code and find the symbol range
			int symbolRange = 256;  // Assume small symbol range
			int value = ((code - low) * symbolRange) / scale;
			if (value >= symbolRange) value = symbolRange - 1;
			
			// Update decoder state (simplified)
			code = bitReader.readBits(8);  // Read next symbol bits
			
			return value & 0xFF;
		}
	}
//						codeTextLength = buffer.getInt(startIndex + 5 + i);
//						try {
			////				    		codeText = VecU32.fromByteBuffer(buffer, startIndex + 9 + i, codeTextLength);
//							System.out.println("Uncompressed length should be: " + valueCount + " compressedLength is: " + codeTextLength + " bits");
//							int bytesToRead = (codeTextLength + 7) / 8;
//							System.out.println("bytes to read: " + bytesToRead);
//							
//							int encodedCodeTextStartIndex = startIndex + 9 + i;
//							byte[] encodedCodeText = new byte[bytesToRead];
//
//							int windowSize = 0;
//							int[] decodedCodeText = new int[(int) valueCount];
//
			////							buffer.get(encodedCodeTextStartIndex, encodedCodeText);
//							
//							// Assume these constants come from somewhere:
//							final int cBlkValBits = 2/* e.g. 4 */;   // bits used to encode delta field width increments
//							final int cBlkLenBits = 2/* e.g. 4 */;   // bits used to encode run length
//
//							// Setup
//							int[] decodedValues = new int[(int) valueCount];
//							BitReader bitReader = new BitReader(buffer, encodedCodeTextStartIndex);
//							int iMean = bitReader.readSignedBits(cBlkValBits); // read mean value, using cBlkValBits bits
//
//							int cMaxFieldDecr = -(1 << (cBlkValBits - 1));
//							int cMaxFieldIncr = (1 << (cBlkValBits - 1)) - 1;
//
//							int cCurFieldWidth = 0;
//							int index = 0;
//
//							while (index < valueCount) {
//							    int cDeltaFieldWidth;
//							    // Adjust current field width
//							    do {
//							        cDeltaFieldWidth = bitReader.readSignedBits(cBlkValBits);
//							        cCurFieldWidth += cDeltaFieldWidth;
//							    } while (cDeltaFieldWidth == cMaxFieldDecr || cDeltaFieldWidth == cMaxFieldIncr);
//
//							    // Read run length for this field width
//							    int cRunLen = bitReader.readBits(cBlkLenBits);
//
//							    // Read values for the run
//							    for (int k = 0; k < cRunLen && index < valueCount; k++, index++) {
//							        int value = bitReader.readSignedBits(cCurFieldWidth);
//							        decodedValues[index] = value + iMean;
//							    }
//							}

//							BitSet bitSet = BitSet.valueOf(encodedCodeText);
//							BitReader bitReader = new BitReader(buffer, encodedCodeTextStartIndex);
//							for (int decodedValueIndex = 0; decodedValueIndex < valueCount; decodedValueIndex++) {
//								int initialWindowSizeBit = bitReader.readBit();
//								if (initialWindowSizeBit == 1) {
//									int firstWindowResizeBit = bitReader.readBit();
//									int currentBit = bitReader.readBit();
//									
//									int windowSizeModifier = firstWindowResizeBit == 1 ? 2 : 0;
//									while (firstWindowResizeBit == currentBit) {
//										if (firstWindowResizeBit == 1) {
//											windowSizeModifier += 2;
//										} else {
//											windowSizeModifier -= 2;
//										}
//										currentBit = bitReader.readBit();
//									}
//									windowSize += windowSizeModifier;
//								}
//								decodedCodeText[decodedValueIndex] = bitReader.readBits(windowSize);
//								// getWindowSize(); // getWindowSize has to return the new windowSize and somehow increment j by the amount of bits it took to find out
//							}
//							
//							Arrays.stream(decodedCodeText).forEach(decodedSymbol -> System.out.println(decodedSymbol));
//							

	public static TopologicallyCompressedRepDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {

		// According to documentation figure, there are 8 Face Degrees arrays
		// (one for each context group/face group)
		// Structure per documentation Figure 92:
		// 1. VecI32 Face Degrees[8] (8 arrays - one per context group)
		// 2. VecI32 Vertex Valences
		// 3. VecI32 Vertex Groups
		// 4. VecI32 Vertex Flags
		// 5. VecI32 Face Attribute Masks[8] (8 arrays - one per context group)
		// ...
		
		// startIndex is in BYTES (consistent with rest of codebase)
		// readInt32CDP expects BYTES and handles bit conversion internally
		Logger.info("===== TopologicallyCompressedRepDataRecord.fromByteBuffer =====");
		Logger.info("Called with startIndex={} bytes", startIndex);
		Logger.info("Buffer capacity: {} bytes", buffer.capacity());
		Logger.info("Attempting to read first Face Degrees array at offset {}", startIndex);
		
		// DIAGNOSTIC: Dump bytes around the given offset
		Logger.info("Bytes at offset {} (diagnostic):", startIndex);
		for (int i = 0; i < 40 && startIndex + i < buffer.capacity(); i += 10) {
			Logger.info("  +{}: 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X} 0x{:02X}",
			           i,
			           buffer.get(startIndex + i) & 0xFF,
			           buffer.get(startIndex + i + 1) & 0xFF,
			           buffer.get(startIndex + i + 2) & 0xFF,
			           buffer.get(startIndex + i + 3) & 0xFF,
			           buffer.get(startIndex + i + 4) & 0xFF,
			           buffer.get(startIndex + i + 5) & 0xFF,
			           buffer.get(startIndex + i + 6) & 0xFF,
			           buffer.get(startIndex + i + 7) & 0xFF,
			           buffer.get(startIndex + i + 8) & 0xFF,
			           buffer.get(startIndex + i + 9) & 0xFF);
		}
		
		VecI32[] faceDegrees = new VecI32[8];
		int byteOffset = startIndex;
		
		// Read 8 Face Degrees arrays (readInt32CDP: byte offset in → byte offset out)
		try {
			faceDegrees[0] = readInt32CDP(buffer, byteOffset);
			for (int i = 1; i < 8; i++) {
				// Get next byte offset from previous CDP end
				byteOffset = faceDegrees[i - 1].jtEndIndex();
				faceDegrees[i] = readInt32CDP(buffer, byteOffset);
			}		
			if (Arrays.stream(faceDegrees).filter(fd -> fd.count() == 0).count() > 0) {
				Logger.warn("Face Degrees array 0 is empty at byte offset {}", byteOffset);
			} else {
				Logger.info("Successfully read Face Degrees array 0 with {} values at byte offset {}", 
				             faceDegrees[0].count(), byteOffset);
				System.out.println("Successfully read 8 Face Degrees arrays:");
			}
		} catch (Exception e) {
			Logger.error("Failed to read Face Degrees arrays with byte offset: {}", byteOffset);
			Logger.error("Exception: {}", e.getMessage());
		}
		
		// Continue with CDP-encoded arrays (all byte offsets)
		VecI32 vertexValences = readInt32CDP(buffer, faceDegrees[7].jtEndIndex());
		VecI32 vertexGroups = readInt32CDP(buffer, vertexValences.jtEndIndex());
		Logger.info(vertexGroups.toString());
		Logger.info(vertexValences.toString());
		
		// Remaining arrays: VecI32.fromByteBuffer takes BYTE OFFSET
		// Get byte offset from jtEndIndex() which now returns bytes
		VecI32 vertexFlags = VecI32.fromByteBuffer(buffer, vertexGroups.jtEndIndex());
		
		// Read 8 Face Attribute Masks arrays (byte offsets)
		VecI32[] faceAttributeMasks = new VecI32[8];
		byteOffset = vertexFlags.jtEndIndex();
		for (int i = 0; i < 8; i++) {
			faceAttributeMasks[i] = VecI32.fromByteBuffer(buffer, byteOffset);
			byteOffset = faceAttributeMasks[i].jtEndIndex();
		}
		
		VecI32 faceAttributeMask8 = VecI32.fromByteBuffer(buffer, byteOffset);
		VecU32 highDegreeFaceAttributeMasks = VecU32.fromByteBuffer(buffer, faceAttributeMask8.jtEndIndex());
		VecI32 splitFaceSyms = VecI32.fromByteBuffer(buffer, highDegreeFaceAttributeMasks.jtEndIndex());
		VecI32 splitFacePositions = VecI32.fromByteBuffer(buffer, splitFaceSyms.jtEndIndex());
		
		// Return record with proper jtEndIndex
		return new TopologicallyCompressedRepDataRecord(faceDegrees, vertexValences, vertexGroups, vertexFlags, faceAttributeMasks, faceAttributeMask8, highDegreeFaceAttributeMasks, splitFaceSyms, splitFacePositions, 0, null);
	}

	@Override
	public int jtEndIndex() {
		return topologicallyCompressedVertexRecords.jtEndIndex();
	}

	public static void dumpBytes(ByteBuffer buffer, int startIndex, int numBytes) {
		System.out.printf("Dumping %d bytes from offset %d (0x%X):\n", numBytes, startIndex, startIndex);

		int relative = 0;
		int end = startIndex + numBytes;

		while (startIndex + relative < end) {
			int offset = startIndex + relative;

			if (relative == 0 && numBytes >= 5) {
				// Possibly VecI32 header with Int32 + U8
				int count = buffer.getInt(offset);
				int codec = buffer.get(offset + 4) & 0xFF;

				String codecName = switch (codec) {
				case 0 -> "Raw";
				case 1 -> "Delta";
				case 3 -> "Int32CDP";
				case 4 -> "Lag1";
				case 5 -> "Lag1CDP";
				default -> "Unknown";
				};

				System.out.printf("  [0x%04X] count (I32): %d\n", offset, count);
				System.out.printf("  [0x%04X] codec (U8):  %d (%s)\n", offset + 4, codec, codecName);

				relative += 5;
				continue;
			}

			// Otherwise: just show raw byte
			int b = buffer.get(offset) & 0xFF;
			System.out.printf("  [0x%04X] byte: 0x%02X\n", offset, b);
			relative++;
		}

		System.out.println();
	}
	
	/**
	 * Reads a nibbler-encoded signed integer from the buffer, following the C++ implementation.
	 * Format: Repeating pattern of 4-bit nibble + 1-bit continuation flag
	 * Returns both the value and updates the buffer's bit offset.
	 */
	public static class NibblerReadResult {
		public int value;
		public int endBitPosition;
		
		public NibblerReadResult(int value, int endBitPosition) {
			this.value = value;
			this.endBitPosition = endBitPosition;
		}
	}
	
	public static NibblerReadResult nibblerGetSignedInt(BitByteBuffer buffer, int startPosition) {
		int value = 0;
	    int cNibbles = 0;
	    int currentPosition = startPosition;

	    int bMoreBits;
	    do {
	        // --- Read one nibble (4 bits) ---
	        int uTmp = (int) buffer.readBitsAt(currentPosition, 4);
	        currentPosition += 4;

	        uTmp <<= cNibbles * 4;  // shift into the correct position
	        value |= uTmp;

	        // --- Read continuation flag (1 bit) ---
	        bMoreBits = (int) buffer.readBitsAt(currentPosition, 1);
	        currentPosition += 1;

	        cNibbles++;
	    } while (bMoreBits != 0);

	    // --- Sign extension ---
	    int sw = cNibbles * 4;  // number of bits that make up the actual value
	    if (sw < 32) {
	        value <<= (32 - sw);
	        value >>= (32 - sw); // arithmetic right shift → sign extension
	    }
	    
	    return new NibblerReadResult(value, currentPosition);
	}
	
	/**
	 * Symbol entry from probability context.
	 * Represents one symbol with its probability information.
	 */
	public static class SymbolEntry {
		public int value;
		public int cCumCount;    // Cumulative count (starting position in range)
		public int cCount;       // Count for this symbol (width of range)
		
		public SymbolEntry(int value, int cCumCount, int cCount) {
			this.value = value;
			this.cCumCount = cCumCount;
			this.cCount = cCount;
		}
	}
	
	/**
	 * Arithmetic Codec State for decoding range-encoded data.
	 * Maintains the decoder state across multiple symbol decodes.
	 * Implements the decoding algorithm from Arithmetic.cpp.
	 */
	public static class ArithmeticDecoderState {
		// Range boundaries (16-bit unsigned)
		public int low = 0;
		public int high = 0xFFFF;
		public int code = 0;
		
		// Bit buffer management
		public long uBitBuff = 0;  // Bit buffer (32-bit)
		public int nBitBuff = 0;   // Number of bits currently in buffer
		
		// Current position tracking
		public int currentBitPosition = 0;  // Tracks bit position for outside management
		
		/**
		 * Read next 32 bits from buffer at given position.
		 * Updates currentBitPosition.
		 */
		public void getNextCodeText(BitByteBuffer buffer, int startBit, int maxBits) {
			if (currentBitPosition >= maxBits) {
				return;  // No more bits to read
			}
			
			int bitsToRead = Math.min(32, maxBits - currentBitPosition);
			uBitBuff = buffer.readBitsAt(startBit + currentBitPosition, bitsToRead);
			nBitBuff = bitsToRead;
			currentBitPosition += bitsToRead;
		}
		
		/**
		 * Read one bit from buffer, filling as needed.
		 * Equivalent to ReadBit0 macro - ORs bit into low bit of the input/output value.
		 */
		public void readBit0(BitByteBuffer buffer, int startBit, int maxBits) {
			if (nBitBuff == 0) {
				getNextCodeText(buffer, startBit, maxBits);
			}
			code = (code << 1) | (int)((uBitBuff >> 31) & 0x1L);
			uBitBuff <<= 1;
			nBitBuff--;
		}
	}
	
	/**
	 * Remove a symbol from the arithmetic stream after decoding.
	 * Adjusts the range [low, high] and shifts bits as needed.
	 * Implements _removeSymbolFromStream from Arithmetic.cpp.
	 */
	private static void removeSymbolFromStream(ArithmeticDecoderState state, int uLowCt, int uHighCt, 
	                                            int uScale, BitByteBuffer buffer, int startBit, int maxBits) {
		// First, expand the range to account for symbol removal
		long uRange = (long)(state.high - state.low) + 1;
		state.high = state.low + (int)(((uRange * uHighCt) / uScale) - 1);
		state.low = state.low + (int)((uRange * uLowCt) / uScale);
		
		// Next, shift out any bits that have stabilized
		for (;;) {
			// If MSB matches, we can shift out bits
			if ((((state.high ^ state.low) & 0x8000) == 0)) {
				// MSBs match - shift left and continue
				state.low <<= 1;
				state.high <<= 1;
				state.high |= 1;
				state.code <<= 1;
				state.readBit0(buffer, startBit, maxBits);
			}
			// Underflow condition: high = 10xx, low = 01xx
			else if ((((state.low >> 14) & 0x3) == 1) && (((state.high >> 14) & 0x3) == 2)) {
				// Handle underflow
				state.code ^= 0x4000;
				state.low &= 0x3FFF;
				state.high |= 0x4000;
				
				// Continue shifting
				state.low <<= 1;
				state.high <<= 1;
				state.high |= 1;
				state.code <<= 1;
				state.readBit0(buffer, startBit, maxBits);
			}
			else {
				// Nothing more can be shifted, so return
				break;
			}
		}
	}
	
	/**
	 * Flush the decoder - read final bits from the stream.
	 * Implements _flushDecoder from Arithmetic.cpp.
	 */
	private static void flushDecoder(ArithmeticDecoderState state, BitByteBuffer buffer, int startBit, int maxBits) {
		// Read two dummy bits to finalize the stream
		int dummy = 0;
		if (state.nBitBuff > 0) {
			dummy = (int)((state.uBitBuff >> 31) & 0x1);
			state.uBitBuff <<= 1;
			state.nBitBuff--;
		}
		if (state.nBitBuff == 0 && state.currentBitPosition < maxBits) {
			state.getNextCodeText(buffer, startBit, maxBits);
		}
		if (state.nBitBuff > 0) {
			dummy = (int)((state.uBitBuff >> 31) & 0x1);
			state.uBitBuff <<= 1;
			state.nBitBuff--;
		}
	}
	
	/**
	 * Decodes arithmetic-encoded data following the algorithm in Arithmetic.cpp.
	 * 
	 * IMPORTANT: This requires a probability context that maps symbols to cumulative counts.
	 * The current implementation uses a simplified uniform context for testing.
	 * A proper implementation would load the context from the file format or derive it adaptively.
	 */
	public static int decodeArithmetic(BitByteBuffer buffer, int startIndex, int codeTextBitLength, 
	                                     int valueCount, int[] decodedSymbols) {
		
		// Create decoder state
		ArithmeticDecoderState state = new ArithmeticDecoderState();
		state.currentBitPosition = 0;
		
		// Initialize: get first batch of bits
		state.getNextCodeText(buffer, startIndex, codeTextBitLength);
		
		// Read initial 16-bit code value
		state.code = 0;
		for (int i = 0; i < 16 && state.nBitBuff > 0; i++) {
			state.code = (state.code << 1) | (int)((state.uBitBuff >> 31) & 0x1L);
			state.uBitBuff <<= 1;
			state.nBitBuff--;
		}
		
		// Initialize range
		state.low = 0;
		state.high = 0xFFFF;
		
		// For a complete implementation, we need the probability context.
		// Without it, we cannot properly decode the symbols.
		// The reference encode() function uses pProbCntx->lookupValue() and
		// the decode() function uses pProbCntx->lookupEntryByCumCount().
		
		// Create a simple test context for demonstration purposes.
		// In reality, this should be loaded from the file or built adaptively.
		int totalSymbols = Math.max(1, valueCount);  // Placeholder
		int cTotalCount = totalSymbols;  // Uniform distribution
		
		// Decode each symbol
		for (int i = 0; i < valueCount; i++) {
			// Calculate rescaled code for probability lookup
			// rescaledCode = (((code - low) + 1) * totalCount - 1) / (high - low + 1)
			long numerator = (long)(state.code - state.low + 1) * (long)cTotalCount - 1;
			long denominator = (long)(state.high - state.low) + 1;
			int rescaledCode = (int)(numerator / denominator);
			
			// For now, use a simplified mapping: assume uniform context
			// Each symbol gets an equal-sized range
			int symbolIndex = (rescaledCode * valueCount) / cTotalCount;
			symbolIndex = Math.min(symbolIndex, valueCount - 1);
			
			// Without a proper probability context, we can't decode correctly.
			// Set a placeholder value
			decodedSymbols[i] = symbolIndex;
			
			// For the complete algorithm, we'd look up the symbol in the context:
			// SymbolEntry entry = pProbCntx->lookupEntryByCumCount(rescaledCode);
			// decodedSymbols[i] = entry.value;
			// removeSymbolFromStream(state, entry.cCumCount, entry.cCumCount + entry.cCount, cTotalCount, ...);
			
			// For now, just use a trivial range removal
			int symbolCumCount = (symbolIndex * cTotalCount) / valueCount;
			int symbolCount = ((symbolIndex + 1) * cTotalCount) / valueCount - symbolCumCount;
			removeSymbolFromStream(state, symbolCumCount, symbolCumCount + symbolCount, cTotalCount, 
			                       buffer, startIndex, codeTextBitLength);
		}
		
		// Flush the decoder
		flushDecoder(state, buffer, startIndex, codeTextBitLength);
		
		// Return the bit position (aligned to byte boundary)
		return startIndex + ((state.currentBitPosition + 7) / 8) * 8;
	}
	
	public static int decodeBitlength(BitByteBuffer byteBuffer, int startIndex, int valueCount, int[] decodedSymbols) {
		int currentBitPosition = startIndex;
		
		// Step 1: Tag bit
		int tagBit = (int) byteBuffer.readBitsAt(currentBitPosition, 1);
		currentBitPosition += 1;

		if (tagBit == 0) {
			// -------------------------------------------------
			// Fixed-width coding
			// -------------------------------------------------

			// Read min/max using nibbler encoding
			NibblerReadResult minResult = nibblerGetSignedInt(byteBuffer, currentBitPosition);
			int min = minResult.value;
			currentBitPosition = minResult.endBitPosition;
			
			NibblerReadResult maxResult = nibblerGetSignedInt(byteBuffer, currentBitPosition);
			int max = maxResult.value;
			currentBitPosition = maxResult.endBitPosition;

			int fieldWidth = bitsize(max - min);

			// Decode all values with fixed field width
			for (int i = 0; i < valueCount; i++) {
				int symbol = 0;
				if (fieldWidth != 0) {
					// Read as unsigned then sign-extend
					symbol = (int) byteBuffer.readBitsAt(currentBitPosition, fieldWidth);
					symbol <<= (32 - fieldWidth);
					symbol >>= (32 - fieldWidth); // arithmetic right shift for sign extension
					currentBitPosition += fieldWidth;
				}
				decodedSymbols[i] = symbol + min;
			}

		} else {
			// -------------------------------------------------
			// Variable-width coding
			// -------------------------------------------------

			// Read mean using nibbler encoding
			NibblerReadResult meanResult = nibblerGetSignedInt(byteBuffer, currentBitPosition);
			int mean = meanResult.value;
			currentBitPosition = meanResult.endBitPosition;
			
			// Constants from spec
			final int cBlkValBits = 3; // signed delta width
			final int cBlkLenBits = 4; // run length width

			int cMaxFieldDecr = -(1 << (cBlkValBits - 1)); // -4
			int cMaxFieldIncr = (1 << (cBlkValBits - 1)) - 1; // +3

			int cCurFieldWidth = 0;
			int ii = 0;

			while (ii < valueCount) {
				// Step 1: adjust field width until delta is not extreme
				int cDeltaFieldWidth;
				do {
					// Read as signed 3-bit value
					long deltaRaw = byteBuffer.readBitsAt(currentBitPosition, cBlkValBits);
					// Sign extend from 3 bits to 32 bits
					if ((deltaRaw & (1 << (cBlkValBits - 1))) != 0) {
						// Negative: fill upper bits with 1s
						cDeltaFieldWidth = (int) (deltaRaw | (0xFFFFFFFF << cBlkValBits));
					} else {
						// Positive: upper bits stay 0
						cDeltaFieldWidth = (int) deltaRaw;
					}
					currentBitPosition += cBlkValBits;
					cCurFieldWidth += cDeltaFieldWidth;
				} while (cDeltaFieldWidth == cMaxFieldDecr || cDeltaFieldWidth == cMaxFieldIncr);

				// Step 2: run length (unsigned, cBlkLenBits bits)
				int cRunLen = (int) byteBuffer.readBitsAt(currentBitPosition, cBlkLenBits);
				currentBitPosition += cBlkLenBits;
				
				// Step 3: decode run values
				for (int k = ii; k < ii + cRunLen && k < valueCount; k++) {
					int symbol = 0;
					if (cCurFieldWidth != 0) {
						// Read as signed value
						long symbolRaw = byteBuffer.readBitsAt(currentBitPosition, cCurFieldWidth);
						// Sign extend from cCurFieldWidth bits to 32 bits
						if ((symbolRaw & (1L << (cCurFieldWidth - 1))) != 0) {
							// Negative: fill upper bits with 1s
							symbol = (int) (symbolRaw | (0xFFFFFFFF << cCurFieldWidth));
						} else {
							// Positive: upper bits stay 0
							symbol = (int) symbolRaw;
						}
						currentBitPosition += cCurFieldWidth;
					}
					decodedSymbols[k] = symbol + mean;
				}

				ii += cRunLen;
			}
		}
		
		return currentBitPosition;
	}

}
