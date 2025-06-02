package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.lsgDataRecords.GroupNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.segments.LSGDataSegment;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public record GroupNodeElementRecord(
		GroupNodeDataRecord grouNodeDataRecord,
	    int jtEndIndex
	) implements BufferDeserializable {

    public static GroupNodeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
    	BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
		GroupNodeDataRecord groupNodeDataRecord = ReadNodesFromBufferUtils.readGroupNodeData(baseNodeDataRecord.jtEndIndex(), buffer, baseNodeDataRecord);
		int jtEndIndex = groupNodeDataRecord.jtEndIndex();
		return new GroupNodeElementRecord(groupNodeDataRecord, jtEndIndex);
    }

    @Override
    public int jtEndIndex() {
        return jtEndIndex;
    }
}