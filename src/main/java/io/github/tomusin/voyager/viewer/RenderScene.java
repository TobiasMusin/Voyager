package io.github.tomusin.voyager.viewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.tomusin.voyager.datastructures.TreeNode;

/** Retained render hierarchy built from parsed JT scene graph roots. */
public final class RenderScene {
    private final List<RenderSceneNode> roots;
    private RenderSceneNode selectedNode;

    private RenderScene(List<RenderSceneNode> roots) {
        this.roots = List.copyOf(roots);
    }

    public static RenderScene fromRoots(List<TreeNode> sourceRoots) {
        Objects.requireNonNull(sourceRoots, "sourceRoots");
        Set<Integer> visitedObjectIds = new HashSet<>();
        List<RenderSceneNode> sceneRoots = new ArrayList<>();
        for (TreeNode sourceRoot : sourceRoots) {
            RenderSceneNode root = buildNode(sourceRoot, visitedObjectIds);
            if (root != null) {
                sceneRoots.add(root);
            }
        }
        return new RenderScene(sceneRoots);
    }

    public List<RenderSceneNode> roots() {
        return roots;
    }

    public RenderSceneNode selectedNode() {
        return selectedNode;
    }

    public List<RenderSceneNode> nodes() {
        List<RenderSceneNode> nodes = new ArrayList<>();
        for (RenderSceneNode root : roots) {
            collectNodes(root, nodes);
        }
        return nodes;
    }

    public void select(RenderSceneNode node) {
        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }
        selectedNode = node;
        if (selectedNode != null) {
            selectedNode.setSelected(true);
        }
    }

    public void selectNext(int direction) {
        List<RenderSceneNode> nodes = nodes();
        if (nodes.isEmpty()) {
            select(null);
            return;
        }
        int currentIndex = selectedNode == null ? -1 : nodes.indexOf(selectedNode);
        int nextIndex = Math.floorMod(currentIndex + direction, nodes.size());
        select(nodes.get(nextIndex));
    }

    public void toggleSelectedVisibility() {
        if (selectedNode != null) {
            selectedNode.setVisible(!selectedNode.visible());
        }
    }

    public List<RenderSceneNode> visibleGeometryNodes() {
        List<RenderSceneNode> geometryNodes = new ArrayList<>();
        for (RenderSceneNode root : roots) {
            collectVisibleGeometry(root, true, geometryNodes);
        }
        return geometryNodes;
    }

    private static RenderSceneNode buildNode(TreeNode sourceNode, Set<Integer> visitedObjectIds) {
        if (sourceNode == null || !visitedObjectIds.add(sourceNode.objectID)) {
            return null;
        }
        RenderSceneNode renderNode = new RenderSceneNode(sourceNode, decodeMesh(sourceNode));
        for (TreeNode sourceChild : sourceNode.children) {
            RenderSceneNode renderChild = buildNode(sourceChild, visitedObjectIds);
            if (renderChild != null) {
                renderNode.addChild(renderChild);
            }
        }
        return renderNode;
    }

    private static RenderMesh decodeMesh(TreeNode sourceNode) {
        if (!sourceNode.hasGeometry()) {
            return null;
        }
        try {
            float[][] coordinates = sourceNode.getVertexCoordinates();
            int[] triangleIndices = sourceNode.getTriangleIndices();
            if (coordinates == null || coordinates.length < 3 || coordinates[0] == null
                    || coordinates[1] == null || coordinates[2] == null || triangleIndices == null
                    || triangleIndices.length == 0 || triangleIndices.length % 3 != 0) {
                return null;
            }
            int vertexCount = coordinates[0].length;
            if (coordinates[1].length != vertexCount || coordinates[2].length != vertexCount || vertexCount == 0) {
                return null;
            }
            float[] positions = new float[vertexCount * 3];
            for (int index = 0; index < vertexCount; index++) {
                float x = coordinates[0][index];
                float y = coordinates[1][index];
                float z = coordinates[2][index];
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    return null;
                }
                positions[index * 3] = x;
                positions[index * 3 + 1] = y;
                positions[index * 3 + 2] = z;
            }
            for (int triangleIndex : triangleIndices) {
                if (triangleIndex < 0 || triangleIndex >= vertexCount) {
                    return null;
                }
            }
            return new RenderMesh(positions, triangleIndices);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void collectVisibleGeometry(RenderSceneNode node, boolean ancestorsVisible,
            List<RenderSceneNode> geometryNodes) {
        boolean effectivelyVisible = ancestorsVisible && node.visible();
        if (!effectivelyVisible) {
            return;
        }
        if (node.hasGeometry()) {
            geometryNodes.add(node);
        }
        for (RenderSceneNode child : node.children()) {
            collectVisibleGeometry(child, true, geometryNodes);
        }
    }

    private static void collectNodes(RenderSceneNode node, List<RenderSceneNode> nodes) {
        nodes.add(node);
        for (RenderSceneNode child : node.children()) {
            collectNodes(child, nodes);
        }
    }
}