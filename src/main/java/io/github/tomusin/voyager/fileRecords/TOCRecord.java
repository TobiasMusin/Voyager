package io.github.tomusin.voyager.fileRecords;

// long segmentOffset is U64
// long segmentLength is U32
// long segmentAttributes is U32
public record TOCRecord(String guid, long segmentOffset, long segmentLength, long segmentAttributes) {
	
	public long getSegmentType() {
		return (segmentAttributes >>> 24) & 0xFF;
	}

}
