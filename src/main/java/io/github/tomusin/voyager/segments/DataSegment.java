package io.github.tomusin.voyager.segments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.readers.JTReader;

public class DataSegment {
	private SegmentHeaderRecord segmentHeaderRecord;
	
	public DataSegment(SegmentHeaderRecord segmentHeaderRecord) {
		this.segmentHeaderRecord = segmentHeaderRecord;
	}
}
