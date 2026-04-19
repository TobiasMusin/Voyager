package io.github.tomusin.voyager.export;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import io.github.tomusin.voyager.datastructures.TreeNode;

/**
 * Exports JT geometry tree nodes to a glTF 2.0 (.gltf) file using DSL-JSON.
 * Produces a single .gltf file with base64-embedded binary vertex data.
 * Each tree node with geometry becomes a mesh + node in the glTF scene.
 */
public class GltfExporter {

    /**
     * Export all nodes with geometry from the given root nodes to a .gltf file.
     */
    public static void export(List<TreeNode> rootNodes, Path outputPath) throws IOException {
        List<TreeNode> geometryNodes = new ArrayList<>();
        for (TreeNode root : rootNodes) {
            geometryNodes.addAll(root.findNodesWithGeometry());
        }

        if (geometryNodes.isEmpty()) {
            System.out.println("No geometry nodes found — nothing to export.");
            return;
        }

        List<MeshData> meshes = new ArrayList<>();
        for (TreeNode node : geometryNodes) {
            float[][] coords = node.getVertexCoordinates();
            if (coords == null || coords.length < 3) continue;
            meshes.add(buildMeshData(node, coords));
        }

        DslJson<Object> dslJson = new DslJson<>();
        JsonWriter writer = dslJson.newWriter();
        writeGltf(writer, meshes);

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            writer.toStream(fos);
        }

