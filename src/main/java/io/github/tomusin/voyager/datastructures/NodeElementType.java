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
import io.github.tomusin.voyager.utils.BitByteBuffer;

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
	TRI_STRIP_SET_SHAPE_LOD_ELEMENT("{10DD10AB-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", TriStripSetShapeLODElementRecord::fromByteBuffer),
	// Additional node types (stubs — parsed as BaseNode or GroupNode until full deserializers are added)
	SWITCH_NODE_ELEMENT("{10DD1046-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", GroupNodeElementRecord::fromByteBuffer),
	TRANSFORM_NODE_ELEMENT("{10DD10C4-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", GroupNodeElementRecord::fromByteBuffer),
	LINE_STRIP_SET_SHAPE_NODE_ELEMENT("{10DD1048-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	POINT_SET_SHAPE_NODE_ELEMENT("{10DD107F-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	POLYGON_SET_SHAPE_NODE_ELEMENT("{10DD1059-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	LIGHT_NODE_ELEMENT("{10DD10F3-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	// JT 10DD100x series stubs
	STUB_10DD1001("{10DD1001-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1004("{10DD1004-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1014("{10DD1014-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1028("{10DD1028-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1045("{10DD1045-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD104B("{10DD104B-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1073("{10DD1073-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1083("{10DD1083-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1096("{10DD1096-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	STUB_10DD1106("{10DD1106-2AC8-11D1-9B-6B-00-80-C7-BB-59-97}", BaseNodeElementRecord::fromByteBuffer),
	// Non-10DD1xxx GUIDs stubs
	STUB_E40373C1("{E40373C1-1AD9-11D3-9D-AF-00-A0-C9-C7-DD-C2}", BaseNodeElementRecord::fromByteBuffer),
	STUB_8D57C010("{8D57C010-E5CB-11D4-84-0E-00-A0-D2-18-2F-9D}", BaseNodeElementRecord::fromByteBuffer),
	STUB_AA1B831D("{AA1B831D-6E47-4FEE-A8-65-CD-7E-1F-2F-39-DC}", BaseNodeElementRecord::fromByteBuffer),
	STUB_A3CFB921("{A3CFB921-BDEB-48D7-B3-96-8B-8D-0E-F4-85-A0}", BaseNodeElementRecord::fromByteBuffer),
	STUB_3E70739D("{3E70739D-8CB0-41EF-84-5C-A1-98-D4-00-3B-3F}", BaseNodeElementRecord::fromByteBuffer),
	STUB_72475FD1("{72475FD1-2823-4219-A0-6C-D9-E6-E3-9A-45-C1}", BaseNodeElementRecord::fromByteBuffer),
	STUB_92F5B094("{92F5B094-6499-4D2D-92-AA-60-D0-5A-44-32-CF}", BaseNodeElementRecord::fromByteBuffer),
	STUB_D239E7B6("{D239E7B6-DD77-4289-A0-7D-B0-EE-79-F7-94-94}", BaseNodeElementRecord::fromByteBuffer),
	// Siemens proprietary node (no-op: just parse base node header and stop)
	SIEMENS_PROPRIETARY_NODE("{98134716-0010-0818-19-98-08-00-09-83-5D-5A}", BaseNodeElementRecord::fromByteBuffer);
	                              
    private final String guid;
    private final BiFunction<BitByteBuffer, Integer, BufferDeserializable> deserializer;

    NodeElementType(String guid, BiFunction<BitByteBuffer, Integer, BufferDeserializable> deserializer) {
        this.guid = guid.toLowerCase();
        this.deserializer = deserializer;
    }

    public BufferDeserializable deserialize(BitByteBuffer buffer, int startIndex) {
        return deserializer.apply(buffer, startIndex);
    }

    public static Optional<NodeElementType> fromGuid(String guid) {
        return Arrays.stream(values())
            .filter(t -> t.guid.equalsIgnoreCase(guid))
            .findFirst();
    }
}

