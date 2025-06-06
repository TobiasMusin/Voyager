package io.github.tomusin.voyager.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.SingleXZInputStream;

import io.github.tomusin.voyager.datastructures.BBoxF32;
import io.github.tomusin.voyager.datastructures.CoordF32;

public class ReadFromBufferUtils {

	public static String getGUID(ByteBuffer buffer, int guidStartIndex) {
	    int part1 = buffer.getInt(guidStartIndex);
	    short part2 = buffer.getShort(guidStartIndex + 4);
	    short part3 = buffer.getShort(guidStartIndex + 6);
	    byte[] part4 = new byte[8];

	    for (int i = 0; i < 8; i++) {
	        part4[i] = buffer.get(guidStartIndex + 8 + i);
	    }

	    return String.format("{%08X-%04X-%04X-%02X-%02X-%02X-%02X-%02X-%02X-%02X-%02X}",
	            part1, part2, part3,
	            part4[0], part4[1], part4[2], part4[3],
	            part4[4], part4[5], part4[6], part4[7]);
	}

	
	//-----------------------------------
	public static Set<String> formatGUIDs() {
		String[] guids = {
	            "0x10dd1035, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd101b, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd102a, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd102c, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xce357245, 0x38fb, 0x11d1, 0xa5, 0x06, 0x00, 0x60, 0x97, 0xbd, 0xc6, 0xe1",
	            "0xd239e7b6, 0xdd77, 0x4289, 0xa0, 0x7d, 0xb0, 0xee, 0x79, 0xf7, 0x94, 0x94",
	            "0xce357244, 0x38fb, 0x11d1, 0xa5, 0x06, 0x00, 0x60, 0x97, 0xbd, 0xc6, 0xe1",
	            "0x10dd103e, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd104c, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd10f3, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1059, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x98134716, 0x0010, 0x0818, 0x19, 0x98, 0x08, 0x00, 0x09, 0x83, 0x5d, 0x5a",
	            "0x10dd1048, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1046, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xe40373c1, 0x1ad9, 0x11d3, 0x9d, 0xaf, 0x00, 0xa0, 0xc9, 0xc7, 0xdd, 0xc2",
	            "0x10dd1077, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd107f, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1001, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1014, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1083, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1028, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1096, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd10c4, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1030, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1045, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x8d57c010, 0xe5cb, 0x11d4, 0x84, 0x0e, 0x00, 0xa0, 0xd2, 0x18, 0x2f, 0x9d",
	            "0x10dd1073, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xaa1b831d, 0x6e47, 0x4fee, 0xa8, 0x65, 0xcd, 0x7e, 0x1f, 0x2f, 0x39, 0xdc",
	            "0x10dd1106, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xa3cfb921, 0xbdeb, 0x48d7, 0xb3, 0x96, 0x8b, 0x8d, 0x0e, 0xf4, 0x85, 0xa0",
	            "0x3e70739d, 0x8cb0, 0x41ef, 0x84, 0x5c, 0xa1, 0x98, 0xd4, 0x00, 0x3b, 0x3f",
	            "0x72475fd1, 0x2823, 0x4219, 0xa0, 0x6c, 0xd9, 0xe6, 0xe3, 0x9a, 0x45, 0xc1",
	            "0x92f5b094, 0x6499, 0x4d2d, 0x92, 0xaa, 0x60, 0xd0, 0x5a, 0x44, 0x32, 0xcf",
	            "0x10dd104b, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xce357246, 0x38fb, 0x11d1, 0xa5, 0x06, 0x00, 0x60, 0x97, 0xbd, 0xc6, 0xe1",
	            "0x10dd102b, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd1019, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0xe0b05be5, 0xfbbd, 0x11d1, 0xa3, 0xa7, 0x00, 0xaa, 0x00, 0xd1, 0x09, 0x54",
	            "0x10dd1004, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            "0x10dd106e, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97",
	            // MetaData-Segment
	            "0xce357247, 0x38fb, 0x11d1, 0xa5, 0x06, 0x00, 0x60, 0x97, 0xbd, 0xc6, 0xe1",
	            // ShapeLOD0 Segment
	            "0x10dd10ab, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97"
	        };

        Set<String> formattedGUIDs = new HashSet<>();

        for (String guid : guids) {
            formattedGUIDs.add(formatGUID(guid));
        }

        return formattedGUIDs;
    }

