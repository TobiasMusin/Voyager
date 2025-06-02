package io.github.tomusin.voyager.segments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.tomusin.lsgElements.GroupNodeElementRecord;
import io.github.tomusin.lsgElements.MaterialAttributeElementRecord;
import io.github.tomusin.lsgElements.MetaDataNodeElementRecord;
import io.github.tomusin.lsgElements.PartNodeElementRecord;
import io.github.tomusin.lsgElements.PartitionNodeElementRecord;
import io.github.tomusin.lsgElements.RangeLODNodeElementRecord;
import io.github.tomusin.lsgElements.TriStripSetShapeNodeElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.FloatingPointPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.IntegerPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.LateLoadedPropertyAtomElementRecord;
import io.github.tomusin.lsgElements.PropertyAtomElements.StringPropertyAtomElementRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.ElementPropertyTableRecord;
import io.github.tomusin.voyager.datastructures.LogicalElementHeaderRecord;
import io.github.tomusin.voyager.datastructures.NodeElementType;
import io.github.tomusin.voyager.datastructures.PropertyTableRecord;
import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;
import io.github.tomusin.voyager.utils.ReadNodesFromBufferUtils;

public class LSGDataSegment extends DataSegment{
	
	private static final Set<Integer> EMPTY_INT_SET = Set.of();
	private static final String END_OF_ELEMENTS_SIGNIFIER = "{FFFFFFFF-FFFF-FFFF-FF-FF-FF-FF-FF-FF-FF-FF}";
	private static final Logger LOGGER = LoggerFactory.getLogger(LSGDataSegment.class);
	ByteOrder fileByteOrder;

