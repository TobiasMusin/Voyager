package io.github.tomusin.lodDataRecords;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.tomusin.voyager.datastructures.VecI32;

/** Reconstructs primal mesh faces from the Annex D encoded dual VFMesh. */
public final class DualVFMeshTopologyDecoder {

    private final VecI32[] faceDegrees;
    private final VecI32 vertexValences;
    private final VecI32 vertexGroups;
    private final VecI32 vertexFlags;
    private final int[] degreeReadPositions = new int[8];
    private int valenceReadPosition;
    private int groupReadPosition;
    private int flagReadPosition;
    private int splitFaceReadPosition;
    private int splitPositionReadPosition;
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();
    private final List<Integer> activeFaces = new ArrayList<>();
    private final boolean[] removedFaces;
    private final VecI32 splitFaceSyms;
    private final VecI32 splitFacePositions;

    private DualVFMeshTopologyDecoder(TopologicallyCompressedRepDataRecord data) {
        faceDegrees = data.faceDegrees();
        vertexValences = data.vertexValences();
        vertexGroups = data.vertexGroups();
        vertexFlags = data.vertexFlags();
        splitFaceSyms = data.splitFaceSyms();
        splitFacePositions = data.splitFacePositions();
        removedFaces = new boolean[Math.max(1, vertexValences.count() * 8)];
    }

    public static int[] decodeTriangleIndices(TopologicallyCompressedRepDataRecord data, int vertexCount) {
        if (data == null || data.faceDegrees() == null || data.vertexValences() == null
                || data.vertexGroups() == null || data.vertexFlags() == null) {
            throw new IllegalArgumentException("Topology streams are incomplete");
        }
        DualVFMeshTopologyDecoder decoder = new DualVFMeshTopologyDecoder(data);
        decoder.decode();
        return decoder.toPrimalTriangleIndices(vertexCount);
    }

    private void decode() {
        while (valenceReadPosition < vertexValences.count()) {
            int seedVertex = newVertex();
            for (int faceSlot = 0; faceSlot < vertices.get(seedVertex).faces.length; faceSlot++) {
                activateFace(seedVertex, faceSlot);
            }
            int activeFace;
            while ((activeFace = nextActiveFace()) != -1) {
                completeFace(activeFace);
                removedFaces[activeFace] = true;
            }
        }
        assertStreamsConsumed();
    }

    private int newVertex() {
        if (valenceReadPosition >= vertexValences.count()) {
            throw new IllegalArgumentException("Topology requested more vertex valences than were encoded");
        }
        int valence = vertexValences.valueArray()[valenceReadPosition++];
        if (valence <= 0) {
            throw new IllegalArgumentException("Invalid decoded vertex valence: " + valence);
        }
        int group = nextValue(vertexGroups, groupReadPosition++, "vertex group");
        int flags = nextValue(vertexFlags, flagReadPosition++, "vertex flag");
        vertices.add(new Vertex(valence, group, flags));
        return vertices.size() - 1;
    }

    private void activateFace(int vertexIndex, int faceSlot) {
        int degree = nextDegree(faceContext(vertexIndex));
        if (degree > 0) {
            Face face = new Face(degree);
            faces.add(face);
            int faceIndex = faces.size() - 1;
            setVertexFace(vertexIndex, faceSlot, faceIndex);
            setFaceVertex(faceIndex, 0, vertexIndex);
            activeFaces.add(faceIndex);
            return;
        }
        if (degree != 0) {
            throw new IllegalArgumentException("Invalid decoded face degree: " + degree);
        }
        int splitFace = nextSplitFace();
        int splitPosition = nextSplitPosition();
        setVertexFace(vertexIndex, faceSlot, splitFace);
        addVertexToFace(vertexIndex, faceSlot, splitFace, splitPosition);
    }

    private void completeFace(int faceIndex) {
        Face face = faces.get(faceIndex);
        int faceSlot;
        while ((faceSlot = findVertexSlot(face, -1)) != -1) {
            int vertexIndex = newVertex();
            setVertexFace(vertexIndex, 0, faceIndex);
            addVertexToFace(vertexIndex, 0, faceIndex, faceSlot);
            completeVertex(vertexIndex, faceSlot);
        }
    }

    private void completeVertex(int vertexIndex, int faceSlot) {
        Vertex vertex = vertices.get(vertexIndex);
        int previousFace = vertex.faces[0];
        int previousSlot = faceSlot;
        int slot = 1;
        while (slot < vertex.faces.length && vertex.faces[slot] != -1) {
            previousSlot = decrement(previousSlot, faces.get(previousFace).vertices.length);
            int neighbouringVertex = faces.get(previousFace).vertices[previousSlot];
            if (neighbouringVertex == -1) break;
            int nextFace = vertex.faces[slot];
            int nextSlot = findVertexSlot(faces.get(nextFace), neighbouringVertex);
            if (nextSlot < 0) throw new IllegalArgumentException("Broken dual VFMesh face ring");
            previousFace = nextFace;
            previousSlot = decrement(nextSlot, faces.get(nextFace).vertices.length);
            addVertexToFace(vertexIndex, slot, nextFace, previousSlot);
            slot++;
        }

        int firstUnresolvedSlot = slot;
        previousFace = vertex.faces[0];
        previousSlot = faceSlot;
        slot = vertex.faces.length - 1;
        while (slot >= firstUnresolvedSlot && vertex.faces[slot] != -1) {
            previousSlot = increment(previousSlot, faces.get(previousFace).vertices.length);
            int neighbouringVertex = faces.get(previousFace).vertices[previousSlot];
            if (neighbouringVertex == -1) break;
            int nextFace = vertex.faces[slot];
            int nextSlot = findVertexSlot(faces.get(nextFace), neighbouringVertex);
            if (nextSlot < 0) throw new IllegalArgumentException("Broken dual VFMesh face ring");
            previousFace = nextFace;
            previousSlot = increment(nextSlot, faces.get(nextFace).vertices.length);
            addVertexToFace(vertexIndex, slot, nextFace, previousSlot);
            slot--;
        }

        for (; firstUnresolvedSlot <= slot; firstUnresolvedSlot++) {
            activateFace(vertexIndex, firstUnresolvedSlot);
        }
    }

    private void addVertexToFace(int vertexIndex, int vertexFaceSlot, int faceIndex, int faceVertexSlot) {
        setFaceVertex(faceIndex, faceVertexSlot, vertexIndex);
        Face face = faces.get(faceIndex);

        int clockwiseFaceSlot = decrement(faceVertexSlot, face.vertices.length);
        int clockwiseVertex = face.vertices[clockwiseFaceSlot];
        if (clockwiseVertex != -1) {
            int clockwiseVertexFaceSlot = findFaceSlot(vertices.get(clockwiseVertex), faceIndex);
            int targetVertexFaceSlot = increment(vertexFaceSlot, vertices.get(vertexIndex).faces.length);
            if (vertices.get(vertexIndex).faces[targetVertexFaceSlot] == -1) {
                int targetFaceSlot = decrement(clockwiseVertexFaceSlot, vertices.get(clockwiseVertex).faces.length);
                setVertexFace(vertexIndex, targetVertexFaceSlot, vertices.get(clockwiseVertex).faces[targetFaceSlot]);
            }
        }

        int counterClockwiseFaceSlot = increment(faceVertexSlot, face.vertices.length);
        int counterClockwiseVertex = face.vertices[counterClockwiseFaceSlot];
        if (counterClockwiseVertex != -1) {
            int counterClockwiseVertexFaceSlot = findFaceSlot(vertices.get(counterClockwiseVertex), faceIndex);
            int targetVertexFaceSlot = decrement(vertexFaceSlot, vertices.get(vertexIndex).faces.length);
            if (vertices.get(vertexIndex).faces[targetVertexFaceSlot] == -1) {
                int targetFaceSlot = increment(counterClockwiseVertexFaceSlot, vertices.get(counterClockwiseVertex).faces.length);
                setVertexFace(vertexIndex, targetVertexFaceSlot, vertices.get(counterClockwiseVertex).faces[targetFaceSlot]);
            }
        }
    }

    private int[] toPrimalTriangleIndices(int vertexCount) {
        List<Integer> indices = new ArrayList<>();
        for (Vertex vertex : vertices) {
            if (vertex.flags != 0) continue;
            if (vertex.faces.length < 3) throw new IllegalArgumentException("Primal face has fewer than three vertices");
            for (int vertexIndex : vertex.faces) {
                if (vertexIndex < 0 || vertexIndex >= vertexCount) {
                    throw new IllegalArgumentException("Topology index outside coordinate range: " + vertexIndex);
                }
            }
            for (int index = 1; index < vertex.faces.length - 1; index++) {
                indices.add(vertex.faces[0]);
                indices.add(vertex.faces[index]);
                indices.add(vertex.faces[index + 1]);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private int nextDegree(int context) {
        if (context < 0 || context >= faceDegrees.length || degreeReadPositions[context] >= faceDegrees[context].count()) {
            throw new IllegalArgumentException("Topology exhausted face-degree context " + context
                    + " after " + Arrays.toString(degreeReadPositions)
                    + "; stream counts=" + Arrays.toString(Arrays.stream(faceDegrees).mapToInt(VecI32::count).toArray())
                    + "; valences=" + Arrays.toString(vertexValences.valueArray()));
        }
        return faceDegrees[context].valueArray()[degreeReadPositions[context]++];
    }

    private int nextSplitFace() {
        int offset = nextValue(splitFaceSyms, splitFaceReadPosition++, "split face");
        if (offset <= 0 || offset > activeFaces.size()) throw new IllegalArgumentException("Invalid split face offset: " + offset);
        return activeFaces.get(activeFaces.size() - offset);
    }

    private int nextSplitPosition() {
        return nextValue(splitFacePositions, splitPositionReadPosition++, "split position");
    }

    private int faceContext(int vertexIndex) {
        Vertex vertex = vertices.get(vertexIndex);
        int knownFaces = 0;
        int knownDegree = 0;
        for (int faceIndex : vertex.faces) {
            if (faceIndex >= 0) {
                knownFaces++;
                knownDegree += faces.get(faceIndex).vertices.length;
            }
        }
        if (vertex.faces.length == 3) return knownDegree < knownFaces * 6 ? 0 : knownDegree == knownFaces * 6 ? 1 : 2;
        if (vertex.faces.length == 4) return knownDegree < knownFaces * 4 ? 3 : knownDegree == knownFaces * 4 ? 4 : 5;
        return vertex.faces.length == 5 ? 6 : 7;
    }

    private int nextActiveFace() {
        int candidate = -1;
        int lowestEmptyCount = Integer.MAX_VALUE;
        for (int index = activeFaces.size() - 1; index >= Math.max(0, activeFaces.size() - 16); index--) {
            int faceIndex = activeFaces.get(index);
            if (removedFaces[faceIndex]) continue;
            int emptyCount = emptyVertexSlots(faces.get(faceIndex));
            if (emptyCount < lowestEmptyCount) {
                candidate = faceIndex;
                lowestEmptyCount = emptyCount;
            }
        }
        return candidate;
    }

    private void assertStreamsConsumed() {
        if (groupReadPosition != vertexGroups.count() || flagReadPosition != vertexFlags.count()
                || splitFaceReadPosition != count(splitFaceSyms) || splitPositionReadPosition != count(splitFacePositions)
                || !Arrays.equals(degreeReadPositions, Arrays.stream(faceDegrees).mapToInt(VecI32::count).toArray())) {
            throw new IllegalArgumentException("Topology decoder did not consume every encoded symbol");
        }
    }

    private static int count(VecI32 values) { return values == null ? 0 : values.count(); }
    private static int nextValue(VecI32 values, int position, String name) {
        if (values == null || position >= values.count()) throw new IllegalArgumentException("Topology exhausted " + name + " symbols");
        return values.valueArray()[position];
    }
    private static int findVertexSlot(Face face, int vertexIndex) { for (int index = 0; index < face.vertices.length; index++) if (face.vertices[index] == vertexIndex) return index; return -1; }
    private static int findFaceSlot(Vertex vertex, int faceIndex) { for (int index = 0; index < vertex.faces.length; index++) if (vertex.faces[index] == faceIndex) return index; return -1; }
    private static int emptyVertexSlots(Face face) { int count = 0; for (int vertex : face.vertices) if (vertex == -1) count++; return count; }
    private static int increment(int value, int modulus) { return (value + 1) % modulus; }
    private static int decrement(int value, int modulus) { return (value + modulus - 1) % modulus; }
    private void setVertexFace(int vertexIndex, int faceSlot, int faceIndex) { vertices.get(vertexIndex).faces[faceSlot] = faceIndex; }
    private void setFaceVertex(int faceIndex, int vertexSlot, int vertexIndex) { faces.get(faceIndex).vertices[vertexSlot] = vertexIndex; }

    private static final class Vertex {
        private final int[] faces;
        private final int group;
        private final int flags;
        private Vertex(int valence, int group, int flags) { this.faces = new int[valence]; Arrays.fill(faces, -1); this.group = group; this.flags = flags; }
    }

    private static final class Face {
        private final int[] vertices;
        private Face(int degree) { if (degree < 3) throw new IllegalArgumentException("Invalid decoded face degree: " + degree); this.vertices = new int[degree]; Arrays.fill(vertices, -1); }
    }
}