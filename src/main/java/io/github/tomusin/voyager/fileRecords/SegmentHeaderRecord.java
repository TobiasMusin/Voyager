package io.github.tomusin.voyager.fileRecords;

public record SegmentHeaderRecord(String guid, long segmentType, long segmentLength) {
}