	public LSGDataSegment(SegmentHeaderRecord segmentHeaderRecord, MappedByteBuffer buffer, int segmentStartIndex, ByteOrder fileByteOrder) {
		super(segmentHeaderRecord);
		long startTime = System.nanoTime();
		this.fileByteOrder = fileByteOrder;
		buffer.order(fileByteOrder);
		// We have a LSG with Logical Element Header Compressed
		LOGGER.info("LSGClassStartIndex: {}", segmentStartIndex + 16 + 4 + 4);
		long compressionFlag = ReadFromBufferUtils.readUnsignedInt(buffer, segmentStartIndex + 16 + 4 + 4);
		int compressedDataLength = buffer.getInt(segmentStartIndex + 16 + 4 + 4 + 4);
		int compressionAlgorithm = ReadFromBufferUtils.readUnsignedByte(buffer, segmentStartIndex + 16 + 4 + 4 + 4 + 4);
		LOGGER.info("Length in segment header: {}", segmentHeaderRecord.segmentLength());
		LOGGER.info("CompressionFlag: {}, compressedDataLength: {}, compressionAlgorithm: {}", compressionFlag,	compressedDataLength, compressionAlgorithm);

		if (compressionFlag == 3 && compressionAlgorithm == 3) {
			try {
				Set<String> validGUIDS = ReadFromBufferUtils.formatGUIDs();
				
				byte[] decompressedLSGSegment = ReadFromBufferUtils.decompressLZMA2FromBuffer(buffer, segmentStartIndex + 16 + 4 + 4 + 4 + 4 + 1, compressedDataLength - 1, fileByteOrder);
				long endTimeUntilDecompressed = System.nanoTime();
				LOGGER.info("Time until decompressed: {}", (endTimeUntilDecompressed - startTime)/ 1_000_000);
				ByteBuffer decompressedLSGSegmentBuffer = ByteBuffer.wrap(decompressedLSGSegment).order(fileByteOrder);
				Map<Integer ,LogicalElementHeaderRecord> logicalElementHeaderRecordMap = new HashMap<>();
				
				int startIndex = 0;
				Set<String> guidsUsed = new HashSet<>();
				String lastGUID = "";
				while (startIndex < decompressedLSGSegmentBuffer.capacity()) {
					LogicalElementHeaderRecord logicalElementHeaderRecord = ReadNodesFromBufferUtils.readLogicalElementHeader(decompressedLSGSegmentBuffer, startIndex);
					if (!END_OF_ELEMENTS_SIGNIFIER.equals(logicalElementHeaderRecord.objectTypeID())){
						String objectTypeID = logicalElementHeaderRecord.objectTypeID();
						if (validGUIDS.stream().anyMatch(id -> id.equalsIgnoreCase(objectTypeID))) {
							lastGUID = logicalElementHeaderRecord.objectTypeID();
							logicalElementHeaderRecordMap.put(logicalElementHeaderRecord.objectID(), logicalElementHeaderRecord);
							startIndex += logicalElementHeaderRecord.elementLength() + 4;
							guidsUsed.add(logicalElementHeaderRecord.objectTypeID().toLowerCase());
						} else if (END_OF_ELEMENTS_SIGNIFIER.equals(lastGUID)) { // Unfortunately, there can be several End-Of-Elements signifier. Thats's why I need this workaround
							lastGUID = "";
							if (LOGGER.isInfoEnabled()) {
								LOGGER.info("Unknown GUID: {}", logicalElementHeaderRecord.objectTypeID());								
							}
							break;
						} else {
							// Just skip the unknown element
							startIndex += logicalElementHeaderRecord.elementLength() + 4;
						}
					} else if (END_OF_ELEMENTS_SIGNIFIER.equals(logicalElementHeaderRecord.objectTypeID())) { // Do not break here, there can be several End-Of-Elements signifier
						LOGGER.info("Found identifier to signal End-Of-Elements");
						lastGUID = logicalElementHeaderRecord.objectTypeID();
						startIndex += logicalElementHeaderRecord.elementLength() + 4;
					}
				}
				long timeUntillogicalElementHeaderRecordMapIsBuilt = System.nanoTime();
				LOGGER.info("Time until timeUntillogicalElementHeaderRecordMapIsBuilt: {}", (timeUntillogicalElementHeaderRecordMapIsBuilt - startTime)/ 1_000_000);
				LOGGER.info("Time for timeUntillogicalElementHeaderRecordMapIsBuilt: {}", (timeUntillogicalElementHeaderRecordMapIsBuilt - endTimeUntilDecompressed)/ 1_000_000);
				LOGGER.info("StartIndex is now: {}, capacity is: {}", startIndex, decompressedLSGSegmentBuffer.capacity());

				PropertyTableRecord propertyTableRecord = PropertyTableRecord.fromByteBuffer(decompressedLSGSegmentBuffer, startIndex);
				LOGGER.info("PropertyTableRecord is: {}", propertyTableRecord);
				
				Map<Integer, TreeNode> treeNodeMap = new HashMap<>();
				Set<Integer> childObjectIDs = new HashSet<>();
				Map<LogicalElementHeaderRecord, BufferDeserializable> elementMap = new ConcurrentHashMap<>();
				
				logicalElementHeaderRecordMap.values().stream() // seems to be faster than parallelStream in this case, at least for small files
			    .map(header -> Map.entry(header, NodeElementType.fromGuid(header.objectTypeID())))
			    .forEach(entry -> {
			        LogicalElementHeaderRecord header = entry.getKey();
			        Optional<NodeElementType> optType = entry.getValue();
			        if (optType.isPresent()) {
			            elementMap.put(header, optType.get().deserialize(decompressedLSGSegmentBuffer, header.jtEndIndex()));
			        } else {
			            // Optional logging
			            LOGGER.warn("Unknown NodeElementType for objectTypeID: {}", header.objectTypeID());
			        }
			    });
				
				long timeUntilElementMapIsBuilt = System.nanoTime();
				LOGGER.info("Time until timeUntilElementMapIsBuilt: {}", (timeUntilElementMapIsBuilt - startTime)/ 1_000_000);
				LOGGER.info("Time for timeUntilElementMapIsBuilt: {}", (timeUntilElementMapIsBuilt -timeUntillogicalElementHeaderRecordMapIsBuilt)/ 1_000_000);

				// First, create TreeNode objects for each LogicalElement

				createTreeNodeForEachLogicalElementParallel(logicalElementHeaderRecordMap, propertyTableRecord, treeNodeMap, elementMap);
				long createTreeNodeForEachLogicalElementParallel = System.nanoTime();
				LOGGER.info("Time until createTreeNodeForEachLogicalElementParallel: {}", (createTreeNodeForEachLogicalElementParallel - startTime)/ 1_000_000);
				LOGGER.info("Time for createTreeNodeForEachLogicalElementParallel: {}", (createTreeNodeForEachLogicalElementParallel -timeUntilElementMapIsBuilt)/ 1_000_000);
				attachAttributesToTreeNodes(decompressedLSGSegmentBuffer, logicalElementHeaderRecordMap, treeNodeMap, elementMap);
				long attachAttributesToTreeNodes = System.nanoTime();
				LOGGER.info("Time until attachAttributesToTreeNodes: {}", (attachAttributesToTreeNodes - startTime)/ 1_000_000);
				LOGGER.info("Time for attachAttributesToTreeNodes: {}", (attachAttributesToTreeNodes -timeUntilElementMapIsBuilt)/ 1_000_000);
				connectTreeHierarchy(decompressedLSGSegmentBuffer, logicalElementHeaderRecordMap, treeNodeMap, childObjectIDs, elementMap);
				long connectTreeHierarchy = System.nanoTime();
				LOGGER.info("Time until connectTreeHierarchy: {}", (connectTreeHierarchy - startTime)/ 1_000_000);
				LOGGER.info("Time for connectTreeHierarchy: {}", (connectTreeHierarchy -attachAttributesToTreeNodes)/ 1_000_000);
				
				for (TreeNode node : treeNodeMap.values()) {
				    if (!childObjectIDs.contains(node.objectID)) {
				        printTree(node, "");
				    }
				}
				
			} catch (IOException _) {
				LOGGER.error("Could not decomresss data segment");
			}
		}
	}
	
