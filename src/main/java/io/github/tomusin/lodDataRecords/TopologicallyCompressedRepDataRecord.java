package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.VecI32;
import io.github.tomusin.voyager.datastructures.VecU32;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

// Page 97, Figure 92
public record TopologicallyCompressedRepDataRecord(
		VecI32[] faceDegrees,
		VecI32 vertexValences,
		VecI32 vertexGroups,
		VecI32 vertexFlags,
		VecI32[] faceAttributeMasks,
		VecI32 faceAttributeMask8,
		VecU32 highDegreeFaceAttributeMasks,
		VecI32 splitFaceSyms,
		VecI32 splitFacePositions,
		long compositeHash,
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords
		) implements BufferDeserializable {

	public static TopologicallyCompressedRepDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		// Scan for the right start index, it seems the documentatation leads us the a misaligned offset
		for (int i = -100; i < 100; i++) {
			int possibleCount = buffer.getInt(startIndex + i);
			long pc = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + i);
			int codec = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 4 + i);
			int chopBits = -1;
			int codeTextLength = -1;
			VecU32 codeText = null;
			
			switch (codec) {
		    case 0 -> System.out.println("Null CODEC");
		    case 1 -> {
		    	System.out.println("possibleCount: " + possibleCount + " codec: " + codec);
		    	System.out.println("pc: " + pc);
		    	System.out.println("Bitlength CODEC");
		    	codeTextLength = buffer.getInt(startIndex + 5 + i);
		    	try {
		    		codeText = VecU32.fromByteBuffer(buffer, startIndex + 9 + i);		    		
		    	} catch (Exception e) {
		    		
		    	}
		    }
		    case 2 -> System.out.println("Illegal value for CODEC: 2");
		    case 3 -> System.out.println("Arithmetic CODEC");
		    case 4 -> {
		    	chopBits = ReadFromBufferUtils.readUnsignedByte(buffer, startIndex + 5 + i);
		    	System.out.println("possibleCount: " + possibleCount + " codec: " + codec);
		    	System.out.println("pc: " + pc);
		    	System.out.println("Chopper CODEC");
		    }
		    case 5 -> System.out.println("Move-to-front CODEC");
		    default -> System.out.println("Unknown CODEC: " + codec);//throw new IllegalArgumentException("Unknown CODEC: " + codec);
		}

			
			
			if (codec >= 0 && codec <=5 && pc < 1000 &&  pc >= 0 && ((chopBits > 0 && chopBits < 32) || (codeTextLength > 0 && codeTextLength < 80000 && codeText != null))) {
				System.out.println("possibleCount: " + pc + " codec: " + codec);
				System.out.println("At index: " + i);
			}
		}
		// Note: VecI32 is what we get after decompression
		// I assume we have to treat the following bytes as a compressed block first, according to page 146, Figure 130
		VecI32[] faceDegrees = new VecI32[8];
		for (int i = 0; i < 8; i++) {
			faceDegrees[i] = VecI32.fromByteBuffer(buffer, startIndex);
			startIndex = faceDegrees[i].jtEndIndex();
		}
		VecI32 vertexValences = VecI32.fromByteBuffer(buffer, startIndex);
		VecI32 vertexGroups = VecI32.fromByteBuffer(buffer, vertexValences.jtEndIndex());
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
		TopologicallyCompressedVertexRecordsRecord topologicallyCompressedVertexRecords = TopologicallyCompressedVertexRecordsRecord.fromByteBuffer(buffer, splitFacePositions.jtEndIndex() + 4);
		return new TopologicallyCompressedRepDataRecord(faceDegrees, vertexValences, vertexGroups, vertexFlags, faceAttributeMasks, faceAttributeMask8, highDegreeFaceAttributeMasks, splitFaceSyms, splitFacePositions, compositeHash, topologicallyCompressedVertexRecords);
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

}
