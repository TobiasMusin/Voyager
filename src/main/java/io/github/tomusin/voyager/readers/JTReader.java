package io.github.tomusin.voyager.readers;

import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.tinylog.Logger;

import io.github.tomusin.lodElements.TriStripSetShapeLODElementRecord;
import io.github.tomusin.voyager.datastructures.BufferDeserializable;
import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.fileRecords.FileHeaderRecord;
import io.github.tomusin.voyager.fileRecords.SegmentHeaderRecord;
import io.github.tomusin.voyager.fileRecords.TOCRecord;
import io.github.tomusin.voyager.segments.DataSegmentType;
import io.github.tomusin.voyager.segments.LSGDataSegment;
import io.github.tomusin.voyager.segments.MetaDataSegment;
import io.github.tomusin.voyager.segments.ShapeLOD0DataSegment;
import io.github.tomusin.voyager.utils.ReadFromBufferUtils;

public class JTReader {
	
	FileHeaderRecord fileHeaderRecord;
	
	// Seems most writers ignore the U64 Segment offset and write I32 instead
	// Also 9.5 seems to have some problems for me, maybe documentation changed? -> investigate and fix later
	private static Set<String> writersThatWriteI32SegmentOffset = Set.of("Version 10.5 JT  DM 10.3.1.2", "Version 10.3 JT  DM 9.4.0.0", "Version 9.5 JT  DM 8.0.7.0", "Version 10.5 JT  DM 10.6.0.3", "Version 10.6 JT  DM 10.5.0.0");
	private String strippedVersionString;
	private ByteOrder fileByteOrder;

	/** The LSG segment containing the scene graph tree */
	private LSGDataSegment lsgSegment;
	/** All parsed ShapeLOD0 segments */
	private final List<ShapeLOD0DataSegment> shapeLOD0Segments = new ArrayList<>();

	/**
	 * Returns the root nodes of the scene graph tree with geometry linked.
	 * Call after {@link #startReading(Path)}.
	 */
	public List<TreeNode> getRootNodes() {
		return lsgSegment != null ? lsgSegment.getRootNodes() : List.of();
	}

	/**
	 * Returns all tree nodes indexed by object ID.
	 */
	public Map<Integer, TreeNode> getTreeNodeMap() {
		return lsgSegment != null ? lsgSegment.getTreeNodeMap() : Map.of();
	}