	private void attachAttributesToTreeNodes(ByteBuffer decompressedLSGSegmentBuffer,
			Map<Integer, LogicalElementHeaderRecord> logicalElementHeaderRecordMap,
			Map<Integer, TreeNode> treeNodeMap, Map<LogicalElementHeaderRecord, BufferDeserializable> elementMap) {
		for (LogicalElementHeaderRecord header : logicalElementHeaderRecordMap.values()) {

		    BufferDeserializable obj = elementMap.get(header);
		    if (obj == null) return; // or continue

		        Set<Integer> attributeObjectIDs = switch (obj) {
		            case GroupNodeElementRecord g -> g.grouNodeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            case MetaDataNodeElementRecord m -> m.metaDataNodeDataRecord().groupNodeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            case PartitionNodeElementRecord p -> p.groupNodeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            case PartNodeElementRecord p -> p.metaDataNodeDataRecord().groupNodeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            case RangeLODNodeElementRecord r -> r.lodNodeDataRecord().groupNodeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            case TriStripSetShapeNodeElementRecord t -> t.vertexShapeDataRecord().baseShapeDataRecord().baseNodeDataRecord().attributeObjectIds();
		            default -> EMPTY_INT_SET;
		        };

		        TreeNode parent = treeNodeMap.get(header.objectID());
		        if (parent == null) return;

		        for (Integer attrID : attributeObjectIDs) {
		            LogicalElementHeaderRecord attrHeader = logicalElementHeaderRecordMap.get(attrID);
		            if (attrHeader == null) continue;

		            NodeElementType.fromGuid(attrHeader.objectTypeID()).ifPresent(attrType -> {
		                BufferDeserializable attrObj = attrType.deserialize(decompressedLSGSegmentBuffer, attrHeader.jtEndIndex());

		                String attrKey = attrType.name() + "_" + attrID;
		                String attrValue = switch (attrObj) {
		                    case StringPropertyAtomElementRecord s -> s.mbString();
		                    case IntegerPropertyAtomElementRecord i -> String.valueOf(i.value());
		                    case FloatingPointPropertyAtomElementRecord f -> String.valueOf(f.value());
		                    case MaterialAttributeElementRecord m -> String.valueOf(m.diffuseColorAndAlpha());
		                    default -> "Unsupported attribute type";
		                };

		                parent.addAttribute(attrKey, attrValue);
		            });
		        }
		}
	}
	
