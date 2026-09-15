package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.datastructures.TreeNode;
import io.github.tomusin.voyager.viewer.RenderScene;
import io.github.tomusin.voyager.viewer.RenderSceneNode;

class RenderSceneTest {

    @Test
    void preservesSourceHierarchyAndUsesStableLabels() {
        TreeNode root = new TreeNode(1, "GROUP_NODE");
        TreeNode namedChild = new TreeNode(2, "SHAPE_NODE", "Bracket");
        TreeNode unnamedChild = new TreeNode(3, "PART_NODE");
        root.addChild(namedChild);
        root.addChild(unnamedChild);

        RenderScene scene = RenderScene.fromRoots(List.of(root));
        RenderSceneNode renderRoot = scene.roots().getFirst();

        assertEquals("GROUP_NODE [1]", renderRoot.label());
        assertEquals(2, renderRoot.children().size());
        assertEquals("Bracket [2]", renderRoot.children().get(0).label());
        assertEquals("PART_NODE [3]", renderRoot.children().get(1).label());
        assertSame(root, renderRoot.sourceNode());
    }

    @Test
    void selectionMovesToOneNodeAtATime() {
        TreeNode root = new TreeNode(1, "GROUP_NODE");
        TreeNode child = new TreeNode(2, "SHAPE_NODE");
        root.addChild(child);
        RenderScene scene = RenderScene.fromRoots(List.of(root));
        RenderSceneNode renderRoot = scene.roots().getFirst();
        RenderSceneNode renderChild = renderRoot.children().getFirst();

        scene.select(renderRoot);
        scene.select(renderChild);

        assertFalse(renderRoot.selected());
        assertTrue(renderChild.selected());
        assertSame(renderChild, scene.selectedNode());

        scene.select(null);
        assertFalse(renderChild.selected());
        assertNull(scene.selectedNode());
    }

    @Test
    void hidesAChildWhenAnAncestorIsHidden() {
        TreeNode root = new TreeNode(1, "GROUP_NODE");
        TreeNode child = new TreeNode(2, "SHAPE_NODE");
        root.addChild(child);
        RenderScene scene = RenderScene.fromRoots(List.of(root));
        RenderSceneNode renderRoot = scene.roots().getFirst();
        RenderSceneNode renderChild = renderRoot.children().getFirst();

        renderRoot.setVisible(false);

        assertFalse(renderRoot.visible());
        assertTrue(renderChild.visible());
        assertTrue(scene.visibleGeometryNodes().isEmpty());
    }

    @Test
    void cyclesSelectionInTreeOrderAndTogglesTheSelectedNode() {
        TreeNode root = new TreeNode(1, "GROUP_NODE");
        TreeNode child = new TreeNode(2, "SHAPE_NODE");
        root.addChild(child);
        RenderScene scene = RenderScene.fromRoots(List.of(root));

        scene.selectNext(1);
        assertEquals(1, scene.selectedNode().objectId());
        scene.selectNext(1);
        assertEquals(2, scene.selectedNode().objectId());
        scene.toggleSelectedVisibility();

        assertFalse(scene.selectedNode().visible());
        scene.selectNext(-1);
        assertEquals(1, scene.selectedNode().objectId());
    }
}