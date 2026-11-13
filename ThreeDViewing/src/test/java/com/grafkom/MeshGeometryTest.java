package com.grafkom;

import com.grafkom.model.MeshFactory;
import com.grafkom.model.TexturedMesh;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifikasi geometri mesh Minggu 11 tanpa konteks OpenGL:
 *  - urutan CCW (winding) benar → Back-Face Culling tidak memotong sisi depan,
 *  - normal face (Flat) dan normal vertex averaging (Smooth) akurat,
 *  - koordinat UV valid dan tidak terbalik arah V.
 */
class MeshGeometryTest {

    private static final int F = TexturedMesh.FLOATS_PER_VERTEX;

    // ---------- util ----------
    private static float[] pos(float[] v, int i) { return new float[]{v[i * F], v[i * F + 1], v[i * F + 2]}; }
    private static float[] nrm(float[] v, int i) { return new float[]{v[i * F + 3], v[i * F + 4], v[i * F + 5]}; }
    private static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static float len(float[] a) { return (float) Math.sqrt(dot(a, a)); }
    private static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Normal geometris dari urutan vertex (CCW = menghadap pengamat). */
    private static float[] windingNormal(float[] v, int a, int b, int c) {
        float[] n = cross(sub(pos(v, b), pos(v, a)), sub(pos(v, c), pos(v, a)));
        float l = len(n);
        return new float[]{n[0] / l, n[1] / l, n[2] / l};
    }

    private static float[] centroid(float[] v, int a, int b, int c) {
        float[] pa = pos(v, a), pb = pos(v, b), pc = pos(v, c);
        return new float[]{(pa[0] + pb[0] + pc[0]) / 3, (pa[1] + pb[1] + pc[1]) / 3, (pa[2] + pb[2] + pc[2]) / 3};
    }

    /** Semua segitiga harus CCW dilihat dari luar: normal winding searah (centroid - titikDalam). */
    private static void assertOutwardWinding(TexturedMesh m, java.util.function.Function<float[], float[]> insidePointFor) {
        float[] v = m.getFlatVertices();
        int tris = m.getFlatVertexCount() / 3;
        for (int t = 0; t < tris; t++) {
            float[] n = windingNormal(v, 3 * t, 3 * t + 1, 3 * t + 2);
            float[] c = centroid(v, 3 * t, 3 * t + 1, 3 * t + 2);
            float[] out = sub(c, insidePointFor.apply(c));
            assertTrue(dot(n, out) > 0, m.getName() + ": segitiga #" + t + " berputar CW (akan di-cull dari luar)");
        }
    }

    // ---------- winding / Back-Face Culling ----------

    @Test
    void sphereWindingCcwDariLuar() {
        assertOutwardWinding(MeshFactory.createSphere(1f, 24, 48), c -> new float[]{0, 0, 0});
    }

    @Test
    void cubeWindingCcwDariLuar() {
        assertOutwardWinding(MeshFactory.createCube(1f), c -> new float[]{0, 0, 0});
    }

    @Test
    void torusWindingCcwDariLuar() {
        final float R = 0.95f;
        assertOutwardWinding(MeshFactory.createTorus(R, 0.42f, 48, 24), c -> {
            float l = (float) Math.hypot(c[0], c[2]);
            return new float[]{R * c[0] / l, c[1], R * c[2] / l};   // titik pada lingkaran pusat tabung
        });
    }

    @Test
    void rumahWindingCcwDariLuar() {
        assertOutwardWinding(MeshFactory.createHouse(), c -> new float[]{0, -0.3f, 0});
    }

    @Test
    void lantaiMenghadapKeAtas() {
        TexturedMesh g = MeshFactory.createGround(10f, 2f);
        float[] v = g.getFlatVertices();
        for (int t = 0; t < g.getFlatVertexCount() / 3; t++) {
            assertEquals(1f, windingNormal(v, 3 * t, 3 * t + 1, 3 * t + 2)[1], 1e-5f, "lantai harus menghadap +Y");
        }
    }