	public void startReading(Path filename) {
		long startTime = System.nanoTime();
	    try (RandomAccessFile file = new RandomAccessFile(filename.toAbsolutePath().toString(), "r");
	         FileChannel fileChannel = file.getChannel()) {

	        // Reset state from previous file reads
	        lsgSegment = null;
	        shapeLOD0Segments.clear();
	        
	        // Map the file into memory
	        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
	        setFileHeaderRecordFromFile(buffer);
	        
	        int[] tocStartIndices = generateTOCOffsets(fileHeaderRecord.tocOffset(), fileHeaderRecord.tocEntryCount());
	        Map<String, TOCRecord> tocMap = new ConcurrentHashMap<>();
	        Arrays.stream(tocStartIndices).parallel().forEach(tocStartIndex -> readTOCEntry(buffer, tocStartIndex, tocMap, writersThatWriteI32SegmentOffset.contains(strippedVersionString)));
	        
	        int[] segmentOffsets = tocMap.entrySet()
                    .stream()
                    .mapToLong(entry -> entry.getValue().segmentOffset())
                    .mapToInt(value -> {
                        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                            throw new IllegalArgumentException("Value out of range for int: " + value);
                        }
                        return (int) value;
                    })
                    .toArray();
	        
	        Map<String, SegmentHeaderRecord> segmentHeaderMap = new ConcurrentHashMap<>();
	        Logger.info("----------------------------------------------------------");

	        Arrays.stream(segmentOffsets).forEach(segmentHeaderOffset -> readSegmentHeaderEntry(buffer, segmentHeaderOffset, segmentHeaderMap));

	        // Link ShapeLOD0 geometry to tree nodes
	        linkGeometryToTree();

	    } catch (Exception e) {
	        Logger.error(e, "Failed to read file: {}", filename);
	    }
	    long endTime = System.nanoTime();
	    Logger.info("Total time: {} ms", (endTime - startTime) / 1_000_000);
	}
	
	/**
	 * Links geometry from ShapeLOD0 segments to the corresponding tree nodes.
	 * ShapeLOD0 elements are matched to tree nodes by object ID — if a tree node
	 * has a child reference to an object ID that exists in a ShapeLOD0 segment,
	 * the geometry is attached to that tree node.
	 */
	private void linkGeometryToTree() {
		if (lsgSegment == null || shapeLOD0Segments.isEmpty()) return;

		Map<Integer, TreeNode> treeNodeMap = lsgSegment.getTreeNodeMap();

		for (ShapeLOD0DataSegment shapeSeg : shapeLOD0Segments) {
			String segmentGuid = shapeSeg.getSegmentHeaderRecord().guid();
			for (Map.Entry<Integer, BufferDeserializable> entry : shapeSeg.getElementsByObjectID().entrySet()) {
				int objectID = entry.getKey();
				BufferDeserializable obj = entry.getValue();

				if (obj instanceof TriStripSetShapeLODElementRecord lodElement) {
					TreeNode lateLoadedNode = findLateLoadedShapeNode(treeNodeMap, segmentGuid);
					if (lateLoadedNode != null) {
						lateLoadedNode.setLodGeometry(lodElement);
						Logger.debug("Linked geometry to tree node {} ({}) via ShapeLOD segment {}",
								lateLoadedNode.objectID, lateLoadedNode.nodeType, segmentGuid);
						continue;
					}

					// Check if this object ID is already a tree node (direct match)
					TreeNode node = treeNodeMap.get(objectID);
					if (node != null) {
						node.setLodGeometry(lodElement);
						Logger.debug("Linked geometry to tree node {} ({})", objectID, node.nodeType);
						continue;
					}

					// Otherwise, find tree nodes that reference this ID as a child
					for (TreeNode treeNode : treeNodeMap.values()) {
						BufferDeserializable element = treeNode.getElement();
						if (element == null) continue;

						Set<Integer> childIDs = getChildObjectIDs(element);
						if (childIDs.contains(objectID)) {
							treeNode.setLodGeometry(lodElement);
							Logger.debug("Linked geometry to parent tree node {} ({}) via child ref {}",
									treeNode.objectID, treeNode.nodeType, objectID);
							break;
						}
					}
				}
			}
		}

		// Log summary
		long geometryCount = treeNodeMap.values().stream().filter(TreeNode::hasGeometry).count();
		Logger.info("Linked {} geometry elements to tree nodes", geometryCount);
	}

	private TreeNode findLateLoadedShapeNode(Map<Integer, TreeNode> treeNodeMap, String segmentGuid) {
		return treeNodeMap.values().stream()
				.filter(node -> "TRI_STRIP_SET_SHAPE_NODE_ELEMENT".equals(node.nodeType))
				.filter(node -> node.attributes.entrySet().stream().anyMatch(attribute ->
						attribute.getKey().startsWith("JT_LLPROP_SHAPEIMPL")
								&& segmentGuid.equalsIgnoreCase(attribute.getValue())))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Extract child object IDs from an element, matching the logic in LSGDataSegment.connectTreeHierarchy.
	 */
	private Set<Integer> getChildObjectIDs(BufferDeserializable obj) {
		return switch (obj) {
			case io.github.tomusin.lsgElements.GroupNodeElementRecord g -> g.grouNodeDataRecord().childNodeObjectIDSet();
			case io.github.tomusin.lsgElements.MetaDataNodeElementRecord m -> m.metaDataNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
			case io.github.tomusin.lsgElements.PartitionNodeElementRecord p -> p.groupNodeDataRecord().childNodeObjectIDSet();
			case io.github.tomusin.lsgElements.PartNodeElementRecord p -> p.metaDataNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
			case io.github.tomusin.lsgElements.RangeLODNodeElementRecord r -> r.lodNodeDataRecord().groupNodeDataRecord().childNodeObjectIDSet();
			default -> Set.of();
		};
	}
	
	private void readTOCEntry(MappedByteBuffer buffer, int startIndex, Map<String, TOCRecord> tocMap, boolean i32InsteadOfU64) {
		String segmentGUID = getGUID(buffer, startIndex);
		long segmentOffset;
		long segmentLength;
		
		if (i32InsteadOfU64) {
			segmentOffset = buffer.getInt(startIndex + 16);
			segmentLength = buffer.getInt(startIndex + 16 + 8);
		} else {
			segmentOffset = ReadFromBufferUtils.readUnsignedLong(buffer, startIndex + 16);
			segmentLength = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 16 + 8);			
		}
		long segmentAttributes = ReadFromBufferUtils.readUnsignedInt(buffer, startIndex + 16 + 8 + 4);
		tocMap.put(segmentGUID, new TOCRecord(segmentGUID, segmentOffset, segmentLength, segmentAttributes));
		Logger.info("segmentOffset is: {}", segmentOffset);
		Logger.info("Created TOCRecord: {}", tocMap.get(segmentGUID));
		Logger.info("SegmentType: {} -> {}", tocMap.get(segmentGUID).getSegmentType(), DataSegmentType.getByTypeId((int) tocMap.get(segmentGUID).getSegmentType()).getContents());
		Logger.info("Length in TOC Entry is: {}", tocMap.get(segmentGUID).segmentLength());
	}
	
	private void readSegmentHeaderEntry(MappedByteBuffer buffer, int startIndex, Map<String, SegmentHeaderRecord> segmentHeaderMap) {
		String segmentGUID = ReadFromBufferUtils.getGUID(buffer, startIndex);
		int segmentType = buffer.getInt(startIndex + 16);
		int segmentLength = buffer.getInt(startIndex + 16 + 4);
		segmentHeaderMap.put(segmentGUID, new SegmentHeaderRecord(segmentGUID, segmentType, segmentLength));

		if (segmentType == 1) {
			lsgSegment = new LSGDataSegment(segmentHeaderMap.get(segmentGUID), buffer.duplicate(), startIndex, fileByteOrder);
		} else if (segmentType == 4) {
			new MetaDataSegment(segmentHeaderMap.get(segmentGUID), buffer.duplicate(), startIndex, fileByteOrder);
		} else if (segmentType == 7) {
			ShapeLOD0DataSegment shapeSeg = new ShapeLOD0DataSegment(segmentHeaderMap.get(segmentGUID), buffer.duplicate(), startIndex, fileByteOrder);
			shapeLOD0Segments.add(shapeSeg);
		}
	}
	
	private void setFileHeaderRecordFromFile(MappedByteBuffer buffer) {
		String version = getVersionAndCheckForCorruption(buffer);
		fileByteOrder = buffer.get(80) == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		boolean fileByteOrderIsSystemByteOrder = fileByteOrder.equals(ByteOrder.nativeOrder());
		Logger.info(version);
		Logger.info("File Byte order is {}. Byte order matches Systems ByteOrder: {}", fileByteOrder, fileByteOrderIsSystemByteOrder);
		buffer.order(fileByteOrder);

		int emptyField = buffer.getInt(81);

		boolean version10OrUp = isVersion10OrUp(version);
		strippedVersionString = version.strip();

		int tocOffset = getTOCOffset(buffer, version10OrUp, writersThatWriteI32SegmentOffset.contains(version.strip()));

		// TODO: Research, do we really use the empty field as GUID if it is not 0? The format can not be the same as a I32 can not hold a valid GUID?
		String guid;
		if (emptyField == 0) {
		    guid = getGUID(buffer, 93);
		} else {
		    guid = String.valueOf(emptyField);
		}

		// Check if the tocOffsetUnsigned is within the range of an int
		if (tocOffset > buffer.capacity()) {
		    throw new IllegalArgumentException("TOC Offset is out of range of the file: " + tocOffset + " fileLength: " + buffer.capacity());
		}

		// Use the converted int value to fetch data
		int tocEntryCount = buffer.getInt(tocOffset);

		// Create the FileHeader record
		fileHeaderRecord = new FileHeaderRecord(
		    version,
		    fileByteOrder,
		    fileByteOrderIsSystemByteOrder,
		    emptyField,
		    version10OrUp,
		    tocOffset,
		    guid,
		    tocEntryCount
		);

		// Print the file header data
		Logger.info("FileHeaderRecord is: {}", fileHeaderRecord);
	}

	private int getTOCOffset(MappedByteBuffer buffer, boolean version10OrUp, boolean nobodySeemsToImplementTheReference) {
		int tocOffset = 0;
		if (nobodySeemsToImplementTheReference || !version10OrUp) {
			// I32 is used in versions before 10
			tocOffset = buffer.getInt(85);				
		} else {
			long tocOffsetLong = ReadFromBufferUtils.readUnsignedLong(buffer, 85);
			if (tocOffsetLong > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(String.format("\"TOC Offset is out of range of of an int: %s/%s", tocOffsetLong, Integer.MAX_VALUE));
			}
		}
		return tocOffset;
	}
	
	public static int[] generateTOCOffsets(int tocOffset, int numEntries) {
        // Starting point
        int startPoint = tocOffset + 4;

        // Size of each TOC entry
        int tocEntrySize = 16 + 8 + 4 + 4;  // 32 bytes

        // Create the array
        int[] tocOffsets = new int[numEntries];
        for (int i = 0; i < numEntries; i++) {
            tocOffsets[i] = startPoint + i * tocEntrySize;
        }

        return tocOffsets;
    }

	private boolean isVersion10OrUp(String version) {
		try {
		    String[] versionParts = version.split(" ");
		    if (versionParts.length > 1) {
		        float versionNumber = Float.parseFloat(versionParts[1]);
		        if (versionNumber >= 10) {
		            Logger.info("Version greater than or equal to 10. Using U64 for TOC Offset");
		            Logger.info("Please keep in mind that we currently only support files up to {} MiBytes ({} Megabytes)", Integer.MAX_VALUE / (1024 * 1024), Integer.MAX_VALUE / 1_000_000);
		            return true;
		        } else {
		            Logger.info("Version smaller than 10. Using I32 for TOC Offset");
		            return false;
		        }
		    } else {
		        Logger.warn("Version string does not contain a valid version number.");
		    }
		} catch (NumberFormatException e) {
		    Logger.error("Failed to parse version number from string: {}", version, e);
		}
		return false;
	}

	private String getGUID(MappedByteBuffer buffer, int guidStartIndex) {
		// Extract the GUID components
		int part1 = buffer.getInt(guidStartIndex);
		short part2 = buffer.getShort(guidStartIndex + 4);
		short part3 = buffer.getShort(guidStartIndex + 6);
		byte[] part4 = new byte[8];
		buffer.position(guidStartIndex + 8);
		buffer.get(part4);

		// Format the GUID as a string
		return String.format("{%08X-%04X-%04X-%02X-%02X-%02X-%02X-%02X-%02X-%02X-%02X}", part1, part2, part3,
				part4[0], part4[1], part4[2], part4[3], part4[4], part4[5], part4[6], part4[7]);
	}

	private static String getVersionAndCheckForCorruption(MappedByteBuffer buffer) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean corrupted = false;

		// Expected characters for the last 5 positions
		char[] expectedChars = { ' ', '\n', '\r', '\n', ' ' };

		for (int i = 0; i < Math.min(buffer.limit(), 80); i++) {
			char currentChar = (char) buffer.get(i);
			stringBuilder.append(currentChar);

			// Check the expected characters at specific indices
			if (i >= 75 && i <= 79) {
				corrupted = corrupted || currentChar != expectedChars[i - 75];
			}
		}
		if (corrupted) {
			Logger.warn("Final version characters deviate from definition. JTFile might be corrupted.");
		} else {
			Logger.info("Final version characters check out. JTFile is probably not corrupted");
		}
		return stringBuilder.toString();
	}
}