package com.grafkom.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

/**
 * Model 3D sederhana berbasis daftar vertex + face (poligon).
 * Disediakan dua model bawaan: Color Cube dan Rumah 3D sederhana.
 *
 * Setiap face menyimpan indeks vertex (urutan CCW dilihat dari luar) dan
 * warna opsional. Bila warna face null, warna per-vertex dipakai (gradasi warna,
 * seperti pada RGB Color Cube).
 */
public class Mesh3D {

    private record Face(int[] indices, float[] color) { }

    private final String name;
    private final List<float[]> vertices = new ArrayList<>();
    private final List<float[]> vertexColors = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();
    private final List<int[]> edges = new ArrayList<>();

    public Mesh3D(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public int getVertexCount() { return vertices.size(); }
    public int getFaceCount() { return faces.size(); }

    /** Tambah vertex beserta warnanya, mengembalikan indeks vertex. */
    public int addVertex(float x, float y, float z, float r, float g, float b) {
        vertices.add(new float[]{x, y, z});
        vertexColors.add(new float[]{r, g, b});
        return vertices.size() - 1;
    }

    /** Tambah face dengan warna per-vertex. */
    public void addFace(int... indices) {
        addFace(null, indices);
    }

    /** Tambah face dengan warna seragam. */
    public void addFace(float[] color, int... indices) {
        faces.add(new Face(indices.clone(), color == null ? null : color.clone()));
    }

    /** Bangun daftar rusuk unik dari semua face (untuk outline). */
    private void buildEdges() {
        Set<Long> seen = new LinkedHashSet<>();
        edges.clear();
        for (Face f : faces) {
            for (int i = 0; i < f.indices.length; i++) {
                int a = f.indices[i], b = f.indices[(i + 1) % f.indices.length];
                long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                if (seen.add(key)) edges.add(new int[]{a, b});
            }
        }
    }

    /**
     * Gambar mesh dengan OpenGL immediate mode (memakai matriks MODELVIEW aktif).
     *
     * @param desaturation 0 = warna asli, 1 = abu-abu penuh (berguna untuk anaglyph
     *                     agar warna merah/cyan murni tidak "hilang" di salah satu mata)
     * @param drawEdges    gambar outline rusuk berwarna gelap
     */
    public void render(float desaturation, boolean drawEdges) {
        if (edges.isEmpty()) buildEdges();

        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f); // dorong face sedikit ke belakang agar garis outline tidak z-fighting
        for (Face f : faces) {
            glBegin(GL_POLYGON);
            for (int idx : f.indices) {
                float[] c = (f.color != null) ? f.color : vertexColors.get(idx);
                setColor(c, desaturation);
                float[] v = vertices.get(idx);
                glVertex3f(v[0], v[1], v[2]);
            }
            glEnd();
        }
        glDisable(GL_POLYGON_OFFSET_FILL);

        if (drawEdges) {
            glLineWidth(1.6f);
            glColor3f(0.08f, 0.08f, 0.1f);
            glBegin(GL_LINES);
            for (int[] e : edges) {
                float[] a = vertices.get(e[0]);
                float[] b = vertices.get(e[1]);
                glVertex3f(a[0], a[1], a[2]);
                glVertex3f(b[0], b[1], b[2]);
            }
            glEnd();
        }
    }

    private static void setColor(float[] c, float desaturation) {
        float lum = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
        float k = 1.0f - desaturation;
        glColor3f(lum + (c[0] - lum) * k, lum + (c[1] - lum) * k, lum + (c[2] - lum) * k);
    }

    // =====================================================================
    //  MODEL BAWAAN
    // =====================================================================

    /**
     * RGB Color Cube: kubus [-s, s]^3, warna vertex = posisi yang dinormalisasi ke [0,1]
     * (pojok (-,-,-) hitam, (+,+,+) putih, sumbu X=merah, Y=hijau, Z=biru).
     */
    public static Mesh3D createColorCube(float s) {
        Mesh3D m = new Mesh3D("Color Cube");
        // indeks i: bit0 = x, bit1 = y, bit2 = z
        for (int i = 0; i < 8; i++) {
            int bx = i & 1, by = (i >> 1) & 1, bz = (i >> 2) & 1;
            m.addVertex(bx == 1 ? s : -s, by == 1 ? s : -s, bz == 1 ? s : -s, bx, by, bz);
        }
        m.addFace(4, 5, 7, 6); // depan  (+z)
        m.addFace(1, 0, 2, 3); // belakang (-z)
        m.addFace(5, 1, 3, 7); // kanan  (+x)
        m.addFace(0, 4, 6, 2); // kiri   (-x)
        m.addFace(6, 7, 3, 2); // atas   (+y)
        m.addFace(0, 1, 5, 4); // bawah  (-y)
        return m;
    }

    /** Rumah 3D sederhana: badan balok, atap pelana (prisma), pintu dan jendela. */
    public static Mesh3D createHouse() {
        Mesh3D m = new Mesh3D("Rumah 3D");
        final float w = 1.0f, d = 1.2f, yb = -1.0f, yt = 0.35f, yr = 1.35f;
        final float[] wall = {0.93f, 0.86f, 0.70f};
        final float[] wallSide = {0.85f, 0.76f, 0.58f};
        final float[] roof = {0.72f, 0.20f, 0.16f};
        final float[] roof2 = {0.60f, 0.15f, 0.12f};
        final float[] floor = {0.45f, 0.45f, 0.45f};
        final float[] door = {0.45f, 0.27f, 0.12f};
        final float[] glass = {0.45f, 0.70f, 0.95f};

        int[] v = new int[8];
        for (int i = 0; i < 8; i++) {
            int bx = i & 1, by = (i >> 1) & 1, bz = (i >> 2) & 1;
            v[i] = m.addVertex(bx == 1 ? w : -w, by == 1 ? yt : yb, bz == 1 ? d : -d, 1, 1, 1);
        }
        int r0 = m.addVertex(0, yr, -d, 1, 1, 1); // bubungan belakang
        int r1 = m.addVertex(0, yr, d, 1, 1, 1);  // bubungan depan

        m.addFace(wall, v[4], v[5], v[7], v[6]);      // dinding depan
        m.addFace(wall, v[1], v[0], v[2], v[3]);      // dinding belakang
        m.addFace(wallSide, v[5], v[1], v[3], v[7]);  // dinding kanan
        m.addFace(wallSide, v[0], v[4], v[6], v[2]);  // dinding kiri
        m.addFace(floor, v[0], v[1], v[5], v[4]);     // lantai
        m.addFace(wall, v[6], v[7], r1);              // gable depan
        m.addFace(wall, v[3], v[2], r0);              // gable belakang
        m.addFace(roof, v[7], v[3], r0, r1);          // atap kanan
        m.addFace(roof2, v[2], v[6], r1, r0);         // atap kiri

        float e = 0.006f; // sedikit di depan dinding agar tidak z-fighting
        // Pintu (depan)
        m.addFace(door, quad(m, -0.25f, yb, d + e, 0.25f, -0.15f, d + e));
        // Jendela depan
        m.addFace(glass, quad(m, -0.85f, -0.55f, d + e, -0.45f, -0.10f, d + e));
        m.addFace(glass, quad(m, 0.45f, -0.55f, d + e, 0.85f, -0.10f, d + e));
        // Jendela samping kanan (+x) & kiri (-x)
        m.addFace(glass, sideQuad(m, w + e, -0.55f, -0.10f, 0.25f, 0.85f, true));
        m.addFace(glass, sideQuad(m, -w - e, -0.55f, -0.10f, -0.85f, -0.25f, false));
        return m;
    }

    /** Quad sejajar bidang XY menghadap +z. */
    private static int[] quad(Mesh3D m, float x0, float y0, float z, float x1, float y1, float z1) {
        return new int[]{
                m.addVertex(x0, y0, z, 1, 1, 1), m.addVertex(x1, y0, z, 1, 1, 1),
                m.addVertex(x1, y1, z1, 1, 1, 1), m.addVertex(x0, y1, z1, 1, 1, 1)};
    }

    /** Quad sejajar bidang YZ; facingPlusX menentukan urutan CCW. */
    private static int[] sideQuad(Mesh3D m, float x, float y0, float y1, float z0, float z1, boolean facingPlusX) {
        int a = m.addVertex(x, y0, facingPlusX ? z1 : z0, 1, 1, 1);
        int b = m.addVertex(x, y0, facingPlusX ? z0 : z1, 1, 1, 1);
        int c = m.addVertex(x, y1, facingPlusX ? z0 : z1, 1, 1, 1);
        int d = m.addVertex(x, y1, facingPlusX ? z1 : z0, 1, 1, 1);
        return new int[]{a, b, c, d};
    }
}