	private void connectTreeHierarchy(ByteBuffer decompressedLSGSegmentBuffer,
			Map<Integer, LogicalElementHeaderRecord> logicalElementHeaderRecordMap, Map<Integer, TreeNode> treeNodeMap,
			Set<Integer> childObjectIDs, Map<LogicalElementHeaderRecord, BufferDeserializable> elementMap) {
		for (LogicalElementHeaderRecord header : logicalElementHeaderRecordMap.values()) {
		    BufferDeserializable obj = elementMap.get(header);
		    if (obj == null) continue;

		    Set<Integer> children = switch (obj) {
		        case GroupNodeElementRecord g -> g.grouNodeDataRecord().childNodeObjectIDSet();
		        case MetaDataNodeElementRecord m -> m.metaDataNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
		        case PartitionNodeElementRecord p -> p.groupNodeDataRecord().childNodeObjectIDSet();
		        case PartNodeElementRecord p -> p.metaDataNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
		        case RangeLODNodeElementRecord r -> r.lodNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
		        default -> Set.of();
		    };

		    TreeNode parent = treeNodeMap.get(header.objectID());
		    if (parent == null) continue;

		    for (Integer childID : children) {
		        TreeNode child = treeNodeMap.get(childID);
		        if (child != null) {
		            parent.addChild(child);
		            childObjectIDs.add(childID);
		        }
		    }
		}
	}
	
	private void createTreeNodeForEachLogicalElementParallel(Map<Integer, LogicalElementHeaderRecord> logicalElementHeaderRecordMap, PropertyTableRecord propertyTableRecord, Map<Integer, TreeNode> treeNodeMap, Map<LogicalElementHeaderRecord, BufferDeserializable> elementMap) {
		elementMap.entrySet().parallelStream().forEach(entry -> {
		    LogicalElementHeaderRecord header = entry.getKey();
		    BufferDeserializable obj = entry.getValue();
		    TreeNode node;

		    String nodeType = NodeElementType.fromGuid(header.objectTypeID())
		            .map(NodeElementType::toString)
		            .orElse("Unknown");

		    switch (obj) {
		        case StringPropertyAtomElementRecord stringPropertyRecord -> node = new TreeNode(header.objectID(), nodeType, stringPropertyRecord.mbString());
		        case LateLoadedPropertyAtomElementRecord lateLoadedPropertyRecord -> node = new TreeNode(header.objectID(), nodeType, String.valueOf(lateLoadedPropertyRecord.GUID()));
		        default -> {
		            ElementPropertyTableRecord propertyRecord =
		                propertyTableRecord.elementPropertyTableRecordMap().get(header.objectID());
		            node = new TreeNode(header.objectID(), nodeType);
		            if (propertyRecord != null) {
		                for (Map.Entry<Integer, Integer> attributeEntry : propertyRecord.elementPropertiesMap().entrySet()) {
		                    int keyID = attributeEntry.getKey();
		                    int valueID = attributeEntry.getValue();
		                    LogicalElementHeaderRecord keyHeader = logicalElementHeaderRecordMap.get(keyID);
		                    LogicalElementHeaderRecord valueHeader = logicalElementHeaderRecordMap.get(valueID);
		                    BufferDeserializable keyObj = elementMap.get(keyHeader);
		                    BufferDeserializable valueObj = elementMap.get(valueHeader);
		                    String key = extractValue(keyObj);
		                    String value = extractValue(valueObj);
		                    node.addAttribute(key, value);
		                }
		            }
		        }
		    }

		    treeNodeMap.put(header.objectID(), node);
		});
	}
	
	private void printTree(TreeNode node, String indent) {
		if (node.nodeType.endsWith("ATOM_ELEMENT") || node.nodeType.equals("MATERIAL_ATTRIBUTE_ELEMENT")) {
			return;
		}
	    System.out.println(indent + node.objectID + ":" + node.nodeType + 
	        (node.nodeName != null ? " (" + node.nodeName + ")" : ""));

	    if (!node.attributes.isEmpty()) {
	        for (Map.Entry<String, String> entry : node.attributes.entrySet()) {
	            System.out.println(indent + "  [Attr] " + entry.getKey() + " = " + entry.getValue());
	        }
	    }

	    for (TreeNode child : node.children) {
	        printTree(child, indent + "  ");
	    }
	}
	
	String extractValue(BufferDeserializable elem) {
	    if (elem instanceof StringPropertyAtomElementRecord sp) return sp.mbString();
	    if (elem instanceof IntegerPropertyAtomElementRecord ip) return String.valueOf(ip.value());
	    if (elem instanceof FloatingPointPropertyAtomElementRecord fp) return String.valueOf(fp.value());
	    if (elem instanceof LateLoadedPropertyAtomElementRecord lp) return lp.GUID();
	    return "<unknown>";
	}



}