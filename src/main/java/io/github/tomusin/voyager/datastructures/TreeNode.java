package io.github.tomusin.voyager.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeNode {
    public int objectID;
    public String nodeType;
    public List<TreeNode> children = new ArrayList<>();
    public Map<String, String> attributes = new HashMap<>();
    public String nodeName;

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
}
