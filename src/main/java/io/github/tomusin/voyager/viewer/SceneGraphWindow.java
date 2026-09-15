package io.github.tomusin.voyager.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/** Swing control surface for inspecting and controlling the retained render scene. */
final class SceneGraphWindow {
    private volatile JFrame frame;

    private SceneGraphWindow() {
    }

    static SceneGraphWindow show(RenderScene scene, Runnable resetCamera, Consumer<Boolean> setCoordinateColors,
            Consumer<Boolean> setFaces, Consumer<Boolean> setEdges, Consumer<Boolean> setVertices) {
        SceneGraphWindow window = new SceneGraphWindow();
        SwingUtilities.invokeLater(() -> window.createAndShow(scene, resetCamera, setCoordinateColors, setFaces, setEdges,
                setVertices));
        return window;
    }

    void close() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.dispose();
        });
    }

    private void createAndShow(RenderScene scene, Runnable resetCamera, Consumer<Boolean> setCoordinateColors,
            Consumer<Boolean> setFaces, Consumer<Boolean> setEdges, Consumer<Boolean> setVertices) {
        frame = new JFrame("JT Scene Graph");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JT Scene");
        for (RenderSceneNode sceneRoot : scene.roots()) {
            root.add(createTreeNode(sceneRoot));
        }
        JTree tree = new JTree(root) {
            @Override
            public String getToolTipText(MouseEvent event) {
                var path = getPathForLocation(event.getX(), event.getY());
                if (path == null) return null;
                Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                return value instanceof RenderSceneNode node ? createTooltip(node) : null;
            }
        };
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setRootVisible(false);
        tree.setCellRenderer(new SceneNodeRenderer());
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
        JLabel selection = new JLabel("No node selected", SwingConstants.LEFT);
        tree.addTreeSelectionListener(event -> {
            Object value = ((DefaultMutableTreeNode) event.getPath().getLastPathComponent()).getUserObject();
            RenderSceneNode node = value instanceof RenderSceneNode sceneNode ? sceneNode : null;
            scene.select(node);
            selection.setText(node == null ? "No node selected" : node.label());
            tree.repaint();
        });

        JButton visibility = new JButton("Toggle Visibility");
        visibility.addActionListener(event -> {
            scene.toggleSelectedVisibility();
            RenderSceneNode selected = scene.selectedNode();
            selection.setText(selected == null ? "No node selected" : selected.label());
            tree.repaint();
        });

        JPanel controls = new JPanel();
        JButton reset = new JButton("Reset Camera");
        reset.addActionListener(event -> resetCamera.run());
        JCheckBox coordinateColors = new JCheckBox("Coordinates");
        coordinateColors.addActionListener(event -> setCoordinateColors.accept(coordinateColors.isSelected()));
        JCheckBox faces = new JCheckBox("Faces", true);
        faces.addActionListener(event -> setFaces.accept(faces.isSelected()));
        JCheckBox edges = new JCheckBox("Edges", true);
        edges.addActionListener(event -> setEdges.accept(edges.isSelected()));
        JCheckBox vertices = new JCheckBox("Vertices", true);
        vertices.addActionListener(event -> setVertices.accept(vertices.isSelected()));
        controls.add(reset);
        controls.add(coordinateColors);
        controls.add(faces);
        controls.add(edges);
        controls.add(vertices);
        controls.add(visibility);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new JScrollPane(tree), BorderLayout.CENTER);
        content.add(selection, BorderLayout.NORTH);
        content.add(controls, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.setMinimumSize(new Dimension(420, 360));
        frame.setSize(520, 600);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static DefaultMutableTreeNode createTreeNode(RenderSceneNode sceneNode) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(sceneNode);
        for (RenderSceneNode child : sceneNode.children()) {
            treeNode.add(createTreeNode(child));
        }
        return treeNode;
    }

    private static String createTooltip(RenderSceneNode node) {
        StringBuilder tooltip = new StringBuilder("<html><b>")
                .append(escapeHtml(node.label()))
                .append("</b><br>ID: ").append(node.objectId())
                .append("<br>Type: ").append(escapeHtml(node.sourceNode().nodeType))
                .append("<br>Geometry: ").append(node.hasGeometry() ? "yes" : "no")
                .append("<br>Visible: ").append(node.visible() ? "yes" : "no");
        if (!node.sourceNode().attributes.isEmpty()) {
            tooltip.append("<br><br><b>Attributes</b>");
            for (Map.Entry<String, String> attribute : node.sourceNode().attributes.entrySet()) {
                tooltip.append("<br>").append(escapeHtml(attribute.getKey())).append(": ")
                        .append(escapeHtml(attribute.getValue()));
            }
        }
        return tooltip.append("</html>").toString();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "&lt;null&gt;";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final class SceneNodeRenderer extends DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row,
                    hasFocus);
            Object object = ((DefaultMutableTreeNode) value).getUserObject();
            if (object instanceof RenderSceneNode node) {
                label.setText((node.visible() ? "[visible] " : "[hidden] ") + node.label());
            }
            return label;
        }
    }
}