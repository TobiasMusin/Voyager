package io.github.tomusin.lodDataRecords;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

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
	

	private static VecI32 readInt32CDP(BitByteBuffer buffer, int startIndex) {
		
		long valueCount = ReadFromBufferUtils.readUnsignedIntFromBitIndex(buffer, startIndex); // amount of resulting integer values
		int codec = ReadFromBufferUtils.readUnsignedByteFromBitIndex(buffer, startIndex + 32);
		int chopBits = -1;
		int codeTextLength = -1; // Amount of bits that make up the code
		VecU32 codeText = null;

		switch (codec) {
		case 0 -> {
			System.out.println("possibleCount: " + valueCount + " codec: " + codec);
			System.out.println("Null CODEC");
//			if (valueCount == 0) { // Not correct
//				int[] decodedSymbols = new int[(int) valueCount];
//				return new VecI32((int) valueCount, decodedSymbols, startIndex + 72);
//			}
			codeTextLength = buffer.getIntAtBitPosition(startIndex + 40);
//			codeText = VecU32.fromByteBuffer(buffer, startIndex + 5, codeTextLength);
			int[] decodedSymbols = new int[(int) valueCount];
//			int bytesToRead = (codeTextLength + 7) / 8;
			for (int j = 0; j < valueCount; j++) {
				decodedSymbols[j] = buffer.getIntAtBitPosition(startIndex + 72 + j * 32);
			}
			return new VecI32((int) valueCount, decodedSymbols, startIndex + 72 + (int) codeTextLength * 32);
		}
		case 1 -> {
			System.out.println("valueCount: " + valueCount + " codec: " + codec);
			System.out.println("Bitlength CODEC");
			codeTextLength = buffer.getIntAtBitPosition(startIndex + 40);

			int encodedCodeTextStartIndex = startIndex + 72;
//						long encodedBits = getBits(buffer, encodedCodeTextStartIndex, codeTextLength);
//						System.out.printf("EncodedBits: %23s%n", Long.toBinaryString(encodedBits).replace(' ', '0'));
//			BitReader bitReader = new BitReader(buffer, encodedCodeTextStartIndex);
//			int[] decodedSymbols = new int[(int) valueCount];
//			int tagBit = bitReader.readBit();
//			if (tagBit == 0) { // FixedLength
//				int min = bitReader.nibblerGetSigned();
//				int max = bitReader.nibblerGetSigned();
//				int bitWidth = bitsize(max - min);
//				for (int decodedSymbolCount = 0; decodedSymbolCount < valueCount; decodedSymbolCount++) {
//					int decodedSymbol = 0;
//					if (bitWidth != 0) {
//						decodedSymbol = bitReader.readBits(bitWidth);
//						decodedSymbol <<= (32 - bitWidth);
//						decodedSymbol >>= (32 - bitWidth);
//					}
//					decodedSymbols[decodedSymbolCount] = decodedSymbol + min;
//				}
//			} else {
//				int windowBitSize = 0;
//				int mean = bitReader.nibblerGetSigned();
//				
//				// Constants from spec (cBlkValBits=3, cBlkLenBits=4 → confirmed in encode())
//			    final int cBlkValBits = 3; // number of bits used to encode the change in field width
//			    final int cBlkLenBits = 4; // how many symbols are encoded with the same bit width
//			    
//			    int cMaxFieldDecr = -(1 << (cBlkValBits - 1)); // -4
//			    int cMaxFieldIncr = (1 << (cBlkValBits - 1)) - 1; // +3
//
//			    int cCurFieldWidth = 0;
//			    int ii = 0;
//
//			    while (ii < valueCount) {
//			        // Step 1: adjust field width until delta is not extreme
//			        int cDeltaFieldWidth;
//			        do {
//			            cDeltaFieldWidth = bitReader.readSignedBits(cBlkValBits);
//			            cCurFieldWidth += cDeltaFieldWidth;
//			        } while (cDeltaFieldWidth == cMaxFieldDecr || cDeltaFieldWidth == cMaxFieldIncr);
//
//			        // Step 2: read run length
//			        int cRunLen = bitReader.readBits(cBlkLenBits);
//
//			        // Step 3: decode run values
//			        for (int k = ii; k < ii + cRunLen && k < valueCount; k++) {
//			            int symbol = bitReader.readSignedBits(cCurFieldWidth);
//			            decodedSymbols[k] = symbol + mean;
//			        }
//
//			        ii += cRunLen;
//			    }
//			}
//
//			Arrays.stream(decodedSymbols).forEach(decodedSymbol -> System.out.println(decodedSymbol));
//			return new VecI32((int) valueCount, decodedSymbols, startIndex + 9 + bytesToRead);
			int[] decodedSymbols = new int[(int) valueCount];
			int bufferPositionInBit = decodeBitlength(buffer, encodedCodeTextStartIndex, (int) valueCount, decodedSymbols);
			System.out.println("BitPos: " + bufferPositionInBit);
			return new VecI32((int) valueCount, decodedSymbols, bufferPositionInBit);
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
//				    		System.out.println("CodeText: " + codeText);				    			
//
//							
//
//						} catch (Exception e) {
//
//						}
		}
		case 2 -> System.out.println("Illegal value for CODEC: 2");
		case 3 -> {
			System.out.println("valueCount: " + valueCount + " codec: " + codec);
			System.out.println("Arithmetic CODEC");
			codeTextLength = buffer.getInt(startIndex + 5);
			try {
				if (codeTextLength < 6000) {
//					codeText = VecU32.fromByteBuffer(buffer, startIndex + 9, codeTextLength);
					System.out.println("CodeText: " + codeText);
				}
			} catch (Exception e) {

			}
		}
		case 4 -> {
//			chopBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5);
			System.out.println("valueCount: " + valueCount + " codec: " + codec);
			System.out.println("Chopper CODEC");
		}
		case 5 -> System.out.println("Move-to-front CODEC");
		default -> {System.out.println("Unknown CODEC: " + codec);// throw new IllegalArgumentException("Unknown CODEC: " + codec);
			for (int i = -100; i < 100; i++){
//				System.out.println("Value from index: " + i + ": " + ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4 + i));
			}
		}									
		
		}

		if (codec >= 0 && codec <= 5 && valueCount < 10000 && valueCount >= 0) {
			System.out.println("valueCount: " + valueCount + " codec: " + codec);
		}
		return null;
	}

	private static int adjustBitWindow(BitReader bitReader, int windowBitSize) {
		int adjustWindowSizeBit = bitReader.readBit();
		do {
			if (adjustWindowSizeBit == 1) {
				windowBitSize += 2;
			} else {
				windowBitSize -= 2;
			}
		} while (bitReader.readBit() == adjustWindowSizeBit);
		return windowBitSize;
	}

	private static void readBitWindowAsInt(BitReader bitReader, int windowBitSize, int[] decodedSymbols,
			int decodedSymbolCount) {
		int decodedSymbol = 0;
		if (windowBitSize != 0) {
			decodedSymbol = bitReader.readBits(windowBitSize);
			decodedSymbol <<= (32 - windowBitSize);
			decodedSymbol >>= (32 - windowBitSize);
		}
		decodedSymbols[decodedSymbolCount] = decodedSymbol;
	}

	public static TopologicallyCompressedRepDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {

		VecI32[] faceDegrees = new VecI32[8];
		faceDegrees[0] = readInt32CDP(buffer, startIndex * 8);
		for (int i = 1; i < 8; i++) {
			faceDegrees[i] = readInt32CDP(buffer, faceDegrees[i - 1].jtEndIndex());
		}
		VecI32 vertexValences = readInt32CDP(buffer, faceDegrees[7].jtEndIndex());
		VecI32 vertexGroups = readInt32CDP(buffer, vertexValences.jtEndIndex());
		Logger.info(vertexGroups.toString());
		Logger.info(vertexValences.toString());
		// Note: VecI32 is what we get after decompression
		// I assume we have to treat the following bytes as a compressed block first,
		// according to page 146, Figure 130
//		VecI32[] faceDegrees = new VecI32[8];
//		for (int i = 0; i < 8; i++) {
//			faceDegrees[i] = VecI32.fromByteBuffer(buffer, startIndex);
//			startIndex = faceDegrees[i].jtEndIndex();
//		}
//		VecI32 vertexValences = VecI32.fromByteBuffer(buffer, startIndex);
//		VecI32 vertexGroups = VecI32.fromByteBuffer(buffer, vertexValences.jtEndIndex());
		VecI32 vertexFlags = VecI32.fromByteBuffer(buffer, vertexGroups.jtEndIndex());
		VecI32[] faceAttributeMasks = new VecI32[8];
		startIndex = vertexFlags.jtEndIndex();
		for (int i = 0; i < 8; i++) {
			faceAttributeMasks[i] = VecI32.fromByteBuffer(buffer, startIndex);
			startIndex = faceAttributeMasks[i].jtEndIndex();
		}
		VecI32 faceAttributeMask8 = VecI32.fromByteBuffer(buffer, startIndex);
		VecU32 highDegreeFaceAttributeMasks = VecU32.fromByteBuffer(buffer, faceAttributeMask8.jtEndIndex());
		VecI32 splitFaceSyms = VecI32.fromByteBuffer(buffer, highDegreeFaceAttributeMasks.jtEndIndex());
		VecI32 splitFacePositions = VecI32.fromByteBuffer(buffer, splitFaceSyms.jtEndIndex());
		long compositeHash = ReadFromBufferUtils.readUnsignedInt(buffer, splitFacePositions.jtEndIndex());
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords = TopologicallyCompressedVertexRecordsRecord
				.fromByteBuffer(buffer, splitFacePositions.jtEndIndex() + 4);
		return new TopologicallyCompressedRepDataRecord(faceDegrees, vertexValences, vertexGroups, vertexFlags,
				faceAttributeMasks, faceAttributeMask8, highDegreeFaceAttributeMasks, splitFaceSyms, splitFacePositions,
				compositeHash, topologicallyCompressedVertexRecords);
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
	
	public static int nibblerGetSignedInt(BitByteBuffer buffer, int startPosition) { // Not working correctly
		int value = 0;
	    int cNibbles = 0;

	    int bMoreBits;
	    do {
	        // --- Read one nibble (4 bits) ---
	        int uTmp = (int) buffer.readBitsAt(startPosition, 4);
	        startPosition += 4;

	        uTmp <<= cNibbles * 4;  // shift into the correct position
	        value |= uTmp;

	        // --- Read continuation flag (1 bit) ---
	        bMoreBits = (int) buffer.readBitsAt(startPosition, 1);
	        startPosition += 1;

	        cNibbles++;
	    } while (bMoreBits != 0);

	    // --- Sign extension ---
	    int sw = cNibbles * 4;  // number of bits that make up the actual value
	    if (sw < 32) {
	        value <<= (32 - sw);
	        value >>= (32 - sw); // arithmetic right shift → sign extension
	    }
	    buffer.setBitOffset(startPosition);
	    return value;
	}
	
	public static int decodeBitlength(BitByteBuffer byteBuffer, int startIndex, int valueCount, int[] decodedSymbols) {
//	    BitReader bitReader = new BitReader(byteBuffer, startIndex);

	    // Step 1: Tag bit
//	    int tagBit = bitReader.readBit();
	    int tagBit = byteBuffer.readBitsAt(startIndex, 1);

	    if (tagBit == 0) {
	        // -------------------------------------------------
	        // Fixed-width coding
	        // -------------------------------------------------

	        // Read min/max
//	        int min = bitReader.nibblerGetSigned();
//	        int max = bitReader.nibblerGetSigned();
	        int min = nibblerGetSignedInt(byteBuffer, startIndex + 1);
	        int max = nibblerGetSignedInt(byteBuffer, byteBuffer.getBitOffset());
	        

	        int fieldWidth = bitsize(max - min);

	        for (int i = 0; i < valueCount; i++) {
//	            int symbol = bitReader.readSignedBits(fieldWidth);
	            int symbol = byteBuffer.readBitsAt(byteBuffer.getBitOffset(), fieldWidth);
	            decodedSymbols[i] = symbol + min;
	        }

	    } else {
	        // -------------------------------------------------
	        // Variable-width coding
	        // -------------------------------------------------

	        // Read mean
//	        int mean = bitReader.nibblerGetSigned();
	        int mean = nibblerGetSignedInt(byteBuffer, startIndex + 1);
	        // Constants from spec
	        final int cBlkValBits = 3; // signed delta width
	        final int cBlkLenBits = 4; // run length width

	        int cMaxFieldDecr = -(1 << (cBlkValBits - 1)); // -4
	        int cMaxFieldIncr = (1 << (cBlkValBits - 1)) - 1; // +3

	        int cCurFieldWidth = 0;
	        int ii = 0;

	        while (ii < valueCount) {
	            // Step 1: adjust field width
	            int cDeltaFieldWidth;
	            do {
//	                cDeltaFieldWidth = bitReader.readSignedBits(cBlkValBits);
	                cDeltaFieldWidth = byteBuffer.readBitsAt(byteBuffer.getBitOffset(), cBlkLenBits);
	                cCurFieldWidth += cDeltaFieldWidth;
	            } while (cDeltaFieldWidth == cMaxFieldDecr ||
	                     cDeltaFieldWidth == cMaxFieldIncr);

	            // Step 2: run length
//	            int cRunLen = bitReader.readBits(cBlkLenBits);
	            int cRunLen = byteBuffer.readBitsAt(byteBuffer.getBitOffset(), cBlkLenBits);
	            // Step 3: decode run
	            for (int k = ii; k < ii + cRunLen && k < valueCount; k++) {
//	                int symbol = bitReader.readSignedBits(cCurFieldWidth);
	                int symbol = byteBuffer.readBitsAt(byteBuffer.getBitOffset(), cCurFieldWidth);
	                decodedSymbols[k] = symbol + mean;
	            }

	            ii += cRunLen;
	        }
	    }
	    return byteBuffer.getBitOffset();
//	    return bitReader.getPosition();
	}

}