    // ---------- Flat shading: normal per-face ----------

    @Test
    void flatShadingNormalSamaDiSetiapFaceDanSatuan() {
        TexturedMesh m = MeshFactory.createSphere(1f, 16, 32);
        float[] v = m.getFlatVertices();
        for (int t = 0; t < m.getFlatVertexCount() / 3; t++) {
            float[] face = windingNormal(v, 3 * t, 3 * t + 1, 3 * t + 2);
            for (int k = 0; k < 3; k++) {
                float[] n = nrm(v, 3 * t + k);
                assertEquals(1f, len(n), 1e-4f);
                assertEquals(1f, dot(n, face), 1e-4f, "normal flat harus = normal face");
            }
        }
    }

    // ---------- Smooth shading: vertex normal averaging ----------

    @Test
    void smoothNormalBolaSamaDenganNormalAnalitik() {
        TexturedMesh m = MeshFactory.createSphere(1f, 48, 96);
        float[] v = m.getSmoothVertices();
        float[] center = {0, 0, 0};
        float worst = 1f;
        for (int i = 0; i < m.getSmoothVertexCount(); i++) {
            float[] p = pos(v, i);
            float[] ideal = sub(p, center);
            float l = len(ideal);
            ideal = new float[]{ideal[0] / l, ideal[1] / l, ideal[2] / l};
            float[] n = nrm(v, i);
            assertEquals(1f, len(n), 1e-4f, "normal vertex harus satuan");
            worst = Math.min(worst, dot(n, ideal));
        }
        assertTrue(worst > 0.9995f, "normal smooth menyimpang dari normal analitik bola, cos min = " + worst);
    }

    @Test
    void smoothNormalTorusSamaDenganNormalAnalitik() {
        final float R = 0.95f;
        TexturedMesh m = MeshFactory.createTorus(R, 0.42f, 72, 36);
        float[] v = m.getSmoothVertices();
        float worst = 1f;
        for (int i = 0; i < m.getSmoothVertexCount(); i++) {
            float[] p = pos(v, i);
            float l = (float) Math.hypot(p[0], p[2]);
            float[] tubeCenter = {R * p[0] / l, p[1], R * p[2] / l};   // y tabung dihitung dari p sendiri
            tubeCenter[1] = -1.0f + 0.42f;                              // pusat tabung berada di y = GROUND_Y + r
            float[] ideal = sub(p, tubeCenter);
            float il = len(ideal);
            ideal = new float[]{ideal[0] / il, ideal[1] / il, ideal[2] / il};
            worst = Math.min(worst, dot(nrm(v, i), ideal));
        }
        assertTrue(worst > 0.998f, "cos min = " + worst);
    }

