package com.grafkom.render;

import static org.lwjgl.opengl.GL11.*;

/**
 * Indikator 3 - Hidden Surface Removal (Z-Buffer + Back-Face Culling).
 *
 * <ul>
 *   <li><b>Z-Buffer</b> (tombol Z): GL_DEPTH_TEST + GL_LESS. Fragment hanya menimpa pixel bila z_new &lt; Z_Buffer[x,y].
 *       Dimatikan → hasil hanya ditentukan urutan gambar (painter's algorithm): sisi belakang bisa menimpa sisi depan.</li>
 *   <li><b>Back-Face Culling</b> (tombol C): GL_CULL_FACE, buang segitiga yang berputar CW (membelakangi kamera);
 *       seluruh mesh memakai urutan CCW untuk sisi depan.</li>
 * </ul>
 *
 * Penanganan Z-fighting: buffer depth 24-bit (GLFW_DEPTH_BITS), near plane tidak dibuat mendekati 0
 * (default 0.1), dan geometri yang coplanar (grid di atas lantai, outline di atas face) memakai
 * glPolygonOffset - lihat SceneRenderer.
 */
public class HSRManager {

    private boolean depthTestEnabled = true;
    private boolean backFaceCullingEnabled = true;

    public static void setupDepthTest(boolean enable) {
        if (enable) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);   // pixel lebih dekat lolos uji depth
        } else {
            glDisable(GL_DEPTH_TEST);
        }
    }

    public static void setupBackFaceCulling(boolean enable) {
        if (enable) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);    // buang polygon belakang
            glFrontFace(GL_CCW);    // counter-clockwise = tampak depan
        } else {
            glDisable(GL_CULL_FACE);
        }
    }

    /** Terapkan state HSR saat ini ke OpenGL (dipanggil di awal setiap render scene). */
    public void apply() {
        setupDepthTest(depthTestEnabled);
        setupBackFaceCulling(backFaceCullingEnabled);
        glDepthMask(true);
    }

    public boolean isDepthTestEnabled() { return depthTestEnabled; }
    public boolean isBackFaceCullingEnabled() { return backFaceCullingEnabled; }
    public void toggleDepthTest() { depthTestEnabled = !depthTestEnabled; }
    public void toggleBackFaceCulling() { backFaceCullingEnabled = !backFaceCullingEnabled; }
    public void setDepthTestEnabled(boolean v) { depthTestEnabled = v; }
    public void setBackFaceCullingEnabled(boolean v) { backFaceCullingEnabled = v; }
}
