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
		
		try {
			Set<String> validGUIDS = ReadFromBufferUtils.formatGUIDs();

			Map<Integer, LogicalElementHeaderRecord> logicalElementHeaderRecordMap = new HashMap<>();
			
			int startIndex = segmentStartIndex + 16 + 4 + 4;
			while (startIndex < segmentStartIndex + segmentHeaderRecord.segmentLength()) {
				LogicalElementHeaderRecord logicalElementHeaderRecord = ReadNodesFromBufferUtils.readLogicalElementHeader(buffer, startIndex);
				if (!"{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())){
					logicalElementHeaderRecordMap.put(logicalElementHeaderRecord.objectID(), logicalElementHeaderRecord);
					startIndex += logicalElementHeaderRecord.elementLength() + 4;
				} else {
					break;
				}
			}
			
			for (LogicalElementHeaderRecord logicalElementHeaderRecord : logicalElementHeaderRecordMap.values()) {
				if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
					NodeElementType.fromGuid(logicalElementHeaderRecord.objectTypeID())
							.ifPresent(nodeElementType -> {
								BufferDeserializable obj = nodeElementType.deserialize(new BitByteBuffer(buffer),
										logicalElementHeaderRecord.jtEndIndex());
								elementsByObjectID.put(logicalElementHeaderRecord.objectID(), obj);
							});
				}
			}
		} catch (Exception e) {
			Logger.error("ShapeLOD0DataSegment: Exception during parsing: {}", e.getMessage());
			e.printStackTrace();
		}
	}
}