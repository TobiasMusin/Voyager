package io.github.tomusin.lsgElements;

import java.nio.ByteBuffer;

import io.github.tomusin.lsgDataRecords.BaseNodeDataRecord;
import io.github.tomusin.lsgDataRecords.GroupNodeDataRecord;
import io.github.tomusin.voyager.datastructures.BBoxF32;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils.MbStringResult;

public record PartitionNodeElementRecord(
	    BaseNodeDataRecord baseNodeDataRecord,
	    GroupNodeDataRecord groupNodeDataRecord,
	    int partitionFlags,
	    String fileName,
	    BBoxF32 untransformedBBox,
	    float area,
	    int minVertexCount,
	    int maxVertexCount,
	    int minNodeCount,
	    int maxNodeCount,
	    int minPolygonCount,
	    int maxPolygonCount,
	    int jtEndIndex
	) implements BufferDeserializable {
	
    public static PartitionNodeElementRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
        // all the logic you already have
        BaseNodeDataRecord baseNodeDataRecord = ReadNodesFromBufferUtils.readBaseNodeData(startIndex, buffer);
        int groupNodeStart = baseNodeDataRecord.jtEndIndex();
        GroupNodeDataRecord groupNodeDataRecord = ReadNodesFromBufferUtils.readGroupNodeData(groupNodeStart, buffer, baseNodeDataRecord);
        int partitionFlagsStart = groupNodeDataRecord.jtEndIndex();
//        int partitionFlags = buffer.getInt(partitionFlagsStart); // Documentation wrong
        int partitionFlags = ReadFromBufferUtils.readUnsignedByte(buffer, partitionFlagsStart);
        int mbStringStart = partitionFlagsStart + 1;
        MbStringResult mbStringResult = ReadFromBufferUtils.readMbString(buffer, mbStringStart);
        String fileName = mbStringResult.value();
        int afterString = mbStringResult.nextIndex();
        BBoxF32 untransformedBBox = ReadFromBufferUtils.readBBoxF32(buffer, afterString);
        int areaIndex = afterString + 6 * 4;
        float area = ReadFromBufferUtils.readF32(buffer, areaIndex);
        int countsStart = areaIndex + 4;
        int minVertexCount = buffer.getInt(countsStart);
        int maxVertexCount = buffer.getInt(countsStart + 4);
        int minNodeCount = buffer.getInt(countsStart + 8);
        int maxNodeCount = buffer.getInt(countsStart + 12);
        int minPolygonCount = buffer.getInt(countsStart + 16);
        int maxPolygonCount = buffer.getInt(countsStart + 20);
        int jtEndIndex = countsStart + 24;

        return new PartitionNodeElementRecord(
            baseNodeDataRecord, groupNodeDataRecord, partitionFlags, fileName,
            untransformedBBox, area,
            minVertexCount, maxVertexCount,
            minNodeCount, maxNodeCount,
            minPolygonCount, maxPolygonCount,
            jtEndIndex
        );
    }
}

