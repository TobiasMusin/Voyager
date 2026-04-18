package io.github.tomusin.lodDataRecords;

import java.nio.ByteBuffer;

import io.github.tomusin.voyager.utils.BitByteBuffer;

// Page 92, Figure 86
public record BaseShapeLODDataRecord(byte versionNumber, int jtEndIndex) {
	public static BaseShapeLODDataRecord fromByteBuffer(BitByteBuffer buffer, int startIndex) {
		byte version = buffer.get(startIndex);
		// CRITICAL FIX: Documentation error - the mysterious "+5" was causing -5 byte offset misalignment
		// BaseShapeLODDataRecord should only contain the version byte (1 byte total, not 6)
		// The "+5" was leftover incorrect code that skipped bytes without reading them
		org.tinylog.Logger.info("BaseShapeLODDataRecord.fromByteBuffer: startIndex={}, version={}, consuming 1 byte only", startIndex, version);
		return new BaseShapeLODDataRecord(version, startIndex + 1);  // FIXED: was startIndex + 1 + 5
	}
}
