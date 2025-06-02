package io.github.tomusin.voyager.fileRecords;

// long segmentOffset is U64
// long segmentLength is U32
// long segmentAttributes is U32
public record TOCRecord(String guid, long segmentOffset, long segmentLength, long segmentAttributes) {
	
	public long getSegmentType() {
		return (segmentAttributes >>> 24) & 0xFF;
	}
	
	// TODO: This is probably BS - AI Hallucinated, could not find it in the documentation. there 0-14 is reserved for future use
	public long getCompressionStatus() {
		return segmentAttributes & 0xFF;
	}
	public long getlogicalSceneGraphPresence() {
		return (segmentAttributes >>> 8) & 0xFF;
	}

}
