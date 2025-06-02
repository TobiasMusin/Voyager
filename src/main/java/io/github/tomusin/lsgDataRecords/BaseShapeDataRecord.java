package io.github.tomusin.lsgDataRecords;

import io.github.tomusin.voyager.datastructures.BBoxF32;
import io.github.tomusin.voyager.datastructures.NodeCountRangeRecord;
import io.github.tomusin.voyager.datastructures.PolygonCountRangeRecord;
import io.github.tomusin.voyager.datastructures.VertexCountRangeRecord;

public record BaseShapeDataRecord(
		BaseNodeDataRecord baseNodeDataRecord,
		int versionNumber,
		BBoxF32 bboxF32,
		float area,
		VertexCountRangeRecord vertexCountRange,
		NodeCountRangeRecord nodeCountRange,
		PolygonCountRangeRecord polygonCountRange,
		long size,
		float compressionLevel,
		int jtEndIndex
		) {

}
