package io.github.tomusin.voyager.datastructures;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;

import io.github.tomusin.lodElements.TriStripSetShapeLODElementRecord;
import io.github.tomusin.lsgElements.BaseNodeElementRecord;
import io.github.tomusin.lsgElements.GroupNodeElementRecord;
import io.github.tomusin.lsgElements.InstanceNodeElementRecord;
import io.github.tomusin.lsgElements.LODNodeElementRecord;
import io.github.tomusin.lsgElements.MaterialAttributeElementRecord;
import io.github.tomusin.lsgElements.MetaDataNodeElementRecord;
import io.github.tomusin.lsgElements.PartNodeElementRecord;
import io.github.tomusin.lsgElements.PartitionNodeElementRecord;
import io.github.tomusin.lsgElements.RangeLODNodeElementRecord;
import io.github.tomusin.lsgElements.TriStripSetShapeNodeElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.DatePropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.FloatingPointPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.IntegerPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.LateLoadedPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.StringPropertyAtomElementRecord;
import io.github.tomusin.voyager.metaDataElements.PropertyProxyMetaDataElementRecord;

public enum NodeElementType {
	PARTITION_NODE("{10dd103e-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", PartitionNodeElementRecord::fromByteBuffer),
	BASE_NODE_ELEMENT("{10dd1035-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", BaseNodeElementRecord::fromByteBuffer),
	GROUP_NODE_ELEMENT("{10dd101b-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", GroupNodeElementRecord::fromByteBuffer),
	INSTANCE_NODE_ELEMENT("{10dd102a-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", InstanceNodeElementRecord::fromByteBuffer),
	LOD_NODE_ELEMENT("{10dd102c-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", LODNodeElementRecord::fromByteBuffer),
	META_DATA_NODE_ELEMENT("{ce357245-38fb-11d1-a5-06-00-60-97-bd-c6-e1}", MetaDataNodeElementRecord::fromByteBuffer),
	PART_NODE_ELEMENT("{ce357244-38fb-11d1-a5-06-00-60-97-bd-c6-e1}", PartNodeElementRecord::fromByteBuffer),
	TRI_STRIP_SET_SHAPE_NODE_ELEMENT("{10dd1077-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", TriStripSetShapeNodeElementRecord::fromByteBuffer),
	STRING_PROPERTY_ATOM_ELEMENT("{10dd106e-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", StringPropertyAtomElementRecord::fromByteBuffer),
	RANGE_LOD_NODE_ELEMENT("{10dd104c-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", RangeLODNodeElementRecord::fromByteBuffer),
	MATERIAL_ATTRIBUTE_ELEMENT("{10dd1030-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", MaterialAttributeElementRecord::fromByteBuffer),
	//AtomProperty
	DATE_PROPERTY_ATOM_ELEMENT("{ce357246-38fb-11d1-a5-06-00-60-97-bd-c6-e1}", DatePropertyAtomElementRecord::fromByteBuffer),
	INTEGER_PROPERTY_ATOM_ELEMENT("{10dd102b-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", IntegerPropertyAtomElementRecord::fromByteBuffer),
	FLOATING_POINT_PROPERTY_ATOM_ELEMENT("{10dd1019-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", FloatingPointPropertyAtomElementRecord::fromByteBuffer),
	STRING_ATOM_PROPERTY_ELEMENT("{10dd106e-2ac8-11d1-9b-6b-00-80-c7-bb-59-97}", StringPropertyAtomElementRecord::fromByteBuffer),
	LATE_LOADED_PROPERTY_ATOM_ELEMENT("{e0b05be5-fbbd-11d1-a3-a7-00-aa-00-d1-09-54}", LateLoadedPropertyAtomElementRecord::fromByteBuffer),
	// MetaData-Segment
	PROPERTY_PROXY_META_DATA_ELEMENT("{ce357247-38fb-11d1-a5-06-00-60-97-bd-c6-e1}", PropertyProxyMetaDataElementRecord::fromByteBuffer),
	// LOD0 Segment
	TRI_STRIP_SET_SHAPE_LOD_ELEMENT("{10DD10AB-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", TriStripSetShapeLODElementRecord::fromByteBuffer);
	                              
    private final String guid;
    private final BiFunction<ByteBuffer, Integer, BufferDeserializable> deserializer;

    NodeElementType(String guid, BiFunction<ByteBuffer, Integer, BufferDeserializable> deserializer) {
        this.guid = guid.toLowerCase();
        this.deserializer = deserializer;
    }

    public BufferDeserializable deserialize(ByteBuffer buffer, int startIndex) {
        return deserializer.apply(buffer, startIndex);
    }

    public static Optional<NodeElementType> fromGuid(String guid) {
        return Arrays.stream(values())
            .filter(t -> t.guid.equalsIgnoreCase(guid))
            .findFirst();
    }
}

