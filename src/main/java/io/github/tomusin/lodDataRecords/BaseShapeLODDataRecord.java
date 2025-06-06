package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

// Page 92, Figure 86
public record BaseShapeLODDataRecord(byte versionNumber, int jtEndIndex) {
	public static BaseShapeLODDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
		return new BaseShapeLODDataRecord(buffer.get(startIndex), startIndex + 1); // @@DOCUMENTATION_ERROR@@ I8 in 10.6, I16 in 9.5
	}
}
