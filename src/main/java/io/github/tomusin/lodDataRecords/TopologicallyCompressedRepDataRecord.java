package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.viewer.JTGeometryViewer;

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
	private static int findValidCDPOffset(BitByteBuffer buffer, int startIndex, int maxScanBytes, boolean onlyForward) {
		Logger.debug("Scanning for valid CDP header starting at byte offset {}", startIndex);
		
		int scanStart = Math.max(0, startIndex - maxScanBytes);
		if (onlyForward) {
			scanStart = startIndex;  // Only scan forward, not backward
		}
		int scanEnd = Math.min(buffer.capacity() - 9, startIndex + maxScanBytes);
		
		Logger.debug("  Scan range: {} to {} ({} bytes)", scanStart, scanEnd, scanEnd - scanStart);
		
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
				Logger.debug("  Found valid CDP at offset {}: valueCount={}, codecType={}, codeTextLength={}", 
				           offset, valueCount, codecType, codeTextLengthBits);
				return offset;
			}
		}
		
		Logger.warn("  No valid CDP header found in range");
		return startIndex;  // Fall back to original offset
	}

	/**
	 * Finds the next plausible CDP header at or after {@code startIndex}.
	 * Used to re-sync offsets after arithmetic packets that may carry additional
	 * serialized structures after code-text words.
	 */
	private static int findNextLikelyCDPOffset(BitByteBuffer buffer, int startIndex, int maxScanBytes) {
		int scanStart = Math.max(0, startIndex);
		int scanEnd = Math.min(buffer.capacity() - 9, startIndex + maxScanBytes);

		for (int offset = scanStart; offset <= scanEnd; offset++) {
			int valueCount = buffer.getInt(offset);
			int codecType = buffer.get(offset + 4) & 0xFF;
			if (codecType < 0 || codecType > 5) {
				continue;
			}

			if (valueCount < 0 || valueCount > 1_000_000) {
				continue;
			}

			if (codecType == 4) {
				// Chopper has at least 6-byte header, 11 when chopBits != 0.
				if (offset + 6 <= buffer.capacity()) {
					int chopBits = buffer.get(offset + 5) & 0xFF;
					if (chopBits == 0 || offset + 11 <= buffer.capacity()) {
						return offset;
					}
				}
				continue;
			}

			int codeTextLengthBits = buffer.getInt(offset + 5);
			if (codeTextLengthBits < 0 || codeTextLengthBits > buffer.capacity() * 8) {
				continue;
			}

			int codeTextWordBytes = ((codeTextLengthBits + 31) / 32) * 4;
			int end = offset + 9 + codeTextWordBytes;
			if (end <= buffer.capacity()) {
				return offset;
			}
		}

		return startIndex;
	}

	static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex) {
		return readInt32CDP(buffer, startIndex, 0, false, PredictorType.PredLag1);
	}
	
	static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex, PredictorType predictorType) {
		return readInt32CDP(buffer, startIndex, 0, false, predictorType);
	}
	
	private static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex, int recursionDepth, boolean isOobCall) {
		return readInt32CDP(buffer, startIndex, recursionDepth, isOobCall, PredictorType.PredLag1);
	}
	
	private static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex, int recursionDepth, boolean isOobCall, PredictorType predictorType) {
		// Prevent infinite recursion from offset scanning
		if (recursionDepth > 8) {
			Logger.error("readInt32CDP: Max recursion depth (8) exceeded at offset {}", startIndex);
			return new VecI32(0, new int[0], startIndex);
		}
		// ========== CDP FORMAT (Byte-aligned) ==========
		// Different codecs have DIFFERENT header structures!
		// 
		// Standard Format (Types 0, 1, 3):
		//   Bytes 0-3:   valueCount (I32)
		//   Byte 4:      codecType (U8)
		//   Bytes 5-8:   codeTextLength (I32)
		//   Bytes 9+:    Encoded data
		//
		// Chopper Format (Type 4):
		//   Bytes 0-3:   valueCount (I32)
		//   Byte 4:      codecType (U8) = 4
		//   Byte 5:      chopBits (U8)
		//   Bytes 6-9:   valueBias (I32)
		//   Byte 10:     valueSpanBits (U8)
		//   Bytes 11+:   Int32CDP (Chopped MSB Data)
		//   Then:        Int32CDP (Chopped LSB Data)
		
		Logger.debug("readInt32CDP called with startIndex={} bytes", startIndex);
		Logger.debug("  Buffer capacity: {} bytes", buffer.capacity());
		
		// Bounds check for minimum header (always at least 5 bytes)
		if (startIndex < 0 || startIndex + 5 > buffer.capacity()) {
			Logger.error("readInt32CDP: startIndex {} is out of bounds (buffer capacity {})", 
			            startIndex, buffer.capacity());
			return new VecI32(0, new int[0], startIndex);
		}
		
		// ========== BASIC HEADER PARSING (byte-aligned) ==========
		// Bytes 0-3: Count of decoded values (signed 32-bit)
		int valueCount = buffer.getInt(startIndex);
		// Byte 4: CODEC type (unsigned 8-bit)
		int codecType = buffer.get(startIndex + 4) & 0xFF;

		if (valueCount == 0 && codecType == 4) {
			Logger.debug("  Empty Chopper CDP header at byte {}", startIndex);

			// Chopper can legally appear with zero values and still carry chopper metadata.
			if (startIndex + 6 > buffer.capacity()) {
				return new VecI32(0, new int[0], startIndex + 5);
			}
			int chopBits = buffer.get(startIndex + 5) & 0xFF;
			if (chopBits == 0) {
				// C++ behavior: recurse immediately after chopBits when there is no chopped payload.
				return readInt32CDP(buffer, startIndex + 6, recursionDepth + 1, false);
			}
			if (startIndex + 11 > buffer.capacity()) {
				return new VecI32(0, new int[0], startIndex + 5);
			}
			// Parse nested MSB/LSB CDPs only to advance offset correctly.
			VecI32 msbData = readInt32CDP(buffer, startIndex + 11, recursionDepth + 1, false);
			VecI32 lsbData = readInt32CDP(buffer, msbData.jtEndIndex(), recursionDepth + 1, false);
			return new VecI32(0, new int[0], lsbData.jtEndIndex());
		}
		
		if (valueCount == 0 && codecType == 5) {
			Logger.debug("  Empty MtF CDP header at byte {}", startIndex);
			VecI32 msbData = readInt32CDP(buffer, startIndex + 5, recursionDepth + 1, false);
			VecI32 winData = readInt32CDP(buffer, msbData.jtEndIndex(), recursionDepth + 1, false);
			return new VecI32(0, new int[0], winData.jtEndIndex());
		}
		
		if (valueCount == 0) {
			Logger.debug("  Empty CDP at byte {}, advancing 4 bytes", startIndex);
			return new VecI32(0, new int[0], startIndex + 4);
		}
		
		// Bounds check for standard header (9 bytes minimum for non-Chopper/MtF)
		if (codecType != 4 && codecType != 5 && startIndex + 9 > buffer.capacity()) {
			Logger.error("readInt32CDP: startIndex {} needs 9 bytes but only {} available", 
			            startIndex, buffer.capacity() - startIndex);
			return new VecI32(0, new int[0], startIndex);
		}
		
		// Bounds check for Chopper header (11 bytes minimum)
		if (codecType == 4 && startIndex + 11 > buffer.capacity()) {
			Logger.error("readInt32CDP: Chopper codec at {} needs 11 bytes but only {} available", 
			            startIndex, buffer.capacity() - startIndex);
			return new VecI32(0, new int[0], startIndex);
		}
		
		// Codec-specific header parsing
		int codeTextLengthBits = 0;
		int endByte = startIndex + 9;  // Default for standard codecs
		
		if (codecType == 5) {
			// ========== MOVE-TO-FRONT CODEC ==========
			// MtF codec consists of two nested Int32CDPs (no codeTextLength field):
			//   1. Int32 Compressed Data Packet: Chopped MSB Data
			//   2. Int32 Compressed Data Packet: Window Offsets
			Logger.debug("  MtF codec at byte {}: valueCount={}", startIndex, valueCount);
			
			VecI32 choppedMsbData = readInt32CDP(buffer, startIndex + 5, recursionDepth + 1, false);
			Logger.debug("  MtF: Chopped MSB Data: count={}, endByte={}", choppedMsbData.count(), choppedMsbData.jtEndIndex());
			
			VecI32 windowOffsets = readInt32CDP(buffer, choppedMsbData.jtEndIndex(), recursionDepth + 1, false);
			Logger.debug("  MtF: Window Offsets: count={}, endByte={}", windowOffsets.count(), windowOffsets.jtEndIndex());
			
			// TODO: implement actual MtF decoding using choppedMsbData + windowOffsets
			int[] decodedData = new int[valueCount];
			
			return new VecI32(valueCount, decodedData, windowOffsets.jtEndIndex());
		} else if (codecType == 4) {
			// ========== CHOPPER CODEC HEADER ==========
			
			int chopBits = buffer.get(startIndex + 5) & 0xFF;
			int valueBias = buffer.getInt(startIndex + 6);
			int valueSpanBits = buffer.get(startIndex + 10) & 0xFF;
			
			// For Chopper with data, we need to read the two embedded Int32CDP blocks
			// First Int32CDP (Chopped MSB Data) starts at byte 11
			VecI32 msbData = readInt32CDP(buffer, startIndex + 11, recursionDepth + 1, false);
			int lsbStartByte = msbData.jtEndIndex();
			
			// Second Int32CDP (Chopped LSB Data) starts after MSB data
			VecI32 lsbData = readInt32CDP(buffer, lsbStartByte, recursionDepth + 1, false);
			int chopperEndByte = lsbData.jtEndIndex();
			
			// Merge the two data sets (MSB and LSB)
			// For now, just use MSB data as placeholder
			int[] mergedData = new int[valueCount];
			System.arraycopy(msbData.valueArray(), 0, mergedData, 0, Math.min(msbData.valueArray().length, valueCount));
			
			Logger.debug("  Chopper: MSB data ends at byte {}, LSB data ends at byte {}", lsbStartByte, chopperEndByte);
			
			return new VecI32(valueCount, mergedData, chopperEndByte);
		} else {
			// ========== STANDARD HEADER (Types 0, 1, 3) ==========
			// Bytes 5-8: Length of encoded data in bits (signed 32-bit)
			codeTextLengthBits = buffer.getInt(startIndex + 5);
			
			Logger.debug("CDP Header at byte offset {}: valueCount={}, codecType={}, codeTextLengthBits={}", 
			           startIndex, valueCount, codecType, codeTextLengthBits);
			
			int codeTextWordBytes = ((codeTextLengthBits + 31) / 32) * 4;
			endByte = startIndex + 9 + codeTextWordBytes;
		}
		
		// Check for unrealistic code text length (common indicator of wrong offset)
		if (codeTextLengthBits > buffer.capacity() * 8) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}: codeTextLengthBits {} exceeds buffer capacity {} bits", 
			            startIndex, codeTextLengthBits, buffer.capacity() * 8);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		// Check for NULL codec with invalid codeTextLength
		if (codecType == 0 && codeTextLengthBits != valueCount * 32) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}: NULL codec requires codeTextLengthBits={} but got {}", 
			            startIndex, valueCount * 32, codeTextLengthBits);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		// Check for unrealistic value counts (indicates wrong offset)
		if (valueCount < 0 || valueCount > 1000000) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}: invalid valueCount={}", startIndex, valueCount);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		// Check for invalid codec type
		if (codecType < 0 || codecType > 5) {
			Logger.error("CDP VALIDATION ERROR at byte offset {}: invalid codecType={}", startIndex, codecType);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		// Check for negative code text length
		if (codeTextLengthBits < 0) {
			Logger.warn("CDP WARNING at byte offset {}: negative codeTextLengthBits={}, treating as 0", startIndex, codeTextLengthBits);
			codeTextLengthBits = 0;
		}
			
		Logger.debug("CDP Header: valueCount={}, codec={}, codeTextLengthBits={} at byteOffset={} (depth={}, isOOB={})", 
		             valueCount, codecType, codeTextLengthBits, startIndex, recursionDepth, isOobCall);
			
		// ========== CODEC DISPATCH (Standard codecs) ==========
		int cdpHeaderBytes = 9;
		int encodedDataStartByte = startIndex + cdpHeaderBytes;
		int encodedDataStartBit = encodedDataStartByte * 8;
		int codeTextWordBytes = ((codeTextLengthBits + 31) / 32) * 4;
		endByte = encodedDataStartByte + codeTextWordBytes;
			
		int[] decodedValues = new int[(int) valueCount];
		boolean codecImplemented = true;
		
		// ========== CODEC DISPATCH (SWITCH) ==========
		switch (codecType) {
		case 0 -> decodeNullCodec(buffer, encodedDataStartBit, codeTextLengthBits, 
		                            decodedValues, (int) valueCount);
		case 1 -> decodeBitlengthCodec(buffer, encodedDataStartBit, codeTextLengthBits,
		                                decodedValues, (int) valueCount);
		case 3 -> {
			// ===== ARITHMETIC CODEC: Must read probability context FIRST =====
			// The probability context is stored AFTER the code text words.
			// We need it to decode, so read it first, then decode.
			if (!isOobCall) {
				int probCtxStartBit = endByte * 8;
				Logger.info("  ARITH: codeTextStartByte={}, codeTextWordBytes={}, probCtxStartByte={}, probCtxStartBit={}",
					encodedDataStartByte, codeTextWordBytes, endByte, probCtxStartBit);
				// Dump raw bytes at prob context start
				Logger.info("  ARITH: raw bytes at probCtx: {:02x} {:02x} {:02x} {:02x} {:02x} {:02x} {:02x} {:02x}",
					buffer.getUnsignedByte(endByte), buffer.getUnsignedByte(endByte+1),
					buffer.getUnsignedByte(endByte+2), buffer.getUnsignedByte(endByte+3),
					buffer.getUnsignedByte(endByte+4), buffer.getUnsignedByte(endByte+5),
					buffer.getUnsignedByte(endByte+6), buffer.getUnsignedByte(endByte+7));
				Int32ProbabilityContextRecord ctx = Int32ProbabilityContextRecord.fromBitBuffer(buffer, probCtxStartBit);
				int probCtxEndBit = ctx.jtEndBitIndex();
				int probCtxEndByte = (probCtxEndBit + 7) / 8;
				
				// Check for OOB CDP (present if escape symbol exists)
				boolean hasEscape = false;
				int[] oobValues = null;
				if (ctx.entries() != null) {
					for (var entry : ctx.entries()) {
						if (entry.isEscapeSymbol()) { hasEscape = true; break; }
					}
				}
				if (hasEscape) {
					VecI32 oobCdp = readInt32CDP(buffer, probCtxEndByte, recursionDepth + 1, true);
					oobValues = oobCdp.valueArray();
					endByte = oobCdp.jtEndIndex();
				} else {
					endByte = probCtxEndByte;
				}
				
				// NOW decode using the probability context
				decodeArithmeticCodecWithContext(buffer, encodedDataStartBit, codeTextLengthBits,
				                                 decodedValues, (int) valueCount, ctx, oobValues);
			} else {
				// OOB call: no probability context, use simplified decode
				decodeArithmeticCodec(buffer, encodedDataStartBit, codeTextLengthBits,
				                     decodedValues, (int) valueCount);
			}
		}
		case 2 -> {
			Logger.warn("CODEC 2 (Illegal) not implemented");
			codecImplemented = false;
		}
		case 4 -> {
			Logger.warn("CODEC 4 (Chopper) should have been handled in header parsing!");
			codecImplemented = false;
		}
		case 5 -> {
			// MtF is handled in the header section (early return) like Chopper.
			// This case should never be reached.
			Logger.error("CODEC 5 (MtF) should have been handled in header parsing!");
			codecImplemented = false;
		}
		default -> {
			Logger.error("ERROR: Unknown CODEC type: {}", codecType);
			codecImplemented = false;
		}
		}
			
		// ========== RESIDUAL UNPACKING ==========
		// OOB CDPs carry raw residuals that will be accumulated by the parent
		// sequence's unpackResiduals — do NOT accumulate them independently.
		if (codecImplemented && valueCount > 0 && !isOobCall) {
			// Log residuals before unpacking around drift point
			if (valueCount > 860) {
				Logger.info("  PRE-UNPACK residuals [850..865]:");
				for (int i = 850; i <= Math.min(865, valueCount - 1); i++) {
					Logger.info("    residual[{}] = {}", i, decodedValues[i]);
				}
			}
			unpackResiduals(decodedValues, predictorType);
			// Log accumulated values after unpacking around drift point
			if (valueCount > 860) {
				Logger.info("  POST-UNPACK values [0..5] and [850..870] and LAST 5:");
				for (int i = 0; i <= Math.min(5, valueCount - 1); i++) {
					Logger.info("    value[{}] = {} (float={})", i, decodedValues[i], Float.intBitsToFloat(decodedValues[i]));
				}
				for (int i = 850; i <= Math.min(870, valueCount - 1); i++) {
					Logger.info("    value[{}] = {} (float={})", i, decodedValues[i], Float.intBitsToFloat(decodedValues[i]));
				}
				for (int i = Math.max(871, valueCount - 5); i < valueCount; i++) {
					Logger.info("    value[{}] = {} (float={})", i, decodedValues[i], Float.intBitsToFloat(decodedValues[i]));
				}
			}
		}

		// ========== TAIL STRUCTURES (after codeTextWords) ==========
		// Per JT spec, after codeTextWords (only for top-level CDPs, not OOB):
		//   Codec 3 (Arithmetic): Already handled above (read before decoding)
		//   Codec 1 (Bitlength): No separate OOB CDP (escape values are inline)
		//   Codec 0 (Null): no tail
		// OOB CDPs are leaf nodes — they never recurse into further tails.

		if (!isOobCall && codecType == 3) {
			// Already handled above - skip
		}
			
		// Log the offset calculation for debugging
		Logger.trace("CDP Offset Calculation: startIndex={}, endByte={}", startIndex, endByte);
			
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
		
		// Null codec stores raw 32-bit signed integers as LE words in code text.
		// Use buffer.getInt() which reads LE (matching the ByteBuffer's byte order).
		int startByte = startBit / 8;
		for (int i = 0; i < valueCount; i++) {
			outValues[i] = buffer.getInt(startByte + i * 4);
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
			// Use CodeTextBitReader which reads bits from LE 32-bit words MSB-first,
			// matching the C++ GetUnsignedBits/GetSignedBits/getNextCodeText pattern.
			// startBit is always byte-aligned (encodedDataStartByte * 8).
			int startByte = startBit / 8;
			CodeTextBitReader reader = new CodeTextBitReader(buffer, startByte, lengthBits);
			
			// Read format tag (bit 0) - determines fixed vs variable width
			// C++ line 493: GetUnsignedBits(iTmp, 1)
			int formatTag = reader.readBit();
			Logger.info("Bitlength CODEC: formatTag={}, startByte={}", formatTag, startByte);
			
			if (formatTag == 0) {
				// ===== FIXED-WIDTH FORMAT =====
				// C++ decode lines 494-507: nibblerGet min/max, bitsize(max-min), read unsigned + add min
				int minSymbol = reader.nibblerGetSigned();
				int maxSymbol = reader.nibblerGetSigned();
				Logger.info("Bitlength FIXED: minSymbol={}, maxSymbol={}", minSymbol, maxSymbol);
				
				// bitsize of UNSIGNED range (C++ line 498: bitsize(UInt32(iMaxSymbol - iMinSymbol)))
				int valSpanBits = bitsize(maxSymbol - minSymbol);
				Logger.info("Bitlength FIXED: valSpanBits={}", valSpanBits);
				
				// Read all values as unsigned fixed-width offsets from minSymbol (C++ lines 500-506)
				for (int i = 0; i < valueCount; i++) {
					int value = reader.readBits(valSpanBits); // unsigned read
					outValues[i] = value + minSymbol;
				}
			} else {
				// ===== VARIABLE-WIDTH FORMAT (Block-based) =====
				// C++ decode lines 511-537
				// Structure: mean via nibbler, then blocks of [delta-width-loop][run-length][values...]
				int meanValue = reader.nibblerGetSigned();
				Logger.info("Bitlength VARIABLE: meanValue={} (0x{})", meanValue, Integer.toHexString(meanValue));
				
				final int cBlkValBits = 4;   // Bits per field-width delta (C++ line 225)
				final int cBlkLenBits = 4;   // Bits per run length (C++ line 223)
				final int maxFieldIncr = (1 << (cBlkValBits - 1)) - 1;  // +7
				final int maxFieldDecr = -(1 << (cBlkValBits - 1));     // -8
				
				int cCurFieldWidth = 0;
				int ii = 0;
				int blockNum = 0;
				
				while (ii < valueCount) {
					// Step 1: Adjust field width (loop while delta is at extremes)
					// C++ lines 522-527
					int cDeltaFieldWidth;
					int deltaSum = 0;
					int deltaCount = 0;
					do {
						cDeltaFieldWidth = reader.readSignedBits(cBlkValBits);
						cCurFieldWidth += cDeltaFieldWidth;
						deltaSum += cDeltaFieldWidth;
						deltaCount++;
					} while (cDeltaFieldWidth == maxFieldDecr || cDeltaFieldWidth == maxFieldIncr);
					
					// Step 2: Read run length (unsigned, cBlkLenBits bits)
					// C++ line 529
					int cRunLen = reader.readBits(cBlkLenBits);
					
					// Step 3: Read values for the run (signed, cCurFieldWidth bits each)
					// C++ lines 531-534
					for (int k = 0; k < cRunLen && ii + k < valueCount; k++) {
						int value = reader.readSignedBits(cCurFieldWidth);
						outValues[ii + k] = value + meanValue;
					}
					
					ii += cRunLen;
					blockNum++;
				}
			}
			
			Logger.info("Bitlength CODEC: consumed {} of {} bits", reader.getBitsConsumed(), lengthBits);
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
		
		if (valueCount == 0) {
			Logger.debug("  Arithmetic codec with 0 values - skipping decoding");
			return;
		}

		// Decode exactly from the code-text payload range. Probability context is not embedded
		// in this code-text stream and must not shift the next CDP offset.
		ArithmeticBitReader reader = new ArithmeticBitReader(buffer, startBit, lengthBits);
		
		
		// Decode values using uniform probability distribution (simplified)
		// Full implementation would use probability context from JT10 file format
		for (int i = 0; i < valueCount; i++) {
			outValues[i] = reader.decodeSymbol();
		}
		
		// Log how many bits were actually consumed vs expected
		int bitsConsumed = reader.getBitsConsumed();
		Logger.debug("  Arithmetic CODEC actual bits consumed: {} vs expected: {}", bitsConsumed, lengthBits);
		if (bitsConsumed != lengthBits) {
			Logger.warn("  WARNING: Bit consumption mismatch!");
			Logger.warn("    Consumed: {} bits", bitsConsumed);
			Logger.warn("    Expected: {} bits", lengthBits);
			Logger.warn("    Difference: {} bits", lengthBits - bitsConsumed);
		}
		return;
	}
	
	/**
	 * Decodes Arithmetic CODEC using the actual probability context.
	 * Implements standard 16-bit precision arithmetic decoding.
	 * 
	 * Reference: Standard Arithmetic Coding algorithm
	 * - 16-bit precision (low/high in [0, 0xFFFF])
	 * - MSB-first bit reading
	 * - Escape symbols map to OOB CDP values
	 */
	private static void decodeArithmeticCodecWithContext(BitByteBuffer buffer, int startBit, int lengthBits,
	                                                      int[] outValues, int valueCount,
	                                                      Int32ProbabilityContextRecord ctx,
	                                                      int[] oobValues) {
		Logger.info("Decoding Arithmetic CODEC with context: {} values, {} bits, {} context entries",
		           valueCount, lengthBits, ctx.entryCount());
		
		if (valueCount == 0 || ctx.entries() == null || ctx.entries().isEmpty()) return;
		
		// Build cumulative frequency table from probability context
		var entries = ctx.entries();
		int numSymbols = entries.size();
		int[] cumFreq = new int[numSymbols + 1]; // cumFreq[0] = 0, cumFreq[numSymbols] = totalFreq
		int[] symbolValues = new int[numSymbols];
		boolean[] isEscape = new boolean[numSymbols];
		
		cumFreq[0] = 0;
		for (int i = 0; i < numSymbols; i++) {
			var entry = entries.get(i);
			symbolValues[i] = entry.associatedValue() + ctx.minValue();
			isEscape[i] = entry.isEscapeSymbol();
			cumFreq[i + 1] = cumFreq[i] + entry.occurrenceCount();
		}
		int totalFreq = cumFreq[numSymbols];
		
		Logger.info("  Arithmetic context: {} symbols, totalFreq={}, minValue={}", numSymbols, totalFreq, ctx.minValue());
		
		// DIAGNOSTIC: Dump first 10 and last 5 entries with cumulative frequencies
		for (int i = 0; i < Math.min(10, numSymbols); i++) {
			var e = entries.get(i);
			Logger.info("  PROB_ENTRY[{}]: escape={} occ={} assocVal={} value={} cumFreq=[{},{}]",
				i, e.isEscapeSymbol(), e.occurrenceCount(), e.associatedValue(), symbolValues[i],
				cumFreq[i], cumFreq[i+1]);
		}
		for (int i = Math.max(10, numSymbols - 5); i < numSymbols; i++) {
			var e = entries.get(i);
			Logger.info("  PROB_ENTRY[{}]: escape={} occ={} assocVal={} value={} cumFreq=[{},{}]",
				i, e.isEscapeSymbol(), e.occurrenceCount(), e.associatedValue(), symbolValues[i],
				cumFreq[i], cumFreq[i+1]);
		}
		// Find and log escape entry
		for (int i = 0; i < numSymbols; i++) {
			if (isEscape[i]) {
				Logger.info("  ESCAPE_ENTRY at sym={}: occ={} cumFreq=[{},{}]",
					i, entries.get(i).occurrenceCount(), cumFreq[i], cumFreq[i+1]);
				break;
			}
		}
		
		// DIAGNOSTIC: Log OOB values if present
		if (oobValues != null) {
			Logger.info("  OOB values: count={}", oobValues.length);
			for (int i = 0; i < Math.min(20, oobValues.length); i++) {
				Logger.info("  OOB[{}]: {}", i, oobValues[i]);
			}
			for (int i = Math.max(20, oobValues.length - 5); i < oobValues.length; i++) {
				Logger.info("  OOB[{}]: {}", i, oobValues[i]);
			}
		}
		
		if (totalFreq == 0) {
			Logger.error("Arithmetic codec: totalFreq is 0, cannot decode");
			return;
		}
		
		// Arithmetic Decoding matching C++ _removeSymbolFromStream implementation.
		// Uses 16-bit precision with explicit masking to prevent overflow in Java's
		// 32-bit int (C++ uses unsigned short which naturally wraps).
		//
		// Reference: Arithmetic.cpp _removeSymbolFromStream / _startDecoder
		final int MASK = 0xFFFF;  // 16-bit mask
		
		// Read bits from LE 32-bit code text words using CodeTextBitReader
		int startByte = startBit / 8;
		CodeTextBitReader ctReader = new CodeTextBitReader(buffer, startByte, lengthBits);
		int bitsRead = 0;
		
		// Helper to read one bit, returning 0 past end of data
		// C++ _startDecoder: reads 16 bits to initialize code register
		int code = 0;
		for (int i = 0; i < 16; i++) {
			int bit = (bitsRead < lengthBits) ? ctReader.readBit() : 0;
			bitsRead++;
			code = ((code << 1) | bit) & MASK;
		}
		int low = 0;
		int high = MASK; // 0xFFFF
		
		Logger.debug("    Initial code=0x{}", Integer.toHexString(code));
		
		int oobIndex = 0;
		
		// Debug: detect large contexts (Z coordinate context has 348 entries)
		boolean isDebugContext = (numSymbols > 100);
		
		if (isDebugContext) {
			int firstWord = buffer.getInt(startByte);
			Logger.info("  ARITH INIT: codeTextStartByte={} firstWord=0x{} lengthBits={} code=0x{}",
				startByte, Integer.toHexString(firstWord), lengthBits, Integer.toHexString(code));
		}
		
		for (int v = 0; v < valueCount; v++) {
			// Determine symbol from current code
			// C++ lookupEntryByCumCount: rescaledCode = ((code - low + 1) * total - 1) / (high - low + 1)
			long range = (long)(high - low) + 1;
			int cum = (int)(((long)(code - low + 1) * totalFreq - 1) / range);
			
			// DEBUG: Check invariant and log around drift point
			if (isDebugContext && (v >= 850 && v <= 865 || code < low || code > high)) {
				Logger.info("  ARITH[v={}]: code=0x{} low=0x{} high=0x{} range={} cum={} bitsRead={}",
					v, Integer.toHexString(code), Integer.toHexString(low), Integer.toHexString(high), range, cum, bitsRead);
			}
			if (code < low || code > high) {
				Logger.error("  INVARIANT VIOLATED at v={}: code=0x{} low=0x{} high=0x{}", 
					v, Integer.toHexString(code), Integer.toHexString(low), Integer.toHexString(high));
			}
			
			// Find symbol: cumFreq[sym] <= cum < cumFreq[sym+1]
			int sym = 0;
			for (sym = 0; sym < numSymbols; sym++) {
				if (cumFreq[sym + 1] > cum) break;
			}
			if (sym >= numSymbols) sym = numSymbols - 1;
			
			// DEBUG: Log symbol selection around drift point
			if (isDebugContext && v >= 850 && v <= 865) {
				Logger.info("  ARITH[v={}]: sym={} cumFreq=[{},{}] val={} escape={} oobIdx={}",
					v, sym, cumFreq[sym], cumFreq[sym+1], symbolValues[sym], isEscape[sym], oobIndex);
			}
			
			// Get the value for this symbol
			if (isEscape[sym]) {
				if (oobValues != null && oobIndex < oobValues.length) {
					outValues[v] = oobValues[oobIndex++];
				} else {
					outValues[v] = 0;
				}
			} else {
				outValues[v] = symbolValues[sym];
			}
			
			// _removeSymbolFromStream: update range then normalize
			// C++: high = low + (range * cumHigh / total) - 1
			//      low  = low + (range * cumLow  / total)
			int newHigh = (low + (int)((range * cumFreq[sym + 1]) / totalFreq) - 1) & MASK;
			int newLow  = (low + (int)((range * cumFreq[sym]) / totalFreq)) & MASK;
			
			if (isDebugContext && v >= 850 && v <= 865) {
				Logger.info("  ARITH[v={}]: rangeUpdate: low 0x{}->0x{} high 0x{}->0x{} val={}",
					v, Integer.toHexString(low), Integer.toHexString(newLow),
					Integer.toHexString(high), Integer.toHexString(newHigh), outValues[v]);
			}
			
			low = newLow;
			high = newHigh;
			
			// Normalize: C++ _removeSymbolFromStream loop
			for (;;) {
				if (((high ^ low) & 0x8000) == 0) {
					// MSBs match (both 0 or both 1) — E1 or E2
				} else if ((low & 0x4000) != 0 && (high & 0x4000) == 0) {
					// E3 underflow: low = 01xx, high = 10xx
					code ^= 0x4000;
					low &= 0x3FFF;
					high |= 0x4000;
				} else {
					break;
				}
				// Shift left and read next bit
				low = (low << 1) & MASK;
				high = ((high << 1) | 1) & MASK;
				int bit = (bitsRead < lengthBits) ? ctReader.readBit() : 0;
				bitsRead++;
				code = ((code << 1) | bit) & MASK;
			}
		}
		
		Logger.debug("  Arithmetic decoded {} values", outValues.length);
		Logger.info("  ARITH FINAL: oobIndex={}/{} bitsRead={}/{} ctReaderBits={}",
			oobIndex, oobValues != null ? oobValues.length : 0,
			bitsRead, lengthBits, ctReader.getBitsConsumed());
	}

	/**
	 * Reads a nibbler-encoded unsigned value (4-bit groups with continuation bit).
	 * From C++ reference Bitlength.cpp: nibblerGet(UInt32&)
	 * Format: [4-bit nibble][1-bit continue][4-bit nibble][1-bit continue]...
	 */
	private static int readNibblerValueUnsigned(BitReader reader) {
		int result = 0;
		int cNibbles = 0;
		while (true) {
			int nibble = reader.readBits(4);
			result |= (nibble << (cNibbles * 4));
			int contBit = reader.readBits(1);  // 1 = more nibbles, 0 = end
			cNibbles++;
			if (contBit == 0) break;
		}
		return result;
	}

	/**
	 * Reads a nibbler-encoded signed value (4-bit groups with continuation bit + sign extension).
	 * From C++ reference Bitlength.cpp: nibblerGet(Int32&) lines 114-133
	 */
	private static int readNibblerValue(BitReader reader) {
		int result = 0;
		int cNibbles = 0;
		while (true) {
			int nibble = reader.readBits(4);
			result |= (nibble << (cNibbles * 4));
			int contBit = reader.readBits(1);  // 1 = more nibbles, 0 = end
			cNibbles++;
			if (contBit == 0) break;
		}
		// Sign-extend the resulting bits (C++ lines 127-131)
		int sw = cNibbles * 4;
		if (sw < 32) {
			result <<= (32 - sw);
			result >>= (32 - sw); // arithmetic right shift for sign extension
		}
		return result;
	}
	
	/**
	 * Bit reader that mirrors the C++ Bitlength codec bit reading:
	 * reads from LE 32-bit code text words, MSB-first within each word.
	 * 
	 * C++ reference: Bitlength.cpp lines 36-57 (GetUnsignedBits),
	 * lines 29-34 (GetSignedBits), lines 576-582 (getNextCodeText).
	 * 
	 * The code text is stored as VecU32 (LE 32-bit words). Bits are read
	 * from bit 31 (MSB) to bit 0 (LSB) within each word, then advancing
	 * to the next word. This differs from raw byte-stream MSB-first reading.
	 */
	private static class CodeTextBitReader {
		private final BitByteBuffer buffer;
		private final int codeTextStartByte; // byte offset where code text words begin
		private final int totalBits;         // total bits in code text
		private int wordIndex;               // current word index (0-based)
		private int uVal;                    // current 32-bit word value (bits shift left as consumed)
		private int nValBits;                // bits remaining in the current word
		private int nBitsConsumed;           // total bits consumed so far
		
		CodeTextBitReader(BitByteBuffer buffer, int codeTextStartByte, int totalBits) {
			this.buffer = buffer;
			this.codeTextStartByte = codeTextStartByte;
			this.totalBits = totalBits;
			this.wordIndex = 0;
			this.nBitsConsumed = 0;
			// Load first word
			loadNextWord();
		}
		
		private void loadNextWord() {
			int byteOffset = codeTextStartByte + wordIndex * 4;
			// Read LE 32-bit word (buffer.getInt uses LE byte order)
			uVal = buffer.getInt(byteOffset);
			// How many valid bits in this word?
			nValBits = Math.min(32, totalBits - wordIndex * 32);
			if (nValBits < 32) {
				// Shift valid bits to MSB position (C++ stores them MSB-aligned)
				// Actually in C++, getNextCodeText just returns the raw word and nBits.
				// GetUnsignedBits reads from MSB (bit 31) downward.
				// If the last word has fewer than 32 valid bits, the valid bits
				// are still in the MSB positions after being written by addCodeText.
				// No shift needed - they're already MSB-aligned in the word.
			}
			wordIndex++;
		}
		
		/**
		 * Read n unsigned bits. Mirrors C++ GetUnsignedBits (lines 36-57).
		 */
		int readBits(int n) {
			if (n == 0) return 0;
			
			int uOut;
			if (nValBits >= n) {
				// Enough bits in current word
				uOut = uVal >>> (32 - n);
				if (n == 32) {
					uVal = 0; // C++: _uVal &= (n==32)-1 → _uVal = 0
				} else {
					uVal <<= n;
				}
				nValBits -= n;
				nBitsConsumed += n;
			} else {
				// Need bits from current word + next word
				int nLBits = nValBits;
				uOut = uVal >>> (32 - n);
				nBitsConsumed += nLBits;
				loadNextWord();
				int nRBits = n - nLBits;
				uOut |= uVal >>> (32 - nRBits);
				if (nRBits == 32) {
					uVal = 0;
				} else {
					uVal <<= nRBits;
				}
				nValBits -= nRBits;
				nBitsConsumed += nRBits;
			}
			return uOut;
		}
		
		/**
		 * Read n signed bits with sign extension. Mirrors C++ GetSignedBits (lines 29-34).
		 */
		int readSignedBits(int n) {
			if (n == 0) return 0;
			int uOut = readBits(n);
			// Sign extend: shift left then arithmetic shift right
			uOut <<= (32 - n);
			uOut >>= (32 - n); // arithmetic right shift in Java
			return uOut;
		}
		
		/**
		 * Read a single bit (0 or 1).
		 */
		int readBit() {
			return readBits(1);
		}
		
		/**
		 * Nibbler decode for signed Int32. Mirrors C++ nibblerGet(Int32&) lines 114-133.
		 */
		int nibblerGetSigned() {
			int result = 0;
			int cNibbles = 0;
			int bMoreBits;
			do {
				int uTmp = readBits(4); // cNibbleWidth = 4
				uTmp <<= cNibbles * 4;
				result |= uTmp;
				bMoreBits = readBits(1);
				cNibbles++;
			} while (bMoreBits != 0);
			// Sign-extend
			int sw = cNibbles * 4;
			if (sw < 32) {
				result <<= (32 - sw);
				result >>= (32 - sw);
			}
			return result;
		}
		
		int getBitsConsumed() {
			return nBitsConsumed;
		}
	}
	
	/**
	 * Helper class for bit-level reading with proper bit offset tracking.
	 * Used by Bitlength decoder to read individual bits and multi-bit values.
	 */
	private static class BitReader {
		private BitByteBuffer buffer;
		int bitPosition;  // Package-private for access by ArithmeticBitReader
		
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
		
		int bitsRemaining() {
			return (buffer.capacity() * 8) - bitPosition;
		}
	}
	
	/**
	 * Helper class for Arithmetic codec decoding.
	 * Reference: Arithmetic.cpp ArithmeticCodec class
	 * 
	 * CRITICAL FIX: Now properly limits bit reading to the encoded data length
	 * to prevent reading past the end of the codec data.
	 */
	private static class ArithmeticBitReader {
		private BitReader bitReader;
		private int startBit;  // Initial bit position
		private int endBit;  // Absolute bit position where encoded data ends
		private int code;
		private int low;
		private int high;
		private int scale;  // For probability scaling
		
		ArithmeticBitReader(BitByteBuffer buffer, int startBit, int lengthBits) {
			this.bitReader = new BitReader(buffer, startBit);
			this.startBit = startBit;  // Store initial position
			this.endBit = startBit + lengthBits;  // Store the boundary
			
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
		 * 
		 * CRITICAL FIX: Now checks bounds to prevent reading past the encoded data
		 */
		int decodeSymbol() {
			// Check if we've reached the end of encoded data
			if (bitReader.bitPosition >= endBit) {
				Logger.debug("  Arithmetic codec reached end of encoded data at bit {}/{}", 
				             bitReader.bitPosition, endBit);
				return 0;  // Return 0 to signal end of data
			}
			
			// Calculate remaining bits we can read
			int bitsRemaining = endBit - bitReader.bitPosition;
			if (bitsRemaining < 8) {
				Logger.debug("  Only {} bits remaining (need 8), truncating symbol read", bitsRemaining);
			}
			
			// Placeholder: return a reasonable default
			// In full implementation, this would use probability context
			// to scale the code and find the symbol range
			int symbolRange = 256;  // Assume small symbol range
			int value = ((code - low) * symbolRange) / scale;
			if (value >= symbolRange) value = symbolRange - 1;
			

			// Update decoder state (simplified)
			// Only read if we have enough bits remaining
			if (bitsRemaining >= 8) {
				code = bitReader.readBits(8);  // Read next symbol bits
			} else if (bitsRemaining > 0) {
				code = bitReader.readBits(bitsRemaining);  // Read remaining bits
			}
			
			return value & 0xFF;
		}
		
		/**
		 * Returns total bits consumed by this decoder so far (relative to start position)
		 */
		int getBitsConsumed() {
			return bitReader.bitPosition - startBit;
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
		Logger.info("Called with startIndex={} bytes, buffer capacity={} bytes", startIndex, buffer.capacity());
		
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
				Logger.info("Successfully read 8 Face Degrees arrays, first has {} values", faceDegrees[0].count());
			}
		} catch (Exception e) {
			Logger.error("Failed to read Face Degrees arrays with byte offset: {}", byteOffset);
			Logger.error("Exception: {}", e.getMessage());
		}
		
		// Continue with CDP-encoded arrays (all byte offsets)
		VecI32 vertexValences = readInt32CDP(buffer, faceDegrees[7].jtEndIndex());
		VecI32 vertexGroups = readInt32CDP(buffer, vertexValences.jtEndIndex());
		
		// Remaining arrays: VecI32.fromByteBuffer takes BYTE OFFSET
		// Get byte offset from jtEndIndex() which now returns bytes
//		VecI32 vertexFlags = VecI32.fromByteBuffer(buffer, vertexGroups.jtEndIndex());
		VecI32 vertexFlags = readInt32CDP(buffer, vertexGroups.jtEndIndex());
		
		// Read 8 Face Attribute Masks arrays (byte offsets)
		VecI32[] faceAttributeMasks = new VecI32[8];
		byteOffset = vertexFlags.jtEndIndex();
		Logger.debug("=== Starting faceAttributeMasks at byte {}", byteOffset);
		for (int i = 0; i < 8; i++) {
			faceAttributeMasks[i] = readInt32CDP(buffer, byteOffset);
			int nextOffset = faceAttributeMasks[i].jtEndIndex();
			Logger.debug("  faceAttributeMasks[{}]: count={}, offset {}→{}", i, faceAttributeMasks[i].count(), byteOffset, nextOffset);
			byteOffset = nextOffset;
		}
		
//		VecI32 faceAttributeMask8 = VecI32.fromByteBuffer(buffer, byteOffset);
		VecI32 faceAttributeMask8 = readInt32CDP(buffer, byteOffset);
		Logger.debug("faceAttributeMask8 count={}, endOffset={}", faceAttributeMask8.count(), faceAttributeMask8.jtEndIndex());
		VecU32 highDegreeFaceAttributeMasks = null;
		VecI32 splitFaceSyms = null;
		VecI32 splitFacePositions = null;
		long compositeHash = 0;
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords = null;
		
		try {
			// Use byte-aligned read since data is LE byte-aligned
			highDegreeFaceAttributeMasks = VecU32.fromByteBufferAligned(buffer, faceAttributeMask8.jtEndIndex());
			Logger.debug("highDegreeFaceAttributeMasks count={}, endOffset={}", highDegreeFaceAttributeMasks.count(), highDegreeFaceAttributeMasks.jtEndIndex());
			splitFaceSyms = readInt32CDP(buffer, highDegreeFaceAttributeMasks.jtEndIndex());
			Logger.debug("splitFaceSyms count={}, endOffset={}", splitFaceSyms.count(), splitFaceSyms.jtEndIndex());
			splitFacePositions = readInt32CDP(buffer, splitFaceSyms.jtEndIndex());
			Logger.debug("splitFacePositions count={}, endOffset={}", splitFacePositions.count(), splitFacePositions.jtEndIndex());
			
			// U32: CompositeHash
			int compositeHashOffset = splitFacePositions.jtEndIndex();
			
			compositeHash = ReadFromBufferUtils.readUnsignedInt(buffer, compositeHashOffset);
			Logger.debug("CompositeHash = 0x{}", Long.toHexString(compositeHash));
			
			// TopologicallyCompressedVertexRecords
			topologicallyCompressedVertexRecords = 
					TopologicallyCompressedVertexRecordsRecord.fromByteBuffer(buffer, compositeHashOffset + 4);
			
			// Print the dequantized vertex coordinate array
			if (topologicallyCompressedVertexRecords.compressedVertexCoordinateArray() != null) {
				CompressedVertexCoordinateArrayRecord coordArray = topologicallyCompressedVertexRecords.compressedVertexCoordinateArray();
				Logger.info("=== Compressed Vertex Coordinate Array: {} vertices, {} components ===",
						coordArray.uniqueVertexCount(), coordArray.numberComponents());
				float[][] coords = coordArray.dequantize();
				
				// Launch 3D viewer to display the geometry
				JTGeometryViewer.show(coordArray);
			}
		} catch (Exception e) {
			Logger.error(e, "Failed to parse highDegreeFaceAttributeMasks/splitFace/vertexRecords");
			Logger.error("This is likely due to incorrect offset calculation for highDegreeFaceAttributeMasks at offset {}", faceAttributeMask8.jtEndIndex());
		}
		
		return new TopologicallyCompressedRepDataRecord(faceDegrees, vertexValences, vertexGroups, vertexFlags, faceAttributeMasks, faceAttributeMask8, highDegreeFaceAttributeMasks, splitFaceSyms, splitFacePositions, compositeHash, topologicallyCompressedVertexRecords);
	}

	@Override
	public int jtEndIndex() {
		if (topologicallyCompressedVertexRecords != null) {
			return topologicallyCompressedVertexRecords.jtEndIndex();
		}
		// Fallback when vertex records couldn't be parsed
		return splitFacePositions != null ? splitFacePositions.jtEndIndex() : 0;
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

			// Decode all values with fixed field width (unsigned, add min)
			for (int i = 0; i < valueCount; i++) {
				int symbol = 0;
				if (fieldWidth != 0) {
					// Read as unsigned (C++ line 502: GetUnsignedBits)
					symbol = (int) byteBuffer.readBitsAt(currentBitPosition, fieldWidth);
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
			
			// Constants from C++ reference (Bitlength.cpp lines 223-225)
			final int cBlkValBits = 4; // signed delta width (4 bits)
			final int cBlkLenBits = 4; // run length width (4 bits)

			int cMaxFieldDecr = -(1 << (cBlkValBits - 1)); // -8
			int cMaxFieldIncr = (1 << (cBlkValBits - 1)) - 1; // +7

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
