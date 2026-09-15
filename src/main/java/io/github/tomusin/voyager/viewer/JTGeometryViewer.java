package io.github.tomusin.voyager.viewer;

import io.github.tomusin.lodDataRecords.CompressedVertexCoordinateArrayRecord;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Standalone OpenGL viewer that displays JT geometry using LWJGL.
 * <p>
 * Usage: {@code java io.github.tomusin.voyager.viewer.JTGeometryViewer <path-to-jt-file>}
 * <p>
 * Renders decoded vertex coordinate arrays as indexed triangle meshes when topology is available.
 * Supports mouse-drag rotation and scroll-wheel zoom.
 */
public class JTGeometryViewer {

    private long window;
    private int vao, vbo, ibo, shaderProgram;
    private int vertexCount, triangleIndexCount;
    private final Map<RenderSceneNode, GpuMesh> sceneMeshes = new IdentityHashMap<>();
    private RenderScene renderScene;
    private SceneGraphWindow sceneGraphWindow;

    // Camera
    private volatile float rotX = 25f, rotY = -35f;
    private volatile float zoom = 2.5f;
    private double lastMX, lastMY;
    private boolean dragging;
    private volatile boolean coordinateColorMode;
    private volatile boolean drawFaces = true;
    private volatile boolean drawEdges = true;
    private volatile boolean drawVertices = true;

    // Geometry centre / extent for framing
    private float cx, cy, cz, extent;

    private record GpuMesh(int vao, int vbo, int ibo, int vertexCount, int triangleIndexCount) { }

    // ───────── shaders ─────────
    private static final String VERT_SRC = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            uniform mat4 uMVP;
            out vec3 vWorldPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vWorldPos = aPos;
                gl_PointSize = 6.0;
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            in vec3 vWorldPos;
            uniform vec3 uCameraPosition;
            uniform int uCoordinateColorMode;
            uniform int uSelected;
            out vec4 fragColor;
            void main() {
                if (uCoordinateColorMode != 0) {
                    vec3 coordinateColor = (vWorldPos - vec3(%CX%, %CY%, %CZ%)) / %EXT% * 0.5 + 0.5;
                    fragColor = vec4(coordinateColor, 1.0);
                    return;
                }

                if (uSelected != 0) {
                    fragColor = vec4(1.0, 0.58, 0.10, 1.0);
                    return;
                }

                vec3 normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
                if (!gl_FrontFacing) normal = -normal;
                vec3 keyLightDirection = normalize(vec3(-0.45, 0.65, 0.55));
                vec3 fillLightDirection = normalize(vec3(0.38, -0.42, -0.58));
                vec3 viewDirection = normalize(uCameraPosition - vWorldPos);
                vec3 keyHalfVector = normalize(keyLightDirection + viewDirection);
                vec3 fillHalfVector = normalize(fillLightDirection + viewDirection);
                float diffuse = max(dot(normal, keyLightDirection), 0.0)
                    + max(dot(normal, fillLightDirection), 0.0) * 0.28;
                float specular = pow(max(dot(normal, keyHalfVector), 0.0), 72.0)
                    + pow(max(dot(normal, fillHalfVector), 0.0), 72.0) * 0.12;
                vec3 baseColor = vec3(0.48);
                vec3 metallicSpecular = mix(vec3(0.18), baseColor, 0.55);
                vec3 color = baseColor * (0.14 + diffuse * 0.86) + metallicSpecular * specular * 0.55;
                fragColor = vec4(color, 1.0);
            }
            """;

    // ───────── entry point ─────────
    public static void main(String[] args) {
        // Demo: display a 100×80×60 box (8 vertices of a cuboid)
        float[][] coords = {
            {100, 100, 100,   0,   0,   0,   0, 100},  // X
            {  0,   0,  80,   0,  80,  80,   0,   0},  // Y  (placeholder, some may be approximate)
            { 60,   0,   0,   0,   0,  60,  60,  60}   // Z
        };
        new JTGeometryViewer().launchWithCoords(coords);
    }

    /**
     * Launch the viewer with all geometry from the given tree nodes.
    * Merges all vertex arrays into one indexed triangle mesh.
     */
    public static void showAll(java.util.List<io.github.tomusin.voyager.datastructures.TreeNode> geometryNodes) {
        if (geometryNodes.isEmpty()) {
            System.out.println("No geometry nodes to render.");
            return;
        }

        List<float[][]> allCoords = new ArrayList<>();
        List<int[]> allIndices = new ArrayList<>();
        int totalVertices = 0;
        for (var node : geometryNodes) {
            float[][] coords = node.getVertexCoordinates();
            if (coords != null && coords.length >= 3 && coords[0].length > 0) {
                try {
                    int[] indices = node.getTriangleIndices();
                    if (indices == null || indices.length == 0 || indices.length % 3 != 0) continue;
                    allCoords.add(coords);
                    allIndices.add(indices);
                    totalVertices += coords[0].length;
                } catch (IllegalArgumentException exception) {
                    System.err.printf("Skipping geometry node %d: %s%n", node.objectID, exception.getMessage());
                }
            }
        }

        if (allCoords.isEmpty()) {
            System.out.println("No decodable triangle geometry found to render.");
            return;
        }

        // Merge into one coordinate buffer and adjust every node-local index.
        float[][] merged = new float[3][totalVertices];
        List<Integer> mergedIndices = new ArrayList<>();
        int offset = 0;
        for (int nodeIndex = 0; nodeIndex < allCoords.size(); nodeIndex++) {
            float[][] coords = allCoords.get(nodeIndex);
            int n = coords[0].length;
            System.arraycopy(coords[0], 0, merged[0], offset, n);
            System.arraycopy(coords[1], 0, merged[1], offset, n);
            System.arraycopy(coords[2], 0, merged[2], offset, n);
            int[] indices = allIndices.get(nodeIndex);
            if (indices != null) {
                for (int index : indices) mergedIndices.add(offset + index);
            }
            offset += n;
        }

        int[] triangles = mergedIndices.stream().mapToInt(Integer::intValue).toArray();
        System.out.printf("Rendering %d geometry nodes, %d total vertices, %d triangles%n",
                allCoords.size(), totalVertices, triangles.length / 3);
        new JTGeometryViewer().launchWithCoords(merged, triangles);
    }

    /** Launch the viewer with the parsed JT scene hierarchy retained for rendering. */
    public static void showScene(List<io.github.tomusin.voyager.datastructures.TreeNode> roots) {
        new JTGeometryViewer().launchWithScene(roots);
    }

    /**
     * Launch the viewer with geometry from a parsed {@link CompressedVertexCoordinateArrayRecord}.
     */
    public static void show(CompressedVertexCoordinateArrayRecord coordArray) {
        JTGeometryViewer viewer = new JTGeometryViewer();
        float[][] coords = coordArray.dequantize();
        viewer.launchWithCoords(coords);
    }

    // ───────── public API ─────────

    public void launchWithCoords(float[][] coords) {
        launchWithCoords(coords, new int[0]);
    }

    public void launchWithCoords(float[][] coords, int[] triangleIndices) {
        // Log vertex data for debugging
        int n = coords[0].length;
        System.out.println("=== JTGeometryViewer: " + n + " vertices ===");
//        for (int i = 0; i < n; i++) {
//            System.out.printf("  v[%d] = (%.4f, %.4f, %.4f)%n", i, coords[0][i], coords[1][i], coords[2][i]);
//        }
        init();
        uploadGeometry(coords, triangleIndices);
        loop();
        cleanup();
    }

    private void launchWithScene(List<io.github.tomusin.voyager.datastructures.TreeNode> roots) {
        renderScene = RenderScene.fromRoots(roots);
        if (renderScene.visibleGeometryNodes().isEmpty()) {
            System.out.println("No decodable triangle geometry found to render.");
            return;
        }
        init();
        uploadScene();
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            sceneGraphWindow = SceneGraphWindow.show(renderScene, this::resetCamera, this::setCoordinateColorMode, this::setDrawFaces,
                this::setDrawEdges, this::setDrawVertices);
        }
        loop();
        cleanup();
    }

    // ───────── GLFW / OpenGL init ─────────

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialise GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(1024, 768, "JT Geometry Viewer", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        // Input callbacks
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            dragging = button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS;
            if (dragging) {
                double[] mx = new double[1], my = new double[1];
                glfwGetCursorPos(w, mx, my);
                lastMX = mx[0]; lastMY = my[0];
            }
        });
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (dragging) {
                rotY += (float)(xpos - lastMX) * 0.4f;
                rotX += (float)(ypos - lastMY) * 0.4f;
                lastMX = xpos; lastMY = ypos;
            }
        });
        glfwSetScrollCallback(window, (w, xoff, yoff) ->
            zoom = Math.max(0.2f, zoom - (float) yoff * 0.15f));

        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(w, true);
            if (key == GLFW_KEY_C && action == GLFW_RELEASE) {
                coordinateColorMode = !coordinateColorMode;
                System.out.println(coordinateColorMode ? "Coordinate color mode" : "Blinn-Phong lighting mode");
            }
            if (key == GLFW_KEY_F && action == GLFW_RELEASE) drawFaces = !drawFaces;
            if (key == GLFW_KEY_E && action == GLFW_RELEASE) drawEdges = !drawEdges;
            if (key == GLFW_KEY_V && action == GLFW_RELEASE) drawVertices = !drawVertices;
            if (key == GLFW_KEY_R && action == GLFW_RELEASE) resetCamera();
            if (renderScene != null && key == GLFW_KEY_UP && action == GLFW_RELEASE) renderScene.selectNext(-1);
            if (renderScene != null && key == GLFW_KEY_DOWN && action == GLFW_RELEASE) renderScene.selectNext(1);
            if (renderScene != null && key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                renderScene.toggleSelectedVisibility();
            }
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_PROGRAM_POINT_SIZE);
        glClearColor(0.12f, 0.12f, 0.15f, 1f);
    }

    // ───────── geometry upload ─────────

    private void uploadGeometry(float[][] coords, int[] triangleIndices) {
        int n = coords[0].length; // vertex count
        vertexCount = n;

        // Compute bounding box
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float x = coords[0][i], y = coords[1][i], z = coords[2][i];
            // Skip non-finite values (Infinity/NaN from bad dequantization)
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        // If no finite vertices found, use defaults
        if (!Float.isFinite(minX)) { minX = -1f; maxX = 1f; }
        if (!Float.isFinite(minY)) { minY = -1f; maxY = 1f; }
        if (!Float.isFinite(minZ)) { minZ = -1f; maxZ = 1f; }
        cx = (minX + maxX) / 2f;
        cy = (minY + maxY) / 2f;
        cz = (minZ + maxZ) / 2f;
        extent = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ);
        if (extent < 1e-6f) extent = 1f;

        // Interleave into float[] {x,y,z, x,y,z, ...}
        float[] verts = new float[n * 3];
        for (int i = 0; i < n; i++) {
            verts[i * 3]     = Float.isFinite(coords[0][i]) ? coords[0][i] : 0f;
            verts[i * 3 + 1] = Float.isFinite(coords[1][i]) ? coords[1][i] : 0f;
            verts[i * 3 + 2] = Float.isFinite(coords[2][i]) ? coords[2][i] : 0f;
        }

        triangleIndexCount = triangleIndices.length;

        // Upload
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        if (triangleIndexCount > 0) {
            ibo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, triangleIndices, GL_STATIC_DRAW);
        }

        glBindVertexArray(0);

        // Compile shaders with baked-in centre / extent
        // Guard against non-finite values that would produce invalid GLSL
        if (!Float.isFinite(cx)) cx = 0f;
        if (!Float.isFinite(cy)) cy = 0f;
        if (!Float.isFinite(cz)) cz = 0f;
        if (!Float.isFinite(extent) || extent < 1e-6f) extent = 1f;
        String vs = substituteShaderConstants(VERT_SRC);
        String fs = substituteShaderConstants(FRAG_SRC);
        shaderProgram = createProgram(vs, fs);
    }

    private void uploadScene() {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (RenderSceneNode node : renderScene.visibleGeometryNodes()) {
            RenderMesh mesh = node.mesh();
            float[] positions = mesh.positions();
            for (int index = 0; index < positions.length; index += 3) {
                minX = Math.min(minX, positions[index]);
                maxX = Math.max(maxX, positions[index]);
                minY = Math.min(minY, positions[index + 1]);
                maxY = Math.max(maxY, positions[index + 1]);
                minZ = Math.min(minZ, positions[index + 2]);
                maxZ = Math.max(maxZ, positions[index + 2]);
            }
            sceneMeshes.put(node, uploadMesh(mesh));
        }
        cx = (minX + maxX) / 2f;
        cy = (minY + maxY) / 2f;
        cz = (minZ + maxZ) / 2f;
        extent = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ);
        if (!Float.isFinite(extent) || extent < 1e-6f) extent = 1f;
        shaderProgram = createProgram(substituteShaderConstants(VERT_SRC), substituteShaderConstants(FRAG_SRC));
    }

    private GpuMesh uploadMesh(RenderMesh mesh) {
        int meshVao = glGenVertexArrays();
        glBindVertexArray(meshVao);
        int meshVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, meshVbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.positions(), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        int meshIbo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, meshIbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.triangleIndices(), GL_STATIC_DRAW);
        glBindVertexArray(0);
        return new GpuMesh(meshVao, meshVbo, meshIbo, mesh.vertexCount(), mesh.triangleIndices().length);
    }

    private String substituteShaderConstants(String shaderSource) {
        return shaderSource
                .replace("%CX%", String.valueOf(cx))
                .replace("%CY%", String.valueOf(cy))
                .replace("%CZ%", String.valueOf(cz))
                .replace("%EXT%", String.valueOf(extent));
    }

    // ───────── render loop ─────────

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            int[] w = new int[1], h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            glViewport(0, 0, w[0], h[0]);
            float aspect = (float) w[0] / Math.max(h[0], 1);

            float camDist = extent * zoom;
            Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(45), aspect,
                    camDist * 0.01f, camDist * 10f);
            Matrix4f view = new Matrix4f()
                    .translate(0, 0, -camDist)
                    .rotateX((float) Math.toRadians(rotX))
                    .rotateY((float) Math.toRadians(rotY))
                    .translate(-cx, -cy, -cz);
            Matrix4f mvp = new Matrix4f();
            proj.mul(view, mvp);

            glUseProgram(shaderProgram);
            try (MemoryStack stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                mvp.get(fb);
                glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "uMVP"), false, fb);
            }
            Matrix4f inverseView = new Matrix4f(view).invert();
            Vector3f cameraPosition = inverseView.getTranslation(new Vector3f());
            glUniform3f(glGetUniformLocation(shaderProgram, "uCameraPosition"),
                    cameraPosition.x, cameraPosition.y, cameraPosition.z);
            glUniform1i(glGetUniformLocation(shaderProgram, "uCoordinateColorMode"), coordinateColorMode ? 1 : 0);

            if (renderScene == null) {
                drawMesh(new GpuMesh(vao, vbo, ibo, vertexCount, triangleIndexCount), false);
            } else {
                for (RenderSceneNode node : renderScene.visibleGeometryNodes()) {
                    GpuMesh mesh = sceneMeshes.get(node);
                    if (mesh != null) drawMesh(mesh, node.selected());
                }
            }
            glUseProgram(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void drawMesh(GpuMesh mesh, boolean selected) {
        glBindVertexArray(mesh.vao());
        glUniform1i(glGetUniformLocation(shaderProgram, "uSelected"), selected ? 1 : 0);
        if (mesh.triangleIndexCount() > 0 && drawFaces) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glDrawElements(GL_TRIANGLES, mesh.triangleIndexCount(), GL_UNSIGNED_INT, 0);
        }
        if (mesh.triangleIndexCount() > 0 && drawEdges) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glLineWidth(2f);
            glDrawElements(GL_TRIANGLES, mesh.triangleIndexCount(), GL_UNSIGNED_INT, 0);
        }
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        if (drawVertices) glDrawArrays(GL_POINTS, 0, mesh.vertexCount());
        glBindVertexArray(0);
    }

    private void resetCamera() {
        rotX = 25f;
        rotY = -35f;
        zoom = 2.5f;
    }

    private void setCoordinateColorMode(boolean enabled) {
        coordinateColorMode = enabled;
    }

    private void setDrawFaces(boolean enabled) {
        drawFaces = enabled;
    }

    private void setDrawEdges(boolean enabled) {
        drawEdges = enabled;
    }

    private void setDrawVertices(boolean enabled) {
        drawVertices = enabled;
    }

    // ───────── cleanup ─────────

    private void cleanup() {
        if (sceneGraphWindow != null) sceneGraphWindow.close();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        if (ibo != 0) glDeleteBuffers(ibo);
        for (GpuMesh mesh : sceneMeshes.values()) {
            glDeleteVertexArrays(mesh.vao());
            glDeleteBuffers(mesh.vbo());
            glDeleteBuffers(mesh.ibo());
        }
        sceneMeshes.clear();
        glDeleteProgram(shaderProgram);

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    // ───────── shader helpers ─────────

    private static int createProgram(String vertSrc, String fragSrc) {
        int vs = compileShader(GL_VERTEX_SHADER, vertSrc);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link failed: " + glGetProgramInfoLog(prog));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String src) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
