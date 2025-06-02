package io.github.tomusin.voyager.readers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.XZInputStream;

import io.github.tomusin.voyager.fileRecords.FileHeaderRecord;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.fileRecords.TOCRecord;
import io.github.tomusin.voyager.segments.DataSegment;
import io.github.tomusin.voyager.segments.DataSegmentType;
import io.github.tomusin.voyager.segments.LSGDataSegment;
import io.github.tomusin.voyager.segments.MetaDataSegment;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public class JTReader {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JTReader.class);
	FileHeaderRecord fileHeaderRecord;
	
	// Seems most writers ignore the U64 Segment offset and write I32 instead
	// Also 9.5 seems to have some problems for me, maybe documentation changed? -> investigate and fix later
	private static Set<String> writersThatWriteI32SegmentOffset = Set.of("Version 10.5 JT  DM 10.3.1.2", "Version 10.3 JT  DM 9.4.0.0", "Version 9.5 JT  DM 8.0.7.0");
	private String strippedVersionString;
	private ByteOrder fileByteOrder;

	public void startReading(Path filename) {
		long startTime = System.nanoTime();
	    try (RandomAccessFile file = new RandomAccessFile(filename.toAbsolutePath().toString(), "r");
	         FileChannel fileChannel = file.getChannel()) {

	        // Map the file into memory
	        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
	        setFileHeaderRecordFromFile(buffer);
	        
	        int[] tocStartIndices = generateTOCOffsets(fileHeaderRecord.tocOffset(), fileHeaderRecord.tocEntryCount());
	        Map<String, TOCRecord> tocMap = new ConcurrentHashMap<>();
	        Arrays.stream(tocStartIndices).parallel().forEach(tocStartIndex -> readTOCEntry(buffer, tocStartIndex, tocMap, writersThatWriteI32SegmentOffset.contains(strippedVersionString)));
	        
	        int[] segmentOffsets = tocMap.entrySet()
                    .stream()
                    .mapToLong(entry -> entry.getValue().segmentOffset())
                    .mapToInt(value -> {
                        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                            throw new IllegalArgumentException("Value out of range for int: " + value);
                        }
                        return (int) value;
                    })
                    .toArray();
	        
	        Map<String, SegmentHeaderRecord> segmentHeaderMap = new ConcurrentHashMap<>();
	        LOGGER.info("----------------------------------------------------------");

	        Arrays.stream(segmentOffsets).forEach(segmentHeaderOffset -> readSegmentHeaderEntry(buffer, segmentHeaderOffset, segmentHeaderMap));

	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    long endTime = System.nanoTime();
	    LOGGER.info("Total time: {} ms", (endTime - startTime) / 1_000_000);
	}
	
	private void readTOCEntry(MappedByteBuffer buffer, int startIndex, Map<String, TOCRecord> tocMap, boolean i32InsteadOfU64) {
		String segmentGUID = getGUID(buffer, startIndex);
		long segmentOffset;
		long segmentLength;
		
		if (i32InsteadOfU64) {
			segmentOffset = buffer.getInt(startIndex + 16);
			segmentLength = buffer.getInt(startIndex + 16 + 8);
		} else {
			segmentOffset = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex + 16);
			segmentLength = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 16 + 8);			
		}
		long segmentAttributes = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 16 + 8 + 4);
		tocMap.put(segmentGUID, new TOCRecord(segmentGUID, segmentOffset, segmentLength, segmentAttributes));
		LOGGER.info("segmentOffset is: {}", segmentOffset);
		LOGGER.info("Created TOCRecord: {}", tocMap.get(segmentGUID));
		LOGGER.info("SegmentType: {} -> {}", tocMap.get(segmentGUID).getSegmentType(), DataSegmentType.getByTypeId((int) tocMap.get(segmentGUID).getSegmentType()).getContents());
		LOGGER.info("Length in TOC Entry is: {}", tocMap.get(segmentGUID).segmentLength());
	}
	
	private void readSegmentHeaderEntry(MappedByteBuffer buffer, int startIndex, Map<String, SegmentHeaderRecord> segmentHeaderMap) {
		String segmentGUID = ReadFromBufferUtils.getGUID(buffer, startIndex);
		int segmentType = buffer.getInt(startIndex + 16);
		int segmentLength = buffer.getInt(startIndex + 16 + 4);
		segmentHeaderMap.put(segmentGUID, new SegmentHeaderRecord(segmentGUID, segmentType, segmentLength));
		LOGGER.info("SegmentHeader type: {}", segmentHeaderMap.get(segmentGUID).segmentType());

		if (segmentHeaderMap.get(segmentGUID).segmentType() == 1) {
			LSGDataSegment lsgDataSegment = new LSGDataSegment(segmentHeaderMap.get(segmentGUID), buffer.duplicate(), startIndex, fileByteOrder);
		} else if (segmentHeaderMap.get(segmentGUID).segmentType() == 4) {
			MetaDataSegment metaDataSegment = new MetaDataSegment(segmentHeaderMap.get(segmentGUID), buffer.duplicate(), startIndex, fileByteOrder);; 
		}
	}
	
	 public static void findMagicNumber(MappedByteBuffer buffer, int startIndex) {
	        // Define the magic number sequence
	        byte[] magicNumber = {(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00};
	        int magicLength = magicNumber.length;

	        // Scan the buffer for the magic number
	        for (int i = Math.max(0, startIndex - 1000); i <= Math.min(buffer.limit() - magicLength, startIndex + 1000); i++) {
	            boolean found = true;
	            for (int j = 0; j < magicLength; j++) {
	                if (buffer.get(i + j) != magicNumber[j]) {
	                    found = false;
	                    break;
	                }
	            }
	            if (found) {
	                int difference = i - startIndex;
	                System.out.printf("Magic number found at index: %d%n", i);
	                System.out.printf("Difference to startIndex: %d%n", difference);
	                System.out.print("Magic number: ");
	                for (byte b : magicNumber) {
	                    System.out.printf("%02X ", b);
	                }
	                System.out.println();
	                return;
	            }
	        }
	        System.out.println("Magic number not found around the specified startIndex.");
	    }

	private void setFileHeaderRecordFromFile(MappedByteBuffer buffer) {
		String version = getVersionAndCheckForCorruption(buffer);
		fileByteOrder = buffer.get(80) == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		boolean fileByteOrderIsSystemByteOrder = fileByteOrder.equals(ByteOrder.nativeOrder());
		LOGGER.info(version);
		LOGGER.info("File Byte order is {}. Byte order matches Systems ByteOrder: {}", fileByteOrder, fileByteOrderIsSystemByteOrder);
		buffer.order(fileByteOrder);

		int emptyField = buffer.getInt(81);

		boolean version10OrUp = isVersion10OrUp(version);
		strippedVersionString = version.strip();

		int tocOffset = getTOCOffset(buffer, version10OrUp, writersThatWriteI32SegmentOffset.contains(version.strip()));

		// TODO: Research, do we really use the empty field as GUID if it is not 0? The format can not be the same as a I32 can not hold a valid GUID?
		String guid;
		if (emptyField == 0) {
		    guid = getGUID(buffer, 93);
		} else {
		    guid = String.valueOf(emptyField);
		}

		// Check if the tocOffsetUnsigned is within the range of an int
		if (tocOffset > buffer.capacity()) {
		    throw new IllegalArgumentException("TOC Offset is out of range of the file: " + tocOffset + " fileLength: " + buffer.capacity());
		}

		// Use the converted int value to fetch data
		int tocEntryCount = buffer.getInt(tocOffset);

		// Create the FileHeader record
		fileHeaderRecord = new FileHeaderRecord(
		    version,
		    fileByteOrder,
		    fileByteOrderIsSystemByteOrder,
		    emptyField,
		    version10OrUp,
		    tocOffset,
		    guid,
		    tocEntryCount
		);

		// Print the file header data
		LOGGER.info("FileHeaderRecord is: {}", fileHeaderRecord);
	}

	private int getTOCOffset(MappedByteBuffer buffer, boolean version10OrUp, boolean nobodySeemsToImplementTheReference) {
		int tocOffset = 0;
		if (nobodySeemsToImplementTheReference || !version10OrUp) {
			// I32 is used in versions before 10
			tocOffset = buffer.getInt(85);				
		} else {
			long tocOffsetLong = ReadFromBufferUtils.readUnsignedLong(buffer, 85);
			if (tocOffsetLong > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(String.format("\"TOC Offset is out of range of of an int: %s/%s", tocOffsetLong, Integer.MAX_VALUE));
			}
		}
		return tocOffset;
	}
	
	public static int[] generateTOCOffsets(int tocOffset, int numEntries) {
        // Starting point
        int startPoint = tocOffset + 4;

        // Size of each TOC entry
        int tocEntrySize = 16 + 8 + 4 + 4;  // 32 bytes

        // Create the array
        int[] tocOffsets = new int[numEntries];
        for (int i = 0; i < numEntries; i++) {
            tocOffsets[i] = startPoint + i * tocEntrySize;
        }

        return tocOffsets;
    }

	private boolean isVersion10OrUp(String version) {
		try {
		    String[] versionParts = version.split(" ");
		    if (versionParts.length > 1) {
		        float versionNumber = Float.parseFloat(versionParts[1]);
		        if (versionNumber >= 10) {
		            LOGGER.info("Version greater than or equal to 10. Using U64 for TOC Offset");
		            LOGGER.info("Please keep in mind that we currently only support files up to {} MiBytes ({} Megabytes)", Integer.MAX_VALUE / (1024 * 1024), Integer.MAX_VALUE / 1_000_000);
		            return true;
		        } else {
		            LOGGER.info("Version smaller than 10. Using I32 for TOC Offset");
		            return false;
		        }
		    } else {
		        LOGGER.warn("Version string does not contain a valid version number.");
		    }
		} catch (NumberFormatException e) {
		    LOGGER.error("Failed to parse version number from string: {}", version, e);
		}
		return false;
	}

	private String getGUID(MappedByteBuffer buffer, int guidStartIndex) {
		// Extract the GUID components
		int part1 = buffer.getInt(guidStartIndex);
		short part2 = buffer.getShort(guidStartIndex + 4);
		short part3 = buffer.getShort(guidStartIndex + 6);
		byte[] part4 = new byte[8];
		buffer.position(guidStartIndex + 8);
		buffer.get(part4);

		// Format the GUID as a string
		return String.format("{%08X-%04X-%04X-%02X-%02X-%02X-%02X-%02X-%02X-%02X-%02X}", part1, part2, part3,
				part4[0], part4[1], part4[2], part4[3], part4[4], part4[5], part4[6], part4[7]);
	}

	private static String getVersionAndCheckForCorruption(MappedByteBuffer buffer) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean corrupted = false;

		// Expected characters for the last 5 positions
		char[] expectedChars = { ' ', '\n', '\r', '\n', ' ' };

		for (int i = 0; i < Math.min(buffer.limit(), 80); i++) {
			char currentChar = (char) buffer.get(i);
			stringBuilder.append(currentChar);

			// Check the expected characters at specific indices
			if (i >= 75 && i <= 79) {
				corrupted = corrupted || currentChar != expectedChars[i - 75];
			}
		}
		if (corrupted) {
			LOGGER.warn("Final version characters deviate from definition. JTFile might be corrupted.");
		} else {
			LOGGER.info("Final version characters check out. JTFile is probably not corrupted");
		}
		return stringBuilder.toString();
	}
}
