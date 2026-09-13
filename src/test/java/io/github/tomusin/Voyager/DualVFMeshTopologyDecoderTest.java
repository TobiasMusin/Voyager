package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.readers.JTReader;

class DualVFMeshTopologyDecoderTest {

    @Test
    void exampleBlockReconstructsTwelveCubeTriangles() {
        JTReader reader = new JTReader();
        reader.startReading(Path.of("src", "main", "resources", "example_block_jt10.3.jt").toAbsolutePath());

        TreeNode shapeNode = reader.getTreeNodeMap().get(8);
        float[][] coordinates = shapeNode.getVertexCoordinates();
        int[] indices = shapeNode.getTriangleIndices();

        assertNotNull(coordinates);
        assertEquals(8, coordinates[0].length);
        assertNotNull(indices);
        assertEquals(36, indices.length);
        for (int index : indices) assertTrue(index >= 0 && index < coordinates[0].length);

        Map<String, Integer> edgeUseCount = new HashMap<>();
        for (int index = 0; index < indices.length; index += 3) {
            countEdge(edgeUseCount, indices[index], indices[index + 1]);
            countEdge(edgeUseCount, indices[index + 1], indices[index + 2]);
            countEdge(edgeUseCount, indices[index + 2], indices[index]);
        }
        assertEquals(18, edgeUseCount.size());
        assertTrue(edgeUseCount.values().stream().allMatch(count -> count == 2));
    }

    private static void countEdge(Map<String, Integer> counts, int first, int second) {
        String edge = Math.min(first, second) + ":" + Math.max(first, second);
        counts.merge(edge, 1, Integer::sum);
    }
}