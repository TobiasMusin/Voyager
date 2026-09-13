package io.github.tomusin.Voyager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.tomusin.voyager.readers.JTReader;

class ShapeLODLinkingTest {

    @Test
    void shapeLodSegmentLinksToReferencingShapeNode() {
        JTReader reader = new JTReader();
        reader.startReading(Path.of("src", "main", "resources", "example_block_jt10.3.jt").toAbsolutePath());

        assertNotNull(reader.getTreeNodeMap().get(8).getLodGeometry());
        assertFalse(reader.getTreeNodeMap().get(0).hasGeometry());
    }
}