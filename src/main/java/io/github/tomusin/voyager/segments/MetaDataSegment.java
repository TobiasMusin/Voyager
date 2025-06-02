package io.github.tomusin.voyager.segments;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.datastructures.NodeElementType;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public class MetaDataSegment extends DataSegment {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataSegment.class);
	ByteOrder fileByteOrder;
	
	public MetaDataSegment(SegmentHeaderRecord segmentHeaderRecord, MappedByteBuffer buffer, int segmentStartIndex, ByteOrder fileByteOrder) {
		super(segmentHeaderRecord);
		this.fileByteOrder = fileByteOrder;
		buffer.order(fileByteOrder);
		// We have a LSG with Logical Element Header Compressed
		LOGGER.info("LSGClassStartIndex: {}", segmentStartIndex + 16 + 4 + 4);
		long compressionFlag = ReadFromBufferUtils.readUnsignedInt(buffer, segmentStartIndex + 16 + 4 + 4);
		int compressedDataLength = buffer.getInt(segmentStartIndex + 16 + 4 + 4 + 4);
		int compressionAlgorithm = ReadFromBufferUtils.readUnsignedByte(buffer, segmentStartIndex + 16 + 4 + 4 + 4 + 4);
		LOGGER.info("CompressionFlag: {}, compressedDataLength: {}, compressionAlgorithm: {}", compressionFlag,	compressedDataLength, compressionAlgorithm);

		if (compressionFlag == 3 && compressionAlgorithm == 3) {
			try {
				Set<String> validGUIDS = ReadFromBufferUtils.formatGUIDs();
				
				byte[] decompressedLSGSegment = ReadFromBufferUtils.decompressLZMA2FromBuffer(buffer, segmentStartIndex + 16 + 4 + 4 + 4 + 4 + 1, compressedDataLength - 1, fileByteOrder);

				ByteBuffer decompressedLSGSegmentBuffer = ByteBuffer.wrap(decompressedLSGSegment).order(fileByteOrder);
				Map<Integer ,LogicalElementHeaderRecord> logicalElementHeaderRecordMap = new HashMap<>();
				
				int startIndex = 0;
				Set<String> guidsUsed = new HashSet<>();
				while (startIndex < decompressedLSGSegmentBuffer.capacity()) {
					LogicalElementHeaderRecord logicalElementHeaderRecord = ReadNodesFromBufferUtils.readLogicalElementHeader(decompressedLSGSegmentBuffer, startIndex);
					if (!"{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())){
						LOGGER.info("logicalElementHeaderRecord is: {}", logicalElementHeaderRecord);
						logicalElementHeaderRecordMap.put(logicalElementHeaderRecord.objectID(), logicalElementHeaderRecord);
						startIndex += logicalElementHeaderRecord.elementLength() + 4;
						if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
							LOGGER.info("GUID is correct: {}", logicalElementHeaderRecord.objectTypeID());
							guidsUsed.add(logicalElementHeaderRecord.objectTypeID().toLowerCase());
						} 
					} else if ("{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}".equals(logicalElementHeaderRecord.objectTypeID())) {
						LOGGER.info("Found identifier to signal End-Of-Elements");
						break;
					}
				}
				
				for (LogicalElementHeaderRecord logicalElementHeaderRecord : logicalElementHeaderRecordMap.values()) {
					LOGGER.info("LogicalElementHeader objectID is: {}", logicalElementHeaderRecord.objectID());
					if (validGUIDS.contains(logicalElementHeaderRecord.objectTypeID().toLowerCase())) {
						NodeElementType.fromGuid(logicalElementHeaderRecord.objectTypeID())
								.ifPresentOrElse(nodeElementType -> {
									BufferDeserializable obj = nodeElementType.deserialize(decompressedLSGSegmentBuffer,
											logicalElementHeaderRecord.jtEndIndex());
									LOGGER.info("Deserialized object is: {}", obj);
								}, () -> LOGGER.warn("Unknown objectTypeID: {}",
										logicalElementHeaderRecord.objectTypeID()));
					} else {
						LOGGER.info("GUID not found: {}", logicalElementHeaderRecord.objectTypeID());
						LOGGER.info("Skipping element, next GUID: ");
					}
				}
			} catch (Exception e) {
				
			}
		}
	}
}