        System.out.printf("Exported %d mesh(es) to %s%n", meshes.size(), outputPath);
    }

    private static class MeshData {
        String name;
        float[] positions;
        int vertexCount;
        float[] min = new float[3];
        float[] max = new float[3];
    }

    private static MeshData buildMeshData(TreeNode node, float[][] coords) {
        int n = coords[0].length;
        MeshData m = new MeshData();
        m.name = node.nodeName != null ? node.nodeName : "mesh_" + node.objectID;
        m.vertexCount = n;
        m.positions = new float[n * 3];

        m.min[0] = Float.MAX_VALUE; m.min[1] = Float.MAX_VALUE; m.min[2] = Float.MAX_VALUE;
        m.max[0] = -Float.MAX_VALUE; m.max[1] = -Float.MAX_VALUE; m.max[2] = -Float.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            float x = Float.isFinite(coords[0][i]) ? coords[0][i] : 0f;
            float y = Float.isFinite(coords[1][i]) ? coords[1][i] : 0f;
            float z = Float.isFinite(coords[2][i]) ? coords[2][i] : 0f;
            m.positions[i * 3]     = x;
            m.positions[i * 3 + 1] = y;
            m.positions[i * 3 + 2] = z;
            if (x < m.min[0]) m.min[0] = x; if (x > m.max[0]) m.max[0] = x;
            if (y < m.min[1]) m.min[1] = y; if (y > m.max[1]) m.max[1] = y;
            if (z < m.min[2]) m.min[2] = z; if (z > m.max[2]) m.max[2] = z;
        }
        return m;
    }

    private static void writeGltf(JsonWriter w, List<MeshData> meshes) {
        w.writeByte(JsonWriter.OBJECT_START);

        // asset
        writeKey(w, "asset", false);
        w.writeByte(JsonWriter.OBJECT_START);
        writeKey(w, "version", false); w.writeString("2.0");
        writeKey(w, "generator", true); w.writeString("Voyager JTReader");
        w.writeByte(JsonWriter.OBJECT_END);

        // scene
        writeKey(w, "scene", true); NumberConverter.serialize(0, w);

        // scenes
        writeKey(w, "scenes", true);
        w.writeByte(JsonWriter.ARRAY_START);
        w.writeByte(JsonWriter.OBJECT_START);
        writeKey(w, "name", false); w.writeString("Scene");
        writeKey(w, "nodes", true);
        writeSequentialIntArray(w, meshes.size());
        w.writeByte(JsonWriter.OBJECT_END);
        w.writeByte(JsonWriter.ARRAY_END);

        // nodes
        writeKey(w, "nodes", true);
        w.writeByte(JsonWriter.ARRAY_START);
        for (int i = 0; i < meshes.size(); i++) {
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "name", false); w.writeString(meshes.get(i).name);
            writeKey(w, "mesh", true); NumberConverter.serialize(i, w);
            w.writeByte(JsonWriter.OBJECT_END);
        }
        w.writeByte(JsonWriter.ARRAY_END);

        // meshes
        writeKey(w, "meshes", true);
        w.writeByte(JsonWriter.ARRAY_START);
        for (int i = 0; i < meshes.size(); i++) {
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "name", false); w.writeString(meshes.get(i).name);
            writeKey(w, "primitives", true);
            w.writeByte(JsonWriter.ARRAY_START);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "attributes", false);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "POSITION", false); NumberConverter.serialize(i, w);
            w.writeByte(JsonWriter.OBJECT_END);
            writeKey(w, "mode", true); NumberConverter.serialize(0, w); // POINTS
            w.writeByte(JsonWriter.OBJECT_END);
            w.writeByte(JsonWriter.ARRAY_END);
            w.writeByte(JsonWriter.OBJECT_END);
        }
        w.writeByte(JsonWriter.ARRAY_END);

        // accessors
        writeKey(w, "accessors", true);
        w.writeByte(JsonWriter.ARRAY_START);
        for (int i = 0; i < meshes.size(); i++) {
            MeshData m = meshes.get(i);
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "bufferView", false); NumberConverter.serialize(i, w);
            writeKey(w, "componentType", true); NumberConverter.serialize(5126, w); // FLOAT
            writeKey(w, "count", true); NumberConverter.serialize(m.vertexCount, w);
            writeKey(w, "type", true); w.writeString("VEC3");
            writeKey(w, "min", true); writeFloatArray(w, m.min);
            writeKey(w, "max", true); writeFloatArray(w, m.max);
            w.writeByte(JsonWriter.OBJECT_END);
        }
        w.writeByte(JsonWriter.ARRAY_END);

        // bufferViews
        writeKey(w, "bufferViews", true);
        w.writeByte(JsonWriter.ARRAY_START);
        int byteOffset = 0;
        for (int i = 0; i < meshes.size(); i++) {
            MeshData m = meshes.get(i);
            int byteLength = m.vertexCount * 3 * 4;
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            w.writeByte(JsonWriter.OBJECT_START);
            writeKey(w, "buffer", false); NumberConverter.serialize(0, w);
            writeKey(w, "byteOffset", true); NumberConverter.serialize(byteOffset, w);
            writeKey(w, "byteLength", true); NumberConverter.serialize(byteLength, w);
            writeKey(w, "target", true); NumberConverter.serialize(34962, w); // ARRAY_BUFFER
            w.writeByte(JsonWriter.OBJECT_END);
            byteOffset += byteLength;
        }
        w.writeByte(JsonWriter.ARRAY_END);

        // buffers — single buffer, base64-embedded
        int totalBytes = meshes.stream().mapToInt(m -> m.vertexCount * 3 * 4).sum();
        ByteBuffer binBuf = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (MeshData m : meshes) {
            for (float v : m.positions) {
                binBuf.putFloat(v);
            }
        }
        String dataUri = "data:application/octet-stream;base64," +
                Base64.getEncoder().encodeToString(binBuf.array());

        writeKey(w, "buffers", true);
        w.writeByte(JsonWriter.ARRAY_START);
        w.writeByte(JsonWriter.OBJECT_START);
        writeKey(w, "uri", false); w.writeString(dataUri);
        writeKey(w, "byteLength", true); NumberConverter.serialize(totalBytes, w);
        w.writeByte(JsonWriter.OBJECT_END);
        w.writeByte(JsonWriter.ARRAY_END);

        w.writeByte(JsonWriter.OBJECT_END);
    }

    /** Write a JSON key with optional leading comma. */
    private static void writeKey(JsonWriter w, String key, boolean comma) {
        if (comma) w.writeByte(JsonWriter.COMMA);
        w.writeString(key);
        w.writeByte(JsonWriter.SEMI);
    }

    private static void writeSequentialIntArray(JsonWriter w, int count) {
        w.writeByte(JsonWriter.ARRAY_START);
        for (int i = 0; i < count; i++) {
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            NumberConverter.serialize(i, w);
        }
        w.writeByte(JsonWriter.ARRAY_END);
    }

    private static void writeFloatArray(JsonWriter w, float[] values) {
        w.writeByte(JsonWriter.ARRAY_START);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) w.writeByte(JsonWriter.COMMA);
            NumberConverter.serialize(values[i], w);
        }
        w.writeByte(JsonWriter.ARRAY_END);
    }
}