    @Test
    void seamUvTidakMenimbulkanJahitanNormal() {
        // Posisi sama tetapi UV berbeda (seam u=0 dan u=2) harus bernormal identik (weld by position)
        TexturedMesh m = MeshFactory.createSphere(1f, 16, 32);
        float[] v = m.getSmoothVertices();
        int n = m.getSmoothVertexCount();
        int pairs = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float[] a = pos(v, i), b = pos(v, j);
                if (len(sub(a, b)) < 1e-5f) {
                    pairs++;
                    assertEquals(1f, dot(nrm(v, i), nrm(v, j)), 1e-5f);
                }
            }
        }
        assertTrue(pairs > 0, "seharusnya ada vertex seam kembar");
    }

    @Test
    void smoothBerbagiVertexFlatTidak() {
        TexturedMesh m = MeshFactory.createSphere(1f, 16, 32);
        assertEquals(m.getTriangleCount() * 3, m.getFlatVertexCount());
        assertTrue(m.getSmoothVertexCount() < m.getFlatVertexCount() / 2, "smooth harus memakai index buffer (vertex dibagi)");
    }

    @Test
    void kubusSmoothMerataanNormalDiSudut() {
        // Pada kubus, rata-rata 3 face di sudut = diagonal (±1,±1,±1)/sqrt(3)
        TexturedMesh m = MeshFactory.createCube(1f);
        float[] v = m.getSmoothVertices();
        float s3 = (float) (1.0 / Math.sqrt(3.0));
        for (int i = 0; i < m.getSmoothVertexCount(); i++) {
            float[] n = nrm(v, i);
            assertEquals(s3, Math.abs(n[0]), 1e-4f);
            assertEquals(s3, Math.abs(n[1]), 1e-4f);
            assertEquals(s3, Math.abs(n[2]), 1e-4f);
        }
    }

    // ---------- UV ----------

    @Test
    void uvBolaVBenarDanTidakTerbalik() {
        TexturedMesh m = MeshFactory.createSphere(1f, 16, 32);
        float[] v = m.getSmoothVertices();
        float yTop = -9, vAtTop = -1, yBottom = 9, vAtBottom = -1;
        for (int i = 0; i < m.getSmoothVertexCount(); i++) {
            float y = v[i * F + 1], tv = v[i * F + 7];
            assertTrue(tv >= -1e-5f && tv <= 1f + 1e-5f, "v di luar [0,1]");
            if (y > yTop) { yTop = y; vAtTop = tv; }
            if (y < yBottom) { yBottom = y; vAtBottom = tv; }
        }
        assertEquals(1f, vAtTop, 1e-5f, "bagian atas harus v = 1 (gambar dimuat dengan flip vertikal)");
        assertEquals(0f, vAtBottom, 1e-5f, "bagian bawah harus v = 0");
    }

    /**
     * Pemetaan UV tidak boleh mirror: segitiga yang CCW di ruang 3D (dilihat dari luar) harus
     * juga CCW di ruang UV (luas bertanda positif). UV mirror membuat teks pada tekstur terbalik kiri-kanan.
     */
    @Test
    void uvTidakMirrorUntukSemuaMesh() {
        for (TexturedMesh m : new TexturedMesh[]{MeshFactory.createSphere(1f, 24, 48),
                MeshFactory.createTorus(0.95f, 0.42f, 48, 24), MeshFactory.createHouse(),
                MeshFactory.createCube(1f), MeshFactory.createGround(10f, 2f)}) {
            float[] v = m.getFlatVertices();
            for (int t = 0; t < m.getFlatVertexCount() / 3; t++) {
                int a = 3 * t, b = a + 1, c = a + 2;
                float ux = v[b * F + 6] - v[a * F + 6], uy = v[b * F + 7] - v[a * F + 7];
                float vx = v[c * F + 6] - v[a * F + 6], vy = v[c * F + 7] - v[a * F + 7];
                float uvArea = ux * vy - uy * vx;
                assertTrue(uvArea > 0, m.getName() + ": segitiga #" + t + " UV mirror (luas UV = " + uvArea + ")");
            }
        }
    }

    @Test
    void uvKubusDalamRentang() {
        TexturedMesh m = MeshFactory.createCube(1f);
        float[] v = m.getFlatVertices();
        for (int i = 0; i < m.getFlatVertexCount(); i++) {
            float u = v[i * F + 6], tv = v[i * F + 7];
            assertTrue(u >= 0 && u <= 1 && tv >= 0 && tv <= 1);
        }
    }

    @Test
    void modelBertengerDiAtasLantai() {
        for (TexturedMesh m : new TexturedMesh[]{MeshFactory.createSphere(1f, 16, 32),
                MeshFactory.createTorus(0.95f, 0.42f, 24, 12), MeshFactory.createHouse(), MeshFactory.createCube(1f)}) {
            float minY = Float.MAX_VALUE;
            float[] v = m.getFlatVertices();
            for (int i = 0; i < m.getFlatVertexCount(); i++) minY = Math.min(minY, v[i * F + 1]);
            assertEquals(MeshFactory.GROUND_Y, minY, 1e-3f, m.getName() + " harus menyentuh lantai");
        }
    }
}