    private static String formatGUID(String guid) {
        String[] parts = guid.split(", ");
        StringBuilder formattedGUID = new StringBuilder("{");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].replace("0x", "");
            formattedGUID.append(parts[i]);
            if (i < parts.length - 1) {
                formattedGUID.append("-");
            }
        }

        formattedGUID.append("}");
        return formattedGUID.toString();
    }
	
	//-----------------------------------
	public static long readUnsignedInt(ByteBuffer buffer, int startIndex) {
	    // Read the int value from the specified start index
	    int signedInt = buffer.getInt(startIndex);
	    
	    // Convert the signed int to an unsigned long
	    return signedInt & 0xFFFFFFFFL;
	}
	
	public static int readUnsignedShort(ByteBuffer buffer, int startIndex) {
	    // Read the int value from the specified start index
	    int signedInt = buffer.getShort(startIndex);
	    
	    // Convert the signed int to an unsigned long
	    return signedInt & 0xFFFF;
	}
	
	public static void printByteArrayAsHex(byte[] byteArray) {
	    StringBuilder hexString = new StringBuilder();
	    for (byte b : byteArray) {
	        hexString.append(String.format("%02X ", b));
	    }
	    System.out.println(hexString.toString());
	}
	
	public static int readUnsignedByte(ByteBuffer buffer, int startIndex) {
		// Read the byte value from the specified start index
		byte signedByte = buffer.get(startIndex);

		// Convert the signed byte to an unsigned int
		return signedByte & 0xFF;
	}
	
	public static byte[] decompressLZMA2FromBuffer(MappedByteBuffer buffer, int startIndex, int length, ByteOrder fileByteOrder) throws IOException {
	    // Create a duplicate of the buffer for thread-safe access
	    MappedByteBuffer duplicateBuffer = buffer.duplicate();
	    duplicateBuffer.order(fileByteOrder);

	    // Extract the specific part of the duplicate buffer
	    byte[] compressedData = new byte[length];
	    duplicateBuffer.position(startIndex);
	    duplicateBuffer.get(compressedData, 0, length);

	    // Decompress the data using SingleXZInputStream for LZMA2
	    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
	         SingleXZInputStream singleXZInputStream = new SingleXZInputStream(byteArrayInputStream);
	         ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream()) {

	        // Temporary buffer for reading chunks of decompressed data
	        byte[] tempBuffer = new byte[1024];
	        int bytesRead;
	        while ((bytesRead = singleXZInputStream.read(tempBuffer)) != -1) {
	            decompressedOutput.write(tempBuffer, 0, bytesRead);
	        }

	        // Return the decompressed data as a byte array
	        return decompressedOutput.toByteArray();
	    }
	}
	
	public static byte[] decompressLZMA2FromBufferWithoutSize(MappedByteBuffer buffer, int startIndex, ByteOrder fileByteOrder) throws IOException {
	    // Create a duplicate of the buffer for thread-safe access
	    MappedByteBuffer duplicateBuffer = buffer.duplicate();
	    duplicateBuffer.order(fileByteOrder);

	    // Set the position to the start index
	    duplicateBuffer.position(startIndex);

	    // Create an InputStream from the MappedByteBuffer
	    InputStream bufferInputStream = new InputStream() {
	        @Override
	        public int read() throws IOException {
	            if (!duplicateBuffer.hasRemaining()) {
	                return -1;
	            }
	            return duplicateBuffer.get() & 0xFF;
	        }

	        @Override
	        public int read(byte[] b, int off, int len) throws IOException {
	            if (!duplicateBuffer.hasRemaining()) {
	                return -1;
	            }
	            len = Math.min(len, duplicateBuffer.remaining());
	            duplicateBuffer.get(b, off, len);
	            return len;
	        }
	    };

	    // Decompress the data using SingleXZInputStream for LZMA2
	    try (SingleXZInputStream singleXZInputStream = new SingleXZInputStream(bufferInputStream);
	         ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream()) {

	        // Temporary buffer for reading chunks of decompressed data
	        byte[] tempBuffer = new byte[1024];
	        int bytesRead;
	        while ((bytesRead = singleXZInputStream.read(tempBuffer)) != -1) {
	            decompressedOutput.write(tempBuffer, 0, bytesRead);
	        }

	        // Check for the end of the stream to verify integrity
	        if (singleXZInputStream.read() != -1) {
	            throw new IOException("Unexpected data after the end of the XZ Stream");
	        }

	        // Return the decompressed data as a byte array
	        return decompressedOutput.toByteArray();
	    }
	}
	
    public static byte[] decompressLZMAFromBuffer(MappedByteBuffer buffer, int startIndex, int length) throws IOException {
        // Create a duplicate of the buffer for thread-safe access
        MappedByteBuffer duplicateBuffer = buffer.duplicate();

        // Extract the specific part of the duplicate buffer
        byte[] compressedData = new byte[length];
        duplicateBuffer.position(startIndex);
        duplicateBuffer.get(compressedData, 0, length);

        // Decompress the data
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
             LZMAInputStream lzmaInputStream = new LZMAInputStream(byteArrayInputStream);
             ByteArrayOutputStream decompressedOutput = new ByteArrayOutputStream()) {

            byte[] tempBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = lzmaInputStream.read(tempBuffer)) != -1) {
                decompressedOutput.write(tempBuffer, 0, bytesRead);
            }

            return decompressedOutput.toByteArray();
        }
    }
    
	public static long readUnsignedLong(ByteBuffer buffer, int startIndex) {
        // Read the long value from the specified start index
        long signedLong = buffer.getLong(startIndex);
        
        // Convert the signed long to an unsigned long
        return signedLong & 0xFFFFFFFFFFFFFFFFL;
    }
	
	public static record MbStringResult(String value, int nextIndex) {}

    public static MbStringResult readMbString(ByteBuffer buffer, int startIndex) {
        int numChars = buffer.getInt(startIndex); // 4 bytes
        int stringStartIndex = startIndex + 4;
        int byteLength = numChars * 2;

        byte[] utf16Bytes = new byte[byteLength];
        // Copy without modifying buffer's main position
        for (int i = 0; i < byteLength; i++) {
            utf16Bytes[i] = buffer.get(stringStartIndex + i);
        }

        String decoded = new String(utf16Bytes, StandardCharsets.UTF_16LE);
        int nextIndex = stringStartIndex + byteLength;

        return new MbStringResult(decoded, nextIndex);
    }
	
	public static float readF32(ByteBuffer buffer, int startIndex) {
        return buffer.getFloat(startIndex); // Absolute read = thread-safe
    }

    public static CoordF32 readCoordF32(ByteBuffer buffer, int startIndex) {
        float x = buffer.getFloat(startIndex);
        float y = buffer.getFloat(startIndex + 4);
        float z = buffer.getFloat(startIndex + 8);
        return new CoordF32(x, y, z);
    }

    public static BBoxF32 readBBoxF32(ByteBuffer buffer, int startIndex) {
        CoordF32 min = readCoordF32(buffer, startIndex);
        CoordF32 max = readCoordF32(buffer, startIndex + 12);
        return new BBoxF32(min, max);
    }
}
