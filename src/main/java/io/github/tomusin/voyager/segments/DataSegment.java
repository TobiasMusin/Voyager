package io.github.tomusin.voyager.segments;

import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;

public class DataSegment {
	private SegmentHeaderRecord segmentHeaderRecord;
	
	public DataSegment(SegmentHeaderRecord segmentHeaderRecord) {
		this.segmentHeaderRecord = segmentHeaderRecord;
	}

	public SegmentHeaderRecord getSegmentHeaderRecord() {
		return segmentHeaderRecord;
	}
}
