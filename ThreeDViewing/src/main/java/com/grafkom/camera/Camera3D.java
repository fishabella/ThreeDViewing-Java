package com.grafkom.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Indikator 1 - Kamera Sintetis 3D (Synthetic Camera Model).
 *
 * Kelas ini mengenkapsulasi tiga vektor acuan kamera:
 *   - eye    (E)  : posisi kamera di World Coordinate System (WCS)
 *   - target (At) : titik yang dilihat kamera
 *   - up     (Up) : arah "atas" kamera
 *
 * Matriks LookAt (View Matrix) dihitung MANUAL dari basis ortonormal (u, v, n).
 * Navigasi yang dipakai adalah kamera orbital (turntable): posisi E berputar
 * mengelilingi At dalam koordinat sferis (radius, azimuth, elevation).
 * Pergerakan dibuat halus dengan interpolasi eksponensial (damping).
 *
 * Semua field bersifat private; getter mengembalikan salinan sehingga
 * state internal kamera tidak bisa diubah dari luar (enkapsulasi).
 */
public class Camera3D {

    public static final float MIN_ELEVATION = -89.0f; // dicegah ±90° agar Up x n tidak nol
    public static final float MAX_ELEVATION = 89.0f;
    public static final float MIN_RADIUS = 1.5f;
    public static final float MAX_RADIUS = 60.0f;

    private final Vector3f eye;
    private final Vector3f target;
    private final Vector3f up;

    // Posisi orbital saat ini (yang sedang ditampilkan)
    private float radius;
    private float azimuthDeg;
    private float elevationDeg;

    // Posisi orbital tujuan (diubah oleh input); kamera "mengejar" nilai ini dengan halus
    private float desiredRadius;
    private float desiredAzimuthDeg;
    private float desiredElevationDeg;

    /** Semakin besar semakin cepat kamera mencapai posisi tujuan (satuan 1/detik). */
    private float smoothness = 12.0f;

    public Camera3D(Vector3fc eye, Vector3fc target, Vector3fc up) {
        this.eye = new Vector3f(eye);
        this.target = new Vector3f(target);
        this.up = new Vector3f(up);

        // Turunkan koordinat sferis dari posisi eye awal
        Vector3f d = new Vector3f(eye).sub(target);
        float len = d.length();
        this.radius = clamp(len, MIN_RADIUS, MAX_RADIUS);
        this.elevationDeg = clamp((float) Math.toDegrees(Math.asin(d.y / len)), MIN_ELEVATION, MAX_ELEVATION);
        this.azimuthDeg = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        this.desiredRadius = radius;
        this.desiredAzimuthDeg = azimuthDeg;
        this.desiredElevationDeg = elevationDeg;
        updateOrbitalPosition(radius, azimuthDeg, elevationDeg);
    }

    // =====================================================================
    //  MATRIKS LOOKAT (VIEW MATRIX) - PERHITUNGAN MANUAL
    // =====================================================================

    /** View matrix untuk kamera ini. */
    public Matrix4f calculateLookAtMatrix() {
        return calculateLookAtMatrix(eye, target, up);
    }

    /**
     * Perhitungan manual matriks LookAt:
     * <pre>
     *   n = (E - At) / |E - At|        (View Plane Normal, kamera melihat ke -n)
     *   u = (Up x n) / |Up x n|        (Right vector)
     *   v = n x u                      (True Up vector)
     *
     *            | ux  uy  uz  -u.E |
     *   M_view = | vx  vy  vz  -v.E |
     *            | nx  ny  nz  -n.E |
     *            | 0   0   0    1   |
     * </pre>
     * Catatan: JOML menyimpan matriks column-major, setter mCR(..) = kolom C, baris R.
     * Dibuat static agar bisa dipakai ulang oleh renderer stereo (posisi mata kiri/kanan).
     */
    public static Matrix4f calculateLookAtMatrix(Vector3fc eye, Vector3fc target, Vector3fc up) {
        Vector3f n = new Vector3f(eye).sub(target).normalize(); // View Plane Normal
        Vector3f u = new Vector3f(up).cross(n).normalize();     // Right Vector
        Vector3f v = new Vector3f(n).cross(u);                  // True Up Vector

        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.m00(u.x).m10(u.y).m20(u.z).m30(-u.dot(eye)); // baris 0
        viewMatrix.m01(v.x).m11(v.y).m21(v.z).m31(-v.dot(eye)); // baris 1
        viewMatrix.m02(n.x).m12(n.y).m22(n.z).m32(-n.dot(eye)); // baris 2
        viewMatrix.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);     // baris 3
        return viewMatrix;
    }

    // =====================================================================
    //  NAVIGASI KAMERA ORBITAL
    // =====================================================================

    /**
     * Update koordinat sferis untuk kamera orbital (E berputar mengelilingi At).
     *   Ex = At.x + r cos(el) sin(az)
     *   Ey = At.y + r sin(el)
     *   Ez = At.z + r cos(el) cos(az)
     */
    public void updateOrbitalPosition(float radius, float azimuthDeg, float elevationDeg) {
        this.radius = radius;
        this.azimuthDeg = azimuthDeg;
        this.elevationDeg = elevationDeg;
        float azRad = (float) Math.toRadians(azimuthDeg);
        float elRad = (float) Math.toRadians(elevationDeg);
        this.eye.x = target.x + radius * (float) (Math.cos(elRad) * Math.sin(azRad));
        this.eye.y = target.y + radius * (float) (Math.sin(elRad));
        this.eye.z = target.z + radius * (float) (Math.cos(elRad) * Math.cos(azRad));
    }

    /** Putar kamera (relatif) dalam derajat. Dipanggil oleh listener keyboard/mouse. */
    public void orbit(float deltaAzimuthDeg, float deltaElevationDeg) {
        desiredAzimuthDeg += deltaAzimuthDeg;
        desiredElevationDeg = clamp(desiredElevationDeg + deltaElevationDeg, MIN_ELEVATION, MAX_ELEVATION);
    }

    /** Zoom dengan faktor perkalian (factor < 1 = mendekat). */
    public void zoom(float factor) {
        desiredRadius = clamp(desiredRadius * factor, MIN_RADIUS, MAX_RADIUS);
    }

    /** Ubah radius seketika (tanpa animasi) - dipakai efek Dolly Zoom. */
    public void setRadiusImmediate(float r) {
        desiredRadius = clamp(r, MIN_RADIUS, MAX_RADIUS);
        updateOrbitalPosition(desiredRadius, azimuthDeg, elevationDeg);
    }

    public void setDesiredRadius(float r) {
        desiredRadius = clamp(r, MIN_RADIUS, MAX_RADIUS);
    }

    /** Animasikan kamera menuju sudut tertentu (mengambil jalur azimuth terpendek). */
    public void animateTo(float azimuthDeg, float elevationDeg) {
        float turns = Math.round((desiredAzimuthDeg - azimuthDeg) / 360.0f);
        desiredAzimuthDeg = azimuthDeg + 360.0f * turns;
        desiredElevationDeg = clamp(elevationDeg, MIN_ELEVATION, MAX_ELEVATION);
    }

    /** Lompat langsung ke posisi tujuan (tanpa animasi). */
    public void snapToDesired() {
        updateOrbitalPosition(desiredRadius, desiredAzimuthDeg, desiredElevationDeg);
    }

    /** Dipanggil tiap frame: interpolasi eksponensial agar navigasi terasa halus & independen FPS. */
    public void update(float dt) {
        float a = 1.0f - (float) Math.exp(-smoothness * dt);
        float r = radius + (desiredRadius - radius) * a;
        float az = azimuthDeg + (desiredAzimuthDeg - azimuthDeg) * a;
        float el = elevationDeg + (desiredElevationDeg - elevationDeg) * a;
        updateOrbitalPosition(r, az, el);
    }

    // =====================================================================
    //  BASIS KAMERA & GETTER (mengembalikan salinan -> enkapsulasi aman)
    // =====================================================================

    /** Vektor u (kanan kamera) di world space - dipakai untuk menggeser mata kiri/kanan pada stereo. */
    public Vector3f getRightVector() {
        Vector3f n = new Vector3f(eye).sub(target).normalize();
        return new Vector3f(up).cross(n).normalize();
    }

    /** Arah pandang (-n). */
    public Vector3f getForwardVector() {
        return new Vector3f(target).sub(eye).normalize();
    }

    public Vector3f getEye()    { return new Vector3f(eye); }
    public Vector3f getTarget() { return new Vector3f(target); }
    public Vector3f getUp()     { return new Vector3f(up); }
    public float getRadius()        { return radius; }
    public float getAzimuthDeg()    { return azimuthDeg; }
    public float getElevationDeg()  { return elevationDeg; }
    public float getDesiredRadius() { return desiredRadius; }

    public void setSmoothness(float smoothness) { this.smoothness = Math.max(0.1f, smoothness); }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
