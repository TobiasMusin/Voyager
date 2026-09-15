package io.github.tomusin.voyager.viewer;

import java.util.Arrays;

/** Immutable decoded mesh data ready for upload to OpenGL. */
public record RenderMesh(float[] positions, int[] triangleIndices) {
    public RenderMesh {
        positions = Arrays.copyOf(positions, positions.length);
        triangleIndices = Arrays.copyOf(triangleIndices, triangleIndices.length);
    }

    @Override
    public float[] positions() {
        return Arrays.copyOf(positions, positions.length);
    }

    @Override
    public int[] triangleIndices() {
        return Arrays.copyOf(triangleIndices, triangleIndices.length);
    }

    public int vertexCount() {
        return positions.length / 3;
    }
}