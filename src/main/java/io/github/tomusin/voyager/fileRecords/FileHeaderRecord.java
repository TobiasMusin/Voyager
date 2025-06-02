package io.github.tomusin.voyager.fileRecords;

import java.nio.ByteOrder;

public record FileHeaderRecord(
	    String version,
	    ByteOrder fileByteOrder,
	    boolean fileByteOrderIsSystemByteOrder,
	    int emptyField,
	    boolean version10OrUp,
	    int tocOffset,
	    String guid,
	    int tocEntryCount
	) {
    @Override
    public String toString() {
        return String.format(
            "FileHeaderRecord[version=%s, fileByteOrder=%s, fileByteOrderIsSystemByteOrder=%s, emptyField=%d, version10OrUp=%s, tocOffset=%d, guid=%s, tocEntryCount=%d]",
            version.replace("\n\r\n", "").trim(), fileByteOrder, fileByteOrderIsSystemByteOrder, emptyField, version10OrUp, tocOffset, guid, tocEntryCount
        );
    }
}
