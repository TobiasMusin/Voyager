package io.github.tomusin.lsgDataRecords;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public record BaseNodeDataRecord(
	    int versionNumber,
	    long nodeFlags,
	    int attributeCount,
	    Set<Integer> attributeObjectIds,
	    int jtStartIndex,
	    int jtEndIndex
	) implements BufferDeserializable {

    public static BaseNodeDataRecord fromByteBuffer(ByteBuffer buffer, int startIndex) {
        int attributeAmount = buffer.getInt(startIndex + 1 + 4);
        Set<Integer> attributeIDSet = new HashSet<>();
        for (int i = 0; i < attributeAmount; i++) {
            attributeIDSet.add(buffer.getInt(startIndex + 1 + 4 + 4 + i * 4));
        }
        return new BaseNodeDataRecord(
            ReadFromBufferUtils.readUnsignedByte(buffer, startIndex),
            ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 1),
            attributeAmount,
            attributeIDSet,
            startIndex,
            startIndex + 1 + 4 + 4 + attributeAmount * 4
        );
    }

    @Override
    public int jtEndIndex() {
        return jtEndIndex;
    }
}

