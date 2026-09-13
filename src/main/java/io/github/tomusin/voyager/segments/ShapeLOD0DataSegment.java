package io.github.tomusin.voyager.segments;

import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.datastructures.NodeElementType;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.utils.BitByteBuffer;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;


public class ShapeLOD0DataSegment extends DataSegment {
	
	ByteOrder fileByteOrder;

	/** Parsed elements indexed by their object ID */
	private final Map<Integer, BufferDeserializable> elementsByObjectID = new HashMap<>();

	public Map<Integer, BufferDeserializable> getElementsByObjectID() { return elementsByObjectID; }

	public ShapeLOD0DataSegment(SegmentHeaderRecord segmentHeaderRecord, MappedByteBuffer buffer, int segmentStartIndex, ByteOrder fileByteOrder) {
		super(segmentHeaderRecord);
		this.fileByteOrder = fileByteOrder;
		buffer.order(fileByteOrder);

		Logger.info("ShapeLOD0DataSegment: segmentStartIndex={}", segmentStartIndex);

		try {
			Set<String> validGUIDS = ReadFromBufferUtils.formatGUIDs();

			BitByteBuffer elementBuffer = new BitByteBuffer(buffer);
			int elementBufferStartIndex = segmentStartIndex + 16 + 4 + 4;

			Map<Integer, LogicalElementHeaderRecord> logicalElementHeaderRecordMap = new HashMap<>();
			int startIndex = elementBufferStartIndex;
			while (startIndex < elementBuffer.capacity() - 25) {
				LogicalElementHeaderRecord logicalElementHeaderRecord =
						ReadNodesFromBufferUtils.readLogicalElementHeader(elementBuffer, startIndex);
				if (!"{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())) {
					logicalElementHeaderRecordMap.put(logicalElementHeaderRecord.objectID(), logicalElementHeaderRecord);
					int advance = logicalElementHeaderRecord.elementLength() + 4;
					if (advance <= 0) { Logger.warn("ShapeLOD0: elementLength<=0, stopping loop"); break; }
					startIndex += advance;
				} else {
					break;
				}
			}
			Logger.info("ShapeLOD0DataSegment: found {} logical elements", logicalElementHeaderRecordMap.size());

			for (LogicalElementHeaderRecord logicalElementHeaderRecord : logicalElementHeaderRecordMap.values()) {
				if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
					NodeElementType.fromGuid(logicalElementHeaderRecord.objectTypeID())
							.ifPresent(nodeElementType -> {
								BufferDeserializable obj = nodeElementType.deserialize(
										elementBuffer,
										logicalElementHeaderRecord.jtEndIndex());
								elementsByObjectID.put(logicalElementHeaderRecord.objectID(), obj);
							});
				}
			}
		} catch (Exception e) {
			Logger.error(e, "ShapeLOD0DataSegment: Exception during parsing: {}", e.getMessage());
		}
	}
}