package io.github.tomusin.voyager.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.tomusin.lodDataRecords.CompressedVertexCoordinateArrayRecord;
import io.github.tomusin.lodDataRecords.TopologicallyCompressedRepDataRecord;
import io.github.tomusin.lodDataRecords.VertexShapeLODDataRecord;
import io.github.tomusin.lodElements.TriStripSetShapeLODElementRecord;

public class TreeNode {
    public int objectID;
    public String nodeType;
    public List<TreeNode> children = new ArrayList<>();
    public Map<String, String> attributes = new HashMap<>();
    public String nodeName;

    /** The deserialized element backing this node (may be null for property atoms etc.) */
    private BufferDeserializable element;

    /** Geometry from the ShapeLOD0 segment linked to this node (set by JTReader) */
    private TriStripSetShapeLODElementRecord lodGeometry;

    public TreeNode(int objectID, String type) {
        this.objectID = objectID;
        this.nodeType = type;
    }
    
    public TreeNode(int objectID, String type, String nodeName) {
        this.objectID = objectID;
        this.nodeType = type;
        this.nodeName = nodeName;
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }

    public void addAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public void setElement(BufferDeserializable element) {
        this.element = element;
    }

    public BufferDeserializable getElement() {
        return element;
    }

    public void setLodGeometry(TriStripSetShapeLODElementRecord lodGeometry) {
        this.lodGeometry = lodGeometry;
    }

    public TriStripSetShapeLODElementRecord getLodGeometry() {
        return lodGeometry;
    }

    /**
     * Returns the dequantized vertex coordinates if this node has geometry.
     * Result is float[numComponents][numVertices] (typically [3][N] for X,Y,Z).
     * Returns null if no geometry is available.
     */
    public float[][] getVertexCoordinates() {
        CompressedVertexCoordinateArrayRecord coordArray = getCompressedVertexCoordinateArray();
        return coordArray != null ? coordArray.dequantize() : null;
    }

    /**
     * Returns the compressed vertex coordinate array record if geometry is available.
     */
    public CompressedVertexCoordinateArrayRecord getCompressedVertexCoordinateArray() {
        if (lodGeometry != null) {
            VertexShapeLODDataRecord lod = lodGeometry.vertexShapeLODDataRecord();
            if (lod.topoMeshTopologicallyCompressedLODDataRecord() != null) {
                TopologicallyCompressedRepDataRecord rep = lod.topoMeshTopologicallyCompressedLODDataRecord()
                        .topologicallyCompressedRepDataRecord();
                if (rep != null && rep.topologicallyCompressedVertexRecords() != null) {
                    return rep.topologicallyCompressedVertexRecords().compressedVertexCoordinateArray();
                }
            }
        }
        return null;
    }

    /**
     * Returns true if this node has geometry data attached.
     */
    public boolean hasGeometry() {
        return lodGeometry != null;
    }

    /**
     * Recursively collects all tree nodes that have geometry.
     */
    public List<TreeNode> findNodesWithGeometry() {
        List<TreeNode> result = new ArrayList<>();
        collectGeometryNodes(result);
        return result;
    }

    private void collectGeometryNodes(List<TreeNode> result) {
        if (hasGeometry()) {
            result.add(this);
        }
        for (TreeNode child : children) {
            child.collectGeometryNodes(result);
        }
    }
}