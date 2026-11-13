package com.grafkom.model;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Indikator "Mesh 3D Bermateri": mesh segitiga dengan Vektor Normal Vertex dan Koordinat Tekstur UV.
 *
 * Layout vertex (12 float = 48 byte):
 * <pre>
 *   location 0 : posisi  (x, y, z)
 *   location 1 : normal  (nx, ny, nz)
 *   location 2 : UV      (u, v)
 *   location 3 : tint    (r, g, b, bobot tekstur)
 * </pre>
 *
 * Satu mesh menyimpan DUA representasi geometri yang sama (dibangun oleh {@link MeshBuilder}):
 * <ul>
 *   <li><b>Flat</b>   : tiap segitiga punya 3 vertex sendiri dengan normal FACE (non-indexed, glDrawArrays).</li>
 *   <li><b>Smooth</b> : vertex berbagi lewat index buffer, normal = rata-rata normal semua face yang
 *       bertemu di titik itu / vertex normal averaging (indexed, glDrawElements).</li>
 * </ul>
 * Tombol F cukup memilih salah satu VAO. Data CPU dapat dibaca tanpa konteks OpenGL (untuk unit test);
 * resource GPU baru dibuat saat render pertama.
 */
public class TexturedMesh {

    public static final int FLOATS_PER_VERTEX = 12;
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    private final String name;
    private final float[] flatVertices;
    private final float[] smoothVertices;
    private final int[] smoothIndices;

    private boolean uploaded = false;
    private int flatVao, flatVbo;
    private int smoothVao, smoothVbo, smoothEbo;

    TexturedMesh(String name, float[] flatVertices, float[] smoothVertices, int[] smoothIndices) {
        this.name = name;
        this.flatVertices = flatVertices;
        this.smoothVertices = smoothVertices;
        this.smoothIndices = smoothIndices;
    }

    public String getName() { return name; }
    public int getTriangleCount() { return smoothIndices.length / 3; }
    public int getSmoothVertexCount() { return smoothVertices.length / FLOATS_PER_VERTEX; }
    public int getFlatVertexCount() { return flatVertices.length / FLOATS_PER_VERTEX; }

    /** Data CPU (read-only, untuk pengujian). */
    public float[] getFlatVertices() { return flatVertices; }
    public float[] getSmoothVertices() { return smoothVertices; }
    public int[] getSmoothIndices() { return smoothIndices; }

    /** Gambar mesh. smooth = true → vertex normal averaging; false → face normal (faceted). */
    public void render(boolean smooth) {
        if (!uploaded) upload();
        if (smooth) {
            glBindVertexArray(smoothVao);
            glDrawElements(GL_TRIANGLES, smoothIndices.length, GL_UNSIGNED_INT, 0L);
        } else {
            glBindVertexArray(flatVao);
            glDrawArrays(GL_TRIANGLES, 0, getFlatVertexCount());
        }
        glBindVertexArray(0);
    }

    private void upload() {
        // ---- Flat ----
        flatVao = glGenVertexArrays();
        flatVbo = glGenBuffers();
        glBindVertexArray(flatVao);
        glBindBuffer(GL_ARRAY_BUFFER, flatVbo);
        glBufferData(GL_ARRAY_BUFFER, toBuffer(flatVertices), GL_STATIC_DRAW);
        setupAttributes();

        // ---- Smooth (indexed) ----
        smoothVao = glGenVertexArrays();
        smoothVbo = glGenBuffers();
        smoothEbo = glGenBuffers();
        glBindVertexArray(smoothVao);
        glBindBuffer(GL_ARRAY_BUFFER, smoothVbo);
        glBufferData(GL_ARRAY_BUFFER, toBuffer(smoothVertices), GL_STATIC_DRAW);
        setupAttributes();
        IntBuffer idx = BufferUtils.createIntBuffer(smoothIndices.length);
        idx.put(smoothIndices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, smoothEbo); // tersimpan di state VAO
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        uploaded = true;
    }

    private static void setupAttributes() {
        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, STRIDE, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, STRIDE, 8L * Float.BYTES);
        glEnableVertexAttribArray(3);
    }

    private static FloatBuffer toBuffer(float[] data) {
        FloatBuffer fb = BufferUtils.createFloatBuffer(data.length);
        fb.put(data).flip();
        return fb;
    }

    /** Bebaskan resource GPU. */
    public void dispose() {
        if (!uploaded) return;
        glDeleteVertexArrays(flatVao);
        glDeleteVertexArrays(smoothVao);
        glDeleteBuffers(flatVbo);
        glDeleteBuffers(smoothVbo);
        glDeleteBuffers(smoothEbo);
        uploaded = false;
    }
}
