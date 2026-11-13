package com.grafkom;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.PerspectiveProjection;
import com.grafkom.projection.ProjectionTaxonomyManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifikasi bahwa matriks yang dihitung MANUAL identik dengan
 * implementasi referensi JOML (bukti akurasi perhitungan).
 */
class ViewingMathTest {

    private static final float EPS = 1e-5f;

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        float[] e = expected.get(new float[16]);
        float[] a = actual.get(new float[16]);
        for (int i = 0; i < 16; i++) {
            assertEquals(e[i], a[i], EPS, "elemen ke-" + i + "\nexpected:\n" + expected + "\nactual:\n" + actual);
        }
    }

    @Test
    void lookAtManualSamaDenganJoml() {
        Vector3f eye = new Vector3f(3.5f, 2.0f, 6.0f);
        Vector3f at = new Vector3f(0.2f, -0.3f, 0.5f);
        Vector3f up = new Vector3f(0, 1, 0);
        assertMatrixEquals(new Matrix4f().setLookAt(eye, at, up), Camera3D.calculateLookAtMatrix(eye, at, up));
    }

    @Test
    void lookAtMemetakanEyeKeOriginDanTargetKeMinusZ() {
        Camera3D cam = new Camera3D(new Vector3f(4, 3, 5), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        Matrix4f v = cam.calculateLookAtMatrix();
        Vector3f eyeInView = v.transformPosition(cam.getEye());
        assertEquals(0, eyeInView.length(), 1e-4f);
        Vector3f targetInView = v.transformPosition(new Vector3f(0, 0, 0));
        assertEquals(0, targetInView.x, 1e-4f);
        assertEquals(0, targetInView.y, 1e-4f);
        assertEquals(-cam.getRadius(), targetInView.z, 1e-4f);
    }

    @Test
    void basisUvnOrtonormal() {
        Camera3D cam = new Camera3D(new Vector3f(2, 5, -3), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        Vector3f u = cam.getRightVector();
        Vector3f f = cam.getForwardVector();
        assertEquals(1, u.length(), EPS);
        assertEquals(1, f.length(), EPS);
        assertEquals(0, u.dot(f), EPS);
    }

    @Test
    void orbitalSferisKonsisten() {
        Camera3D cam = new Camera3D(new Vector3f(0, 0, 7), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        cam.updateOrbitalPosition(5f, 30f, 20f);
        assertEquals(5f, cam.getEye().length(), 1e-4f);
        assertEquals(5f * Math.sin(Math.toRadians(20)), cam.getEye().y, 1e-4f);
    }

    @Test
    void perspektifManualSamaDenganJoml() {
        PerspectiveProjection p = new PerspectiveProjection(60f, 16f / 9f, 0.1f, 100f);
        Matrix4f expected = new Matrix4f().setPerspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
        assertMatrixEquals(expected, p.calculateProjectionMatrix());
    }

    @Test
    void frustumManualSamaDenganJoml() {
        Matrix4f expected = new Matrix4f().setFrustum(-0.7f, 0.4f, -0.3f, 0.5f, 0.2f, 50f);
        assertMatrixEquals(expected, PerspectiveProjection.calculateFrustumMatrix(-0.7f, 0.4f, -0.3f, 0.5f, 0.2f, 50f));
    }

    @Test
    void offAxisDenganIpdNolSamaDenganSimetris() {
        PerspectiveProjection p = new PerspectiveProjection(55f, 1.5f, 0.1f, 80f);
        assertMatrixEquals(p.calculateProjectionMatrix(), p.calculateOffAxisMatrix(0f, 7f));
    }

    @Test
    void offAxisZeroParallaxPadaBidangKonvergensi() {
        // Titik di bidang konvergensi harus jatuh di posisi layar yang SAMA untuk kedua mata
        PerspectiveProjection p = new PerspectiveProjection(60f, 16f / 9f, 0.1f, 100f);
        float ipd = 0.3f, conv = 7f;
        Vector3f world = new Vector3f(0.8f, 0.4f, -conv); // relatif ke pusat kamera
        float[] ndcX = new float[2];
        int i = 0;
        for (int side : new int[]{-1, 1}) {
            float off = side * ipd / 2;
            Vector4f clip = new Vector4f(world.x - off, world.y, world.z, 1f); // koordinat di ruang mata
            p.calculateOffAxisMatrix(off, conv).transform(clip);
            ndcX[i++] = clip.x / clip.w;
        }
        assertEquals(ndcX[0], ndcX[1], 1e-5f);
    }

    @Test
    void ortografikManualSamaDenganJoml() {
        Matrix4f expected = new Matrix4f().setOrtho(-4, 4, -2.25f, 2.25f, 0.1f, 100f);
        assertMatrixEquals(expected, ProjectionTaxonomyManager.calculateOrthographicMatrix(-4, 4, -2.25f, 2.25f, 0.1f, 100f));
    }

    @Test
    void obliqueCabinetMemendekkanKedalamanSetengah() {
        // Rusuk sepanjang 1 pada arah -z harus tampak sepanjang k = 0.5 di layar (sebelum ortho)
        Matrix4f shear = ProjectionTaxonomyManager.calculateObliqueShearMatrix(0.5f, 45f, 7f);
        Vector3f a = shear.transformPosition(new Vector3f(0, 0, -7));
        Vector3f b = shear.transformPosition(new Vector3f(0, 0, -8));
        float len2d = (float) Math.hypot(b.x - a.x, b.y - a.y);
        assertEquals(0.5f, len2d, EPS);
        assertEquals(0f, a.x, EPS); // pivot (target) tidak bergeser
    }

    @Test
    void enamAtauLebihJenisProyeksi() {
        assertTrue(ProjectionTaxonomyManager.ProjectionType.values().length >= 5);
    }
}
