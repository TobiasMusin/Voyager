package io.github.tomusin.voyager.segments;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.tinylog.Logger;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.datastructures.NodeElementType;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;


public class ShapeLOD0DataSegment  extends DataSegment  {
	
	public ShapeLOD0DataSegment(SegmentHeaderRecord segmentHeaderRecord, MappedByteBuffer buffer, int segmentStartIndex, ByteOrder fileByteOrder) {
		super(segmentHeaderRecord);
		this.fileByteOrder = fileByteOrder;
		buffer.order(fileByteOrder);
		// We have a LSG with Logical Element Header Compressed
		Logger.info("ShapeLOD0DataSegment GUID is: {}", segmentHeaderRecord.guid());
		Logger.info("LSGClassStartIndex: {}", segmentStartIndex + 16 + 4 + 4);
		
//		// ShapeLOD0DataSegment does not have compression -> LogicalElementHeader instead of LogicalElementHeaderCompressed
//		LogicalElementHeaderRecord logicalElementHeader = ReadNodesFromBufferUtils.readLogicalElementHeader(buffer, segmentStartIndex);
//		Logger.info("logicalElementHEader of ShapeLOD0 is: {}", logicalElementHeader);
		
		try {
			Set<String> validGUIDS = ReadFromBufferUtils.formatGUIDs();

			Map<Integer ,LogicalElementHeaderRecord> logicalElementHeaderRecordMap = new HashMap<>();
			
			int startIndex = segmentStartIndex + 16 + 4 + 4;
			Set<String> guidsUsed = new HashSet<>();
			while (startIndex < segmentStartIndex + segmentHeaderRecord.segmentLength()) {
				LogicalElementHeaderRecord logicalElementHeaderRecord = ReadNodesFromBufferUtils.readLogicalElementHeader(buffer, startIndex);
				if (!"{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())){
					Logger.info("logicalElementHeaderRecord is: {}", logicalElementHeaderRecord);
					logicalElementHeaderRecordMap.put(logicalElementHeaderRecord.objectID(), logicalElementHeaderRecord);
					startIndex += logicalElementHeaderRecord.elementLength() + 4;
					if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
						Logger.info("GUID is correct: {}", logicalElementHeaderRecord.objectTypeID());
						guidsUsed.add(logicalElementHeaderRecord.objectTypeID().toLowerCase());
					} 
				} else if ("{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())) {
					Logger.info("Found identifier to signal End-Of-Elements");
					break;
				}
			}
			
			for (LogicalElementHeaderRecord logicalElementHeaderRecord : logicalElementHeaderRecordMap.values()) {
				Logger.info("LogicalElementHeader objectID is: {}", logicalElementHeaderRecord.objectID());
				if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
					NodeElementType.fromGuid(logicalElementHeaderRecord.objectTypeID())
							.ifPresentOrElse(nodeElementType -> {
								BufferDeserializable obj = nodeElementType.deserialize(buffer,
										logicalElementHeaderRecord.jtEndIndex());
								Logger.info("Deserialized object is: {}", obj);
							}, () -> Logger.warn("Unknown objectTypeID: {}",
									logicalElementHeaderRecord.objectTypeID()));
				} else {
					Logger.info("GUID not found: {}", logicalElementHeaderRecord.objectTypeID());
					Logger.info("Skipping element, next GUID: ");
				}
			}
		} catch (Exception e) {
			
		}
	}
	ByteOrder fileByteOrder;
	
}
