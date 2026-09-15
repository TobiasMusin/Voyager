package io.github.tomusin.voyager.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.tomusin.voyager.datastructures.TreeNode;

/** A viewer-owned node that retains source hierarchy and mutable render state. */
public final class RenderSceneNode {
    private final TreeNode sourceNode;
    private final String label;
    private final List<RenderSceneNode> children = new ArrayList<>();
    private final RenderMesh mesh;
    private volatile boolean visible = true;
    private volatile boolean selected;

    RenderSceneNode(TreeNode sourceNode, RenderMesh mesh) {
        this.sourceNode = Objects.requireNonNull(sourceNode, "sourceNode");
        this.label = createLabel(sourceNode);
        this.mesh = mesh;
    }

    public TreeNode sourceNode() {
        return sourceNode;
    }

    public int objectId() {
        return sourceNode.objectID;
    }

    public String label() {
        return label;
    }

    public List<RenderSceneNode> children() {
        return List.copyOf(children);
    }

    public boolean hasGeometry() {
        return mesh != null;
    }

    public RenderMesh mesh() {
        return mesh;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean selected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    void addChild(RenderSceneNode child) {
        children.add(Objects.requireNonNull(child, "child"));
    }

    private static String createLabel(TreeNode node) {
        String name = node.nodeName;
        if (name != null && !name.isBlank()) {
            return name + " [" + node.objectID + "]";
        }
        return node.nodeType + " [" + node.objectID + "]";
    }
}