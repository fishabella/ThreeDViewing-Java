package com.grafkom.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pembangun mesh di sisi CPU. Menerima "corner vertex" (posisi + UV + tint) dan segitiga
 * (urutan CCW dilihat dari luar), lalu menghitung:
 *
 * <h3>Normal FACE (Flat Shading)</h3>
 * <pre>  N_face = normalize( (B - A) x (C - A) )</pre>
 *
 * <h3>Normal VERTEX (Smooth Shading) - Vertex Normal Averaging</h3>
 * <pre>  N_vertex = normalize( sum_i  w_i * N_face_i )</pre>
 * dijumlahkan atas SEMUA face yang berbagi posisi yang sama. Vertex dengan posisi identik
 * tetapi UV berbeda (mis. seam tekstur pada bola) di-"weld" lewat kunci posisi, sehingga
 * normalnya sama dan tidak muncul garis jahitan pada shading.
 * Bobot w_i = sudut interior segitiga pada vertex tersebut (angle-weighted). Dibanding jumlah
 * polos (w = 1), pembobotan sudut membuat normal tidak bergantung pada cara permukaan
 * di-tessellasi → hasil lebih akurat di dekat kutub bola/seam, tanpa pita Mach yang tidak merata.
 */
public class MeshBuilder {

    private record Corner(float x, float y, float z, float u, float v, float r, float g, float b, float tex) { }

    private final String name;
    private final List<Corner> corners = new ArrayList<>();
    private final List<int[]> triangles = new ArrayList<>();

    private float tintR = 1, tintG = 1, tintB = 1, texWeight = 1;

    public MeshBuilder(String name) {
        this.name = name;
    }

    /** Set warna dasar (tint) dan bobot tekstur untuk vertex yang ditambahkan berikutnya. */
    public MeshBuilder material(float r, float g, float b, float textureWeight) {
        tintR = r; tintG = g; tintB = b; texWeight = textureWeight;
        return this;
    }

    public int addVertex(float x, float y, float z, float u, float v) {
        corners.add(new Corner(x, y, z, u, v, tintR, tintG, tintB, texWeight));
        return corners.size() - 1;
    }

    /** Segitiga CCW (dilihat dari luar). */
    public void addTriangle(int a, int b, int c) {
        triangles.add(new int[]{a, b, c});
    }

    /** Quad a-b-c-d (CCW) dipecah menjadi (a,b,c) dan (a,c,d). */
    public void addQuad(int a, int b, int c, int d) {
        addTriangle(a, b, c);
        addTriangle(a, c, d);
    }

    /** Quad 4 titik dengan UV standar (0,0) (1,0) (1,1) (0,1) yang dikalikan uScale/vScale. */
    public void addQuad(float[] p0, float[] p1, float[] p2, float[] p3, float uScale, float vScale) {
        int a = addVertex(p0[0], p0[1], p0[2], 0, 0);
        int b = addVertex(p1[0], p1[1], p1[2], uScale, 0);
        int c = addVertex(p2[0], p2[1], p2[2], uScale, vScale);
        int d = addVertex(p3[0], p3[1], p3[2], 0, vScale);
        addQuad(a, b, c, d);
    }

    // =====================================================================
    //  BUILD
    // =====================================================================

    public TexturedMesh build() {
        final int F = TexturedMesh.FLOATS_PER_VERTEX;

        // ---- Normal face per segitiga (segitiga degenerate dibuang) ----
        List<int[]> tris = new ArrayList<>();
        List<float[]> faceNormals = new ArrayList<>();
        for (int[] t : triangles) {
            float[] n = faceNormal(t);
            if (n != null) { tris.add(t); faceNormals.add(n); }
        }

        // ---- FLAT: 3 vertex per segitiga dengan normal face ----
        float[] flat = new float[tris.size() * 3 * F];
        int o = 0;
        for (int i = 0; i < tris.size(); i++) {
            for (int k = 0; k < 3; k++) {
                o = put(flat, o, corners.get(tris.get(i)[k]), faceNormals.get(i));
            }
        }

        // ---- SMOOTH: akumulasi normal ke kunci posisi (weld) ----
        Map<PosKey, float[]> accum = new HashMap<>();
        for (int i = 0; i < tris.size(); i++) {
            int[] t = tris.get(i);
            float[] fn = faceNormals.get(i);
            for (int k = 0; k < 3; k++) {
                Corner c = corners.get(t[k]);
                Corner p = corners.get(t[(k + 1) % 3]);
                Corner q = corners.get(t[(k + 2) % 3]);
                float w = cornerAngle(c, p, q);
                float[] acc = accum.computeIfAbsent(positionKey(c), key -> new float[3]);
                acc[0] += fn[0] * w; acc[1] += fn[1] * w; acc[2] += fn[2] * w;
            }
        }
        for (float[] a : accum.values()) normalize(a);

        // Hanya corner yang dipakai segitiga valid yang masuk vertex buffer (remap index)
        Map<Integer, Integer> remap = new HashMap<>();
        List<float[]> smoothList = new ArrayList<>();
        int[] idx = new int[tris.size() * 3];
        int ii = 0;
        for (int[] t : tris) {
            for (int k = 0; k < 3; k++) {
                int ci = t[k];
                Integer mapped = remap.get(ci);
                if (mapped == null) {
                    Corner c = corners.get(ci);
                    float[] row = new float[F];
                    put(row, 0, c, accum.get(positionKey(c)));
                    mapped = smoothList.size();
                    smoothList.add(row);
                    remap.put(ci, mapped);
                }
                idx[ii++] = mapped;
            }
        }
        float[] smooth = new float[smoothList.size() * F];
        for (int i = 0; i < smoothList.size(); i++) System.arraycopy(smoothList.get(i), 0, smooth, i * F, F);

        return new TexturedMesh(name, flat, smooth, idx);
    }

    private static int put(float[] dst, int o, Corner c, float[] n) {
        dst[o++] = c.x; dst[o++] = c.y; dst[o++] = c.z;
        dst[o++] = n[0]; dst[o++] = n[1]; dst[o++] = n[2];
        dst[o++] = c.u; dst[o++] = c.v;
        dst[o++] = c.r; dst[o++] = c.g; dst[o++] = c.b; dst[o++] = c.tex;
        return o;
    }

    /** N = normalize((B-A) x (C-A)); null bila segitiga degenerate (luas ~ 0). */
    private float[] faceNormal(int[] t) {
        Corner a = corners.get(t[0]), b = corners.get(t[1]), c = corners.get(t[2]);
        float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        float vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        float[] n = {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        double len = Math.sqrt((double) n[0] * n[0] + (double) n[1] * n[1] + (double) n[2] * n[2]);
        if (len < 1e-9) return null;
        n[0] /= len; n[1] /= len; n[2] /= len;
        return n;
    }

    /** Sudut interior segitiga di titik c (radian). */
    private static float cornerAngle(Corner c, Corner p, Corner q) {
        double ax = p.x - c.x, ay = p.y - c.y, az = p.z - c.z;
        double bx = q.x - c.x, by = q.y - c.y, bz = q.z - c.z;
        double la = Math.sqrt(ax * ax + ay * ay + az * az), lb = Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 1e-12 || lb < 1e-12) return 0f;
        double cos = (ax * bx + ay * by + az * bz) / (la * lb);
        return (float) Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
    }

    private static void normalize(float[] n) {
        double len = Math.sqrt((double) n[0] * n[0] + (double) n[1] * n[1] + (double) n[2] * n[2]);
        if (len < 1e-12) { n[0] = 0; n[1] = 1; n[2] = 0; return; }
        n[0] /= len; n[1] /= len; n[2] /= len;
    }

    /** Kunci weld: posisi dikuantisasi 1e-4 (toleransi float). */
    private static PosKey positionKey(Corner c) {
        return new PosKey(Math.round(c.x * 10000.0), Math.round(c.y * 10000.0), Math.round(c.z * 10000.0));
    }

    private record PosKey(long x, long y, long z) { }
}
