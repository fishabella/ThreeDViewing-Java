package com.grafkom.projection;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Indikator 4 - Taksonomi Proyeksi.
 *
 * <pre>
 * PROYEKSI
 * ├── PARALEL
 * │   ├── Ortografik (multiview: tampak depan)      [1]
 * │   ├── Aksonometrik → Isometrik                  [2]
 * │   └── Oblique → Cavalier (k = 1)                [3]
 * │               → Cabinet  (k = 1/2)              [4]
 * └── PERSPEKTIF
 *     ├── 1 Titik Hilang                            [5]
 *     ├── 2 Titik Hilang                            [6]
 *     └── 3 Titik Hilang                            [7]
 * </pre>
 *
 * Jumlah titik hilang pada perspektif ditentukan oleh orientasi kamera terhadap
 * sumbu utama objek, sehingga setiap jenis membawa "preset" sudut kamera.
 * Semua matriks proyeksi (ortho, shear oblique, frustum) dihitung manual.
 */
public class ProjectionTaxonomyManager {

    public enum ProjectionType {
        ORTHOGRAPHIC      ("Paralel > Ortografik (Tampak Depan)",            true,   0.0f,  0.0f),
        ISOMETRIC         ("Paralel > Aksonometrik > Isometrik",             true,  45.0f, 35.264f),
        OBLIQUE_CAVALIER  ("Paralel > Oblique > Cavalier (k=1, sudut 45)",   true,   0.0f,  0.0f),
        OBLIQUE_CABINET   ("Paralel > Oblique > Cabinet (k=1/2, sudut 45)",  true,   0.0f,  0.0f),
        PERSPECTIVE_1POINT("Perspektif > 1 Titik Hilang",                    false,  0.0f,  0.0f),
        PERSPECTIVE_2POINT("Perspektif > 2 Titik Hilang",                    false, 40.0f,  0.0f),
        PERSPECTIVE_3POINT("Perspektif > 3 Titik Hilang",                    false, 40.0f, 32.0f);

        private final String label;
        private final boolean parallel;
        private final float presetAzimuth;
        private final float presetElevation;

        ProjectionType(String label, boolean parallel, float presetAzimuth, float presetElevation) {
            this.label = label;
            this.parallel = parallel;
            this.presetAzimuth = presetAzimuth;
            this.presetElevation = presetElevation;
        }

        public String getLabel()          { return label; }
        public boolean isParallel()       { return parallel; }
        public float getPresetAzimuth()   { return presetAzimuth; }
        public float getPresetElevation() { return presetElevation; }
        /** Tombol keyboard 1..7 */
        public int getKeyNumber()         { return ordinal() + 1; }

        /** Mengembalikan jenis proyeksi untuk tombol angka 1..7, atau null. */
        public static ProjectionType fromKeyNumber(int number) {
            ProjectionType[] all = values();
            return (number >= 1 && number <= all.length) ? all[number - 1] : null;
        }
    }

    /** Sudut garis surut (receding axis) pada proyeksi oblique. */
    public static final float OBLIQUE_ANGLE_DEG = 45.0f;

    private ProjectionType current = ProjectionType.PERSPECTIVE_3POINT;

    public ProjectionType getCurrent() { return current; }
    public void setCurrent(ProjectionType type) { if (type != null) this.current = type; }

    /**
     * Hitung matriks proyeksi untuk jenis yang aktif.
     *
     * @param persp        parameter frustum (FOV, aspect, near, far)
     * @param viewDistance jarak kamera ke target; dipakai agar ukuran volume ortografik
     *                     sama dengan ukuran objek pada perspektif (zoom tetap berfungsi)
     */
    public Matrix4f computeProjectionMatrix(PerspectiveProjection persp, float viewDistance) {
        return computeProjectionMatrix(current, persp, viewDistance);
    }

    public static Matrix4f computeProjectionMatrix(ProjectionType type, PerspectiveProjection persp, float viewDistance) {
        if (!type.isParallel()) {
            return persp.calculateProjectionMatrix();
        }
        float halfH = persp.getHalfHeightAt(viewDistance);
        float halfW = halfH * persp.getAspectRatio();
        Matrix4f ortho = calculateOrthographicMatrix(-halfW, halfW, -halfH, halfH, persp.getZNear(), persp.getZFar());

        switch (type) {
            case OBLIQUE_CAVALIER:
                return ortho.mul(calculateObliqueShearMatrix(1.0f, OBLIQUE_ANGLE_DEG, viewDistance));
            case OBLIQUE_CABINET:
                return ortho.mul(calculateObliqueShearMatrix(0.5f, OBLIQUE_ANGLE_DEG, viewDistance));
            case ORTHOGRAPHIC:
            case ISOMETRIC:   // isometrik = ortografik + kamera diputar 45° (Y) dan 35.264° (X)
            default:
                return ortho;
        }
    }

    /**
     * Matriks ortografik (setara glOrtho), dihitung manual:
     * <pre>
     *  | 2/(r-l)  0        0         -(r+l)/(r-l) |
     *  | 0        2/(t-b)  0         -(t+b)/(t-b) |
     *  | 0        0       -2/(f-n)   -(f+n)/(f-n) |
     *  | 0        0        0          1           |
     * </pre>
     */
    public static Matrix4f calculateOrthographicMatrix(float l, float r, float b, float t, float n, float f) {
        Matrix4f m = new Matrix4f(); // identitas
        m.m00(2.0f / (r - l));
        m.m11(2.0f / (t - b));
        m.m22(-2.0f / (f - n));
        m.m30(-(r + l) / (r - l));
        m.m31(-(t + b) / (t - b));
        m.m32(-(f + n) / (f - n));
        m.m33(1.0f);
        return m;
    }

    /**
     * Matriks shearing untuk proyeksi oblique (di view space).
     * Titik yang semakin jauh (z makin negatif) digeser sepanjang arah sudut phi:
     * <pre>
     *   x' = x - k cos(phi) (z + d)
     *   y' = y - k sin(phi) (z + d)
     * </pre>
     * k = 1 → Cavalier (panjang kedalaman sebenarnya), k = 1/2 → Cabinet (kedalaman setengah).
     * d = jarak pivot (target) agar objek tetap di tengah layar.
     */
    public static Matrix4f calculateObliqueShearMatrix(float k, float phiDeg, float pivotDistance) {
        float phi = (float) Math.toRadians(phiDeg);
        float a = -k * (float) Math.cos(phi);
        float b = -k * (float) Math.sin(phi);
        Matrix4f m = new Matrix4f(); // identitas
        m.m20(a).m30(a * pivotDistance);
        m.m21(b).m31(b * pivotDistance);
        return m;
    }

    /** Terapkan langsung ke pipeline OpenGL (GL_PROJECTION). */
    public void applyProjection(PerspectiveProjection persp, float viewDistance) {
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(computeProjectionMatrix(persp, viewDistance).get(new float[16]));
    }
}
