package io.github.tomusin.lodDataRecords;

import java.util.Arrays;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.BitByteBuffer;
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
		for (int i = 4; i < len; i++) {
			int predicted = residuals[i - 1];
			if (predictorType == PredictorType.PredXor1) {
				residuals[i] = residuals[i] ^ predicted;
			} else {
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
		
		// Bounds check for minimum header (always at least 5 bytes)
		if (startIndex < 0 || startIndex + 5 > buffer.capacity()) {
			Logger.error("readInt32CDP: startIndex {} is out of bounds (buffer capacity {})", 
			            startIndex, buffer.capacity());
			return new VecI32(0, new int[0], startIndex);
		}
		
		int valueCount = buffer.getInt(startIndex);
		int codecType = buffer.get(startIndex + 4) & 0xFF;

		if (valueCount == 0 && codecType == 4) {
			if (startIndex + 6 > buffer.capacity()) {
				return new VecI32(0, new int[0], startIndex + 5);
			}
			int chopBits = buffer.get(startIndex + 5) & 0xFF;
			if (chopBits == 0) {
				return readInt32CDP(buffer, startIndex + 6, recursionDepth + 1, false);
			}
			if (startIndex + 11 > buffer.capacity()) {
				return new VecI32(0, new int[0], startIndex + 5);
			}
			VecI32 msbData = readInt32CDP(buffer, startIndex + 11, recursionDepth + 1, false);
			VecI32 lsbData = readInt32CDP(buffer, msbData.jtEndIndex(), recursionDepth + 1, false);
			return new VecI32(0, new int[0], lsbData.jtEndIndex());
		}
		
		if (valueCount == 0 && codecType == 5) {
			VecI32 msbData = readInt32CDP(buffer, startIndex + 5, recursionDepth + 1, false);
			VecI32 winData = readInt32CDP(buffer, msbData.jtEndIndex(), recursionDepth + 1, false);
			return new VecI32(0, new int[0], winData.jtEndIndex());
		}
		
		if (valueCount == 0) {
			return new VecI32(0, new int[0], startIndex + 4);
		}
		
		if (codecType != 4 && codecType != 5 && startIndex + 9 > buffer.capacity()) {
			Logger.error("readInt32CDP: startIndex {} needs 9 bytes but only {} available", 
			            startIndex, buffer.capacity() - startIndex);
			return new VecI32(0, new int[0], startIndex);
		}
		
		if (codecType == 4 && startIndex + 11 > buffer.capacity()) {
			Logger.error("readInt32CDP: Chopper codec at {} needs 11 bytes but only {} available", 
			            startIndex, buffer.capacity() - startIndex);
			return new VecI32(0, new int[0], startIndex);
		}
		
		int codeTextLengthBits = 0;
		int endByte = startIndex + 9;
		
		if (codecType == 5) {
			// ========== MOVE-TO-FRONT CODEC ==========
			VecI32 choppedMsbData = readInt32CDP(buffer, startIndex + 5, recursionDepth + 1, false);
			VecI32 windowOffsets = readInt32CDP(buffer, choppedMsbData.jtEndIndex(), recursionDepth + 1, false);
			
			// TODO: implement actual MtF decoding using choppedMsbData + windowOffsets
			int[] decodedData = new int[valueCount];
			
			return new VecI32(valueCount, decodedData, windowOffsets.jtEndIndex());
		} else if (codecType == 4) {
			// ========== CHOPPER CODEC HEADER ==========
			int chopBits = buffer.get(startIndex + 5) & 0xFF;
			int valueBias = buffer.getInt(startIndex + 6);
			int valueSpanBits = buffer.get(startIndex + 10) & 0xFF;
			
			VecI32 msbData = readInt32CDP(buffer, startIndex + 11, recursionDepth + 1, false);
			int lsbStartByte = msbData.jtEndIndex();
			VecI32 lsbData = readInt32CDP(buffer, lsbStartByte, recursionDepth + 1, false);
			int chopperEndByte = lsbData.jtEndIndex();
			
			// Merge the two data sets (MSB and LSB)
			// For now, just use MSB data as placeholder
			int[] mergedData = new int[valueCount];
			System.arraycopy(msbData.valueArray(), 0, mergedData, 0, Math.min(msbData.valueArray().length, valueCount));
			
			return new VecI32(valueCount, mergedData, chopperEndByte);
		} else {
			// ========== STANDARD HEADER (Types 0, 1, 3) ==========
			codeTextLengthBits = buffer.getInt(startIndex + 5);
			
			Logger.trace("CDP Header at byte {}: valueCount={}, codecType={}, codeTextLengthBits={}", 
			           startIndex, valueCount, codecType, codeTextLengthBits);
			
			int codeTextWordBytes = ((codeTextLengthBits + 31) / 32) * 4;
			endByte = startIndex + 9 + codeTextWordBytes;
		}
		
		// Validate valueCount and codecType first — these are read before codeTextLengthBits,
		// so returning startIndex+5 (count+type) is the tightest safe recovery offset.
		if (valueCount < 0 || valueCount > 1000000) {
			Logger.error("CDP VALIDATION ERROR at byte {}: invalid valueCount={}", startIndex, valueCount);
			return new VecI32(0, new int[0], startIndex + 5);
		}
			
		if (codecType < 0 || codecType > 5) {
			Logger.error("CDP VALIDATION ERROR at byte {}: invalid codecType={}", startIndex, codecType);
			return new VecI32(0, new int[0], startIndex + 5);
		}

		if (codeTextLengthBits > buffer.capacity() * 8) {
			Logger.error("CDP VALIDATION ERROR at byte {}: codeTextLengthBits {} exceeds buffer capacity", 
			            startIndex, codeTextLengthBits);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		if (codecType == 0 && codeTextLengthBits != valueCount * 32) {
			Logger.error("CDP VALIDATION ERROR at byte {}: NULL codec requires codeTextLengthBits={} but got {}", 
			            startIndex, valueCount * 32, codeTextLengthBits);
			return new VecI32(0, new int[0], startIndex + 9);
		}
			
		if (codeTextLengthBits < 0) {
			Logger.warn("CDP at byte {}: negative codeTextLengthBits={}, treating as 0", startIndex, codeTextLengthBits);
			codeTextLengthBits = 0;
		}
			
		// ========== CODEC DISPATCH ==========
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
			if (!isOobCall) {
				int probCtxStartBit = endByte * 8;
				Int32ProbabilityContextRecord ctx = Int32ProbabilityContextRecord.fromBitBuffer(buffer, probCtxStartBit);
				int probCtxEndBit = ctx.jtEndBitIndex();
				int probCtxEndByte = (probCtxEndBit + 7) / 8;
				
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
				
				decodeArithmeticCodecWithContext(buffer, encodedDataStartBit, codeTextLengthBits,
				                                 decodedValues, (int) valueCount, ctx, oobValues);
			} else {
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
			Logger.error("CODEC 5 (MtF) should have been handled in header parsing!");
			codecImplemented = false;
		}
		default -> {
			Logger.error("Unknown CODEC type: {}", codecType);
			codecImplemented = false;
		}
		}
			
		// ========== RESIDUAL UNPACKING ==========
		// OOB CDPs carry raw residuals that will be accumulated by the parent
		// sequence's unpackResiduals — do NOT accumulate them independently.
		if (codecImplemented && valueCount > 0 && !isOobCall) {
			unpackResiduals(decodedValues, predictorType);
		}

		if (!isOobCall && codecType == 3) {
			// Already handled above - skip
		}
			
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
		if (valueCount <= 0 || valueCount > 1000000) {
			Logger.error("Bitlength CODEC: Invalid valueCount {}", valueCount);
			return;
		}
		
		try {
			int startByte = startBit / 8;
			CodeTextBitReader reader = new CodeTextBitReader(buffer, startByte, lengthBits);
			
			int formatTag = reader.readBit();
			
			if (formatTag == 0) {
				// ===== FIXED-WIDTH FORMAT =====
				int minSymbol = reader.nibblerGetSigned();
				int maxSymbol = reader.nibblerGetSigned();
				int valSpanBits = bitsize(maxSymbol - minSymbol);
				
				for (int i = 0; i < valueCount; i++) {
					int value = reader.readBits(valSpanBits);
					outValues[i] = value + minSymbol;
				}
			} else {
				// ===== VARIABLE-WIDTH FORMAT (Block-based) =====
				int meanValue = reader.nibblerGetSigned();
				
				final int cBlkValBits = 4;
				final int cBlkLenBits = 4;
				final int maxFieldIncr = (1 << (cBlkValBits - 1)) - 1;
				final int maxFieldDecr = -(1 << (cBlkValBits - 1));
				
				int cCurFieldWidth = 0;
				int ii = 0;
				
				while (ii < valueCount) {
					int cDeltaFieldWidth;
					do {
						cDeltaFieldWidth = reader.readSignedBits(cBlkValBits);
						cCurFieldWidth += cDeltaFieldWidth;
					} while (cDeltaFieldWidth == maxFieldDecr || cDeltaFieldWidth == maxFieldIncr);
					
					int cRunLen = reader.readBits(cBlkLenBits);
					
					for (int k = 0; k < cRunLen && ii + k < valueCount; k++) {
						int value = reader.readSignedBits(cCurFieldWidth);
						outValues[ii + k] = value + meanValue;
					}
					
					ii += cRunLen;
				}
			}
		} catch (Exception e) {
			Logger.error("Bitlength CODEC decoding failed: {}", e.getMessage());
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
		if (valueCount == 0) return;

		ArithmeticBitReader reader = new ArithmeticBitReader(buffer, startBit, lengthBits);
		
		for (int i = 0; i < valueCount; i++) {
			outValues[i] = reader.decodeSymbol();
		}
	}
	
	private static void decodeArithmeticCodecWithContext(BitByteBuffer buffer, int startBit, int lengthBits,
	                                                      int[] outValues, int valueCount,
	                                                      Int32ProbabilityContextRecord ctx,
	                                                      int[] oobValues) {
		if (valueCount == 0 || ctx.entries() == null || ctx.entries().isEmpty()) return;
		
		var entries = ctx.entries();
		int numSymbols = entries.size();
		int[] cumFreq = new int[numSymbols + 1];
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
		
		if (totalFreq == 0) {
			Logger.error("Arithmetic codec: totalFreq is 0, cannot decode");
			return;
		}
		
		final int MASK = 0xFFFF;
		
		int startByte = startBit / 8;
		CodeTextBitReader ctReader = new CodeTextBitReader(buffer, startByte, lengthBits);
		int bitsRead = 0;
		
		int code = 0;
		for (int i = 0; i < 16; i++) {
			int bit = (bitsRead < lengthBits) ? ctReader.readBit() : 0;
			bitsRead++;
			code = ((code << 1) | bit) & MASK;
		}
		int low = 0;
		int high = MASK;
		
		int oobIndex = 0;
		
		for (int v = 0; v < valueCount; v++) {
			long range = (long)(high - low) + 1;
			int cum = (int)(((long)(code - low + 1) * totalFreq - 1) / range);
			
			if (code < low || code > high) {
				Logger.error("Arithmetic INVARIANT VIOLATED at v={}: code=0x{} low=0x{} high=0x{}", 
					v, Integer.toHexString(code), Integer.toHexString(low), Integer.toHexString(high));
			}
			
			int sym = 0;
			for (sym = 0; sym < numSymbols; sym++) {
				if (cumFreq[sym + 1] > cum) break;
			}
			if (sym >= numSymbols) sym = numSymbols - 1;
			
			if (isEscape[sym]) {
				if (oobValues != null && oobIndex < oobValues.length) {
					outValues[v] = oobValues[oobIndex++];
				} else {
					outValues[v] = 0;
				}
			} else {
				outValues[v] = symbolValues[sym];
			}
			
			int newHigh = (low + (int)((range * cumFreq[sym + 1]) / totalFreq) - 1) & MASK;
			int newLow  = (low + (int)((range * cumFreq[sym]) / totalFreq)) & MASK;
			
			low = newLow;
			high = newHigh;
			
			for (;;) {
				if (((high ^ low) & 0x8000) == 0) {
					// MSBs match — E1 or E2
				} else if ((low & 0x4000) != 0 && (high & 0x4000) == 0) {
					// E3 underflow
					code ^= 0x4000;
					low &= 0x3FFF;
					high |= 0x4000;
				} else {
					break;
				}
				low = (low << 1) & MASK;
				high = ((high << 1) | 1) & MASK;
				int bit = (bitsRead < lengthBits) ? ctReader.readBit() : 0;
				bitsRead++;
				code = ((code << 1) | bit) & MASK;
			}
		}
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
		} catch (Throwable e) {
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

}
