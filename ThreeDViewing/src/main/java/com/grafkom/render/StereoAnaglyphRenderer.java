package com.grafkom.render;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.PerspectiveProjection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Indikator 3 - Stereo View dengan Anaglyph Red-Cyan.
 *
 * Teknik yang dipakai: Off-Axis Projection (Asymmetric Frustum) + Color Masking.
 *  - Mata kiri  → hanya saluran MERAH  (glColorMask(true,  false, false, true))
 *  - Mata kanan → saluran HIJAU+BIRU (cyan) (glColorMask(false, true,  true,  true))
 *  - Kedua mata digeser ±IPD/2 sepanjang vektor kanan kamera (u) dengan sumbu
 *    pandang PARALEL, lalu frustum masing-masing digeser (asimetris) agar
 *    berimpit di bidang konvergensi → tidak ada vertical parallax.
 *
 * Mode Toe-In juga disediakan sebagai pembanding (kedua mata "menoleh" ke titik
 * konvergensi dengan frustum simetris) sesuai materi rubrik.
 *
 * Parameter IPD dan jarak konvergensi bersifat dinamis (bisa diubah saat runtime).
 */
public class StereoAnaglyphRenderer {

    public enum StereoMode { OFF_AXIS, TOE_IN }

    public enum Parallax { ZERO, POSITIVE, NEGATIVE }

    public static final float MIN_IPD = 0.0f;
    public static final float MAX_IPD = 1.0f;
    public static final float MIN_CONVERGENCE_FACTOR = 0.4f;
    public static final float MAX_CONVERGENCE_FACTOR = 2.5f;

    private boolean enabled = false;
    private StereoMode mode = StereoMode.OFF_AXIS;
    private float ipd = 0.2f;                 // Interpupillary Distance (satuan dunia)
    private float convergenceFactor = 1.0f;   // jarak konvergensi = factor * jarak kamera-target
    private boolean swapEyes = false;         // bila kacamata dipakai terbalik (kiri cyan)

    /**
     * Render dua pass (mata kiri & kanan) ke color buffer yang sama.
     *
     * @param camera              kamera utama (posisi tengah kedua mata)
     * @param projection          parameter frustum (FOV, aspect, near, far)
     * @param renderSceneCallback callback yang menggambar scene memakai matriks MODELVIEW saat ini
     */
    public void renderAnaglyph(Camera3D camera, PerspectiveProjection projection, Runnable renderSceneCallback) {
        // Bersihkan seluruh color buffer sekali saja di awal (kedua pass akan di-blend lewat color mask)
        glColorMask(true, true, true, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // PASS 1: Mata Kiri - saluran merah
        renderEye(-1, camera, projection, renderSceneCallback);
        // PASS 2: Mata Kanan - saluran cyan (hijau + biru)
        renderEye(+1, camera, projection, renderSceneCallback);

        // Reset Color Mask ke kondisi normal
        glColorMask(true, true, true, true);
    }

    private void renderEye(int side, Camera3D camera, PerspectiveProjection projection, Runnable scene) {
        boolean redChannel = (side < 0) != swapEyes;
        if (redChannel) glColorMask(true, false, false, true);
        else            glColorMask(false, true, true, true);
        glClear(GL_DEPTH_BUFFER_BIT); // depth dibersihkan, color buffer dipertahankan (di-blend)

        float convergence = getConvergenceDistance(camera);
        float eyeOffset = side * ipd / 2.0f;

        Vector3f right = camera.getRightVector();
        Vector3f shift = new Vector3f(right).mul(eyeOffset);
        Vector3f eyePos = camera.getEye().add(shift);

        Matrix4f proj;
        Vector3f lookAt;
        if (mode == StereoMode.OFF_AXIS) {
            // Sumbu pandang paralel: target ikut digeser, frustum dibuat asimetris
            lookAt = camera.getTarget().add(shift);
            proj = projection.calculateOffAxisMatrix(eyeOffset, convergence);
        } else {
            // Toe-in: kedua mata menoleh ke titik konvergensi, frustum simetris
            lookAt = camera.getEye().add(camera.getForwardVector().mul(convergence));
            proj = projection.calculateProjectionMatrix();
        }
        Matrix4f view = Camera3D.calculateLookAtMatrix(eyePos, lookAt, camera.getUp());

        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(proj.get(new float[16]));
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(view.get(new float[16]));

        scene.run(); // Eksekusi callback render
    }

    /** Jarak bidang layar (zero parallax) dari kamera. */
    public float getConvergenceDistance(Camera3D camera) {
        return Math.max(0.2f, camera.getRadius() * convergenceFactor);
    }

    /** Posisi target (pusat objek) relatif terhadap bidang layar. */
    public Parallax getTargetParallax() {
        if (Math.abs(convergenceFactor - 1.0f) < 0.025f) return Parallax.ZERO;
        // bidang konvergensi lebih dekat dari objek → objek di belakang layar
        return convergenceFactor < 1.0f ? Parallax.POSITIVE : Parallax.NEGATIVE;
    }

    // ---------------- Getter / Setter ----------------

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { enabled = !enabled; }

    public StereoMode getMode() { return mode; }
    public void toggleMode() { mode = (mode == StereoMode.OFF_AXIS) ? StereoMode.TOE_IN : StereoMode.OFF_AXIS; }

    public float getIpd() { return ipd; }
    public void setIpd(float ipd) { this.ipd = Math.max(MIN_IPD, Math.min(MAX_IPD, ipd)); }

    public float getConvergenceFactor() { return convergenceFactor; }
    public void setConvergenceFactor(float f) {
        this.convergenceFactor = Math.max(MIN_CONVERGENCE_FACTOR, Math.min(MAX_CONVERGENCE_FACTOR, f));
    }

    public boolean isSwapEyes() { return swapEyes; }
    public void toggleSwapEyes() { swapEyes = !swapEyes; }
}
