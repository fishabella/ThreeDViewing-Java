package com.grafkom.model;

/**
 * Pabrik model 3D bermateri (normal vertex + UV). Semua model diletakkan di atas bidang lantai
 * y = {@link #GROUND_Y} dan menghadap ke arah +Z.
 *
 * Konvensi UV: u ke kanan, v ke ATAS (v = 1 di bagian atas gambar) - cocok dengan Texture2D yang
 * memuat gambar dengan stbi_set_flip_vertically_on_load(true), sehingga tekstur tidak terbalik.
 */
public final class MeshFactory {

    public static final float GROUND_Y = -1.0f;

    private MeshFactory() { }

    // =====================================================================
    //  SPHERE (UV sphere)
    // =====================================================================

    /** Bola jari-jari r berpusat di (0, GROUND_Y + r, 0). Vertex seam diduplikasi untuk UV. */
    public static TexturedMesh createSphere(float r, int stacks, int slices) {
        MeshBuilder b = new MeshBuilder("Sphere").material(0.85f, 0.32f, 0.28f, 1f);
        float cy = GROUND_Y + r;
        int[][] grid = new int[stacks + 1][slices + 1];
        for (int i = 0; i <= stacks; i++) {
            double theta = Math.PI * i / stacks;          // 0 (kutub atas) .. PI (kutub bawah)
            for (int j = 0; j <= slices; j++) {
                double phi = 2.0 * Math.PI * j / slices;
                float x = (float) (r * Math.sin(theta) * Math.cos(phi));
                float y = (float) (r * Math.cos(theta));
                float z = (float) (r * Math.sin(theta) * Math.sin(phi));
                // u berkurang searah phi: dilihat dari luar, phi bertambah ke KIRI, sehingga tekstur tidak mirror
                grid[i][j] = b.addVertex(x, cy + y, z, 2f * (slices - j) / slices, 1f - (float) i / stacks);
            }
        }
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = grid[i][j], bb = grid[i + 1][j], c = grid[i + 1][j + 1], d = grid[i][j + 1];
                b.addTriangle(a, d, c);   // segitiga degenerate di kutub otomatis dibuang oleh build()
                b.addTriangle(a, c, bb);
            }
        }
        return b.build();
    }

    // =====================================================================
    //  TORUS
    // =====================================================================

    /** Torus rebah (sumbu = Y), jari-jari utama R dan jari-jari tabung r; menyentuh lantai. */
    public static TexturedMesh createTorus(float R, float r, int rings, int sides) {
        MeshBuilder b = new MeshBuilder("Torus").material(0.28f, 0.58f, 0.90f, 1f);
        float cy = GROUND_Y + r;
        int[][] grid = new int[rings + 1][sides + 1];
        for (int i = 0; i <= rings; i++) {
            double u = 2.0 * Math.PI * i / rings;
            for (int j = 0; j <= sides; j++) {
                double v = 2.0 * Math.PI * j / sides;
                float x = (float) ((R + r * Math.cos(v)) * Math.cos(u));
                float y = (float) (r * Math.sin(v));
                float z = (float) ((R + r * Math.cos(v)) * Math.sin(u));
                grid[i][j] = b.addVertex(x, cy + y, z, 4f * (rings - i) / rings, 2f * j / sides);   // (rings - i): anti-mirror
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sides; j++) {
                int a = grid[i][j], bb = grid[i + 1][j], c = grid[i + 1][j + 1], d = grid[i][j + 1];
                b.addTriangle(a, d, c);
                b.addTriangle(a, c, bb);
            }
        }
        return b.build();
    }

    // =====================================================================
    //  KUBUS (hard edge - paling jelas untuk membandingkan Flat vs Smooth)
    // =====================================================================

    public static TexturedMesh createCube(float s) {
        MeshBuilder b = new MeshBuilder("Cube").material(0.92f, 0.76f, 0.30f, 1f);
        float y0 = GROUND_Y, y1 = GROUND_Y + 2 * s;
        float[] lbf = {-s, y0, s}, rbf = {s, y0, s}, rtf = {s, y1, s}, ltf = {-s, y1, s};     // z = +s
        float[] lbb = {-s, y0, -s}, rbb = {s, y0, -s}, rtb = {s, y1, -s}, ltb = {-s, y1, -s}; // z = -s
        b.addQuad(lbf, rbf, rtf, ltf, 1, 1); // depan  (+z)
        b.addQuad(rbb, lbb, ltb, rtb, 1, 1); // belakang (-z)
        b.addQuad(rbf, rbb, rtb, rtf, 1, 1); // kanan  (+x)
        b.addQuad(lbb, lbf, ltf, ltb, 1, 1); // kiri   (-x)
        b.addQuad(ltf, rtf, rtb, ltb, 1, 1); // atas   (+y)
        b.addQuad(lbb, rbb, rbf, lbf, 1, 1); // bawah  (-y)
        return b.build();
    }

    // =====================================================================
    //  RUMAH 3D
    // =====================================================================

    /** Rumah bertekstur bata: dinding, atap pelana, pintu dan jendela (polos, tanpa tekstur). */
    public static TexturedMesh createHouse() {
        MeshBuilder b = new MeshBuilder("Rumah 3D");
        final float w = 1.0f, d = 1.2f, yb = GROUND_Y, yt = 0.35f, yr = 1.35f;
        float[] lbf = {-w, yb, d}, rbf = {w, yb, d}, rtf = {w, yt, d}, ltf = {-w, yt, d};
        float[] lbb = {-w, yb, -d}, rbb = {w, yb, -d}, rtb = {w, yt, -d}, ltb = {-w, yt, -d};
        float[] ridgeF = {0, yr, d}, ridgeB = {0, yr, -d};

        // Dinding (bata: UV diskalakan agar ukuran bata konsisten di tiap sisi)
        b.material(0.95f, 0.88f, 0.78f, 1f);
        b.addQuad(lbf, rbf, rtf, ltf, 2.0f, 1.35f);          // depan  (+z)
        b.addQuad(rbb, lbb, ltb, rtb, 2.0f, 1.35f);          // belakang (-z)
        b.addQuad(rbf, rbb, rtb, rtf, 2.4f, 1.35f);          // kanan  (+x)
        b.addQuad(lbb, lbf, ltf, ltb, 2.4f, 1.35f);          // kiri   (-x)
        // Gable (segitiga)
        int g0 = b.addVertex(-w, yt, d, 0f, 0f), g1 = b.addVertex(w, yt, d, 2f, 0f), g2 = b.addVertex(0, yr, d, 1f, 1f);
        b.addTriangle(g0, g1, g2);
        int h0 = b.addVertex(w, yt, -d, 0f, 0f), h1 = b.addVertex(-w, yt, -d, 2f, 0f), h2 = b.addVertex(0, yr, -d, 1f, 1f);
        b.addTriangle(h0, h1, h2);

        // Atap (tinted merah bata + tekstur)
        b.material(0.95f, 0.45f, 0.38f, 1f);
        b.addQuad(rtf, rtb, ridgeB, ridgeF, 2.4f, 1.2f);     // atap kanan (+x)
        b.addQuad(ltb, ltf, ridgeF, ridgeB, 2.4f, 1.2f);     // atap kiri  (-x)

        // Pintu & jendela: warna polos (bobot tekstur 0), digeser sedikit keluar agar tidak z-fighting
        float e = 0.012f;
        b.material(0.42f, 0.25f, 0.12f, 0f);
        b.addQuad(new float[]{-0.25f, yb, d + e}, new float[]{0.25f, yb, d + e},
                  new float[]{0.25f, -0.15f, d + e}, new float[]{-0.25f, -0.15f, d + e}, 1, 1);
        b.material(0.45f, 0.70f, 0.95f, 0f);
        b.addQuad(new float[]{-0.85f, -0.55f, d + e}, new float[]{-0.45f, -0.55f, d + e},
                  new float[]{-0.45f, -0.10f, d + e}, new float[]{-0.85f, -0.10f, d + e}, 1, 1);
        b.addQuad(new float[]{0.45f, -0.55f, d + e}, new float[]{0.85f, -0.55f, d + e},
                  new float[]{0.85f, -0.10f, d + e}, new float[]{0.45f, -0.10f, d + e}, 1, 1);
        // Jendela samping (+x menghadap kanan, -x menghadap kiri)
        b.addQuad(new float[]{w + e, -0.55f, 0.85f}, new float[]{w + e, -0.55f, 0.25f},
                  new float[]{w + e, -0.10f, 0.25f}, new float[]{w + e, -0.10f, 0.85f}, 1, 1);
        b.addQuad(new float[]{-w - e, -0.55f, -0.85f}, new float[]{-w - e, -0.55f, -0.25f},
                  new float[]{-w - e, -0.10f, -0.25f}, new float[]{-w - e, -0.10f, -0.85f}, 1, 1);
        return b.build();
    }

    // =====================================================================
    //  BIDANG LANTAI (ground plane) - penerima bayangan
    // =====================================================================

    /** Lantai persegi [-half, half]^2 pada y = GROUND_Y, UV berulang tiap tileSize satuan dunia. */
    public static TexturedMesh createGround(float half, float tileSize) {
        MeshBuilder b = new MeshBuilder("Ground").material(0.78f, 0.82f, 0.88f, 1f);
        float n = 2f * half / tileSize;
        float y = GROUND_Y;
        // CCW dilihat dari atas (+y): normal = +y
        b.addQuad(new float[]{-half, y, half}, new float[]{half, y, half},
                  new float[]{half, y, -half}, new float[]{-half, y, -half}, n, n);
        return b.build();
    }
}
