package io.github.tomusin.voyager.segments;

import java.io.IOException;
import java.nio.ByteBuffer;
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

			// Read Data Compression Block header (same layout as LSGDataSegment)
			long compressionFlag = ReadFromBufferUtils.readUnsignedInt(buffer, segmentStartIndex + 16 + 4 + 4);
			int compressedDataLength = buffer.getInt(segmentStartIndex + 16 + 4 + 4 + 4);
			int compressionAlgorithm = ReadFromBufferUtils.readUnsignedByte(buffer, segmentStartIndex + 16 + 4 + 4 + 4 + 4);
			Logger.info("ShapeLOD0DataSegment: compressionFlag={}, compressedDataLength={}, compressionAlgorithm={}",
					compressionFlag, compressedDataLength, compressionAlgorithm);

			final BitByteBuffer elementBuffer;
			final int elementBufferStartIndex;

			if (compressionFlag == 3 && compressionAlgorithm == 3) {
				// LZMA2 compressed — decompress into fresh buffer, same as LSGDataSegment
				try {
					byte[] decompressed = ReadFromBufferUtils.decompressLZMA2FromBuffer(
							buffer,
							segmentStartIndex + 16 + 4 + 4 + 4 + 4 + 1,
							compressedDataLength - 1,
							fileByteOrder);
					elementBuffer = new BitByteBuffer(ByteBuffer.wrap(decompressed).order(fileByteOrder));
					elementBufferStartIndex = 0;
					Logger.info("ShapeLOD0DataSegment: decompressed {} bytes", decompressed.length);
				} catch (IOException e) {
					Logger.error("ShapeLOD0DataSegment: LZMA2 decompression failed: {}", e.getMessage());
					return;
				}
			} else {
				// Uncompressed — skip compression-block header (compressionFlag field only, 4 bytes)
				// Data starts right after the 4-byte compressionFlag field
				elementBuffer = new BitByteBuffer(buffer);
				elementBufferStartIndex = segmentStartIndex + 16 + 4 + 4 + 4;
				Logger.info("ShapeLOD0DataSegment: uncompressed, elementBufferStartIndex={}", elementBufferStartIndex);
			}

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