package com.grafkom.projection;

import org.joml.Matrix4f;

/**
 * Indikator 2 - Proyeksi Perspektif & Viewing Frustum.
 *
 * Frustum didefinisikan oleh 4 parameter: FOV_y, aspect ratio, zNear, zFar.
 * Semua matriks dihitung EKSPLISIT (manual), tidak memakai gluPerspective / JOML.perspective.
 *
 *     | f/aspect  0        0                    0                 |
 * P = | 0         f        0                    0                 |
 *     | 0         0   -(far+near)/(far-near)  -2*far*near/(far-near)|
 *     | 0         0       -1                    0                 |
 *     f = 1 / tan(FOV_y / 2)
 *
 * Aspect ratio selalu diperbarui saat jendela di-resize sehingga objek tidak terdistorsi.
 */
public class PerspectiveProjection {

    public static final float MIN_FOV = 10.0f;
    public static final float MAX_FOV = 150.0f;
    public static final float MIN_NEAR = 0.05f;
    public static final float MAX_FAR = 500.0f;

    private float fovDegree;
    private float aspectRatio;
    private float zNear;
    private float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = fovDegree;
        this.aspectRatio = aspectRatio;
        this.zNear = zNear;
        this.zFar = zFar;
    }

    /** Matriks proyeksi perspektif simetris (dihitung manual). */
    public Matrix4f calculateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fovDegree);
        float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);
        return proj;
    }

    /**
     * Matriks frustum umum (boleh asimetris) - setara glFrustum, dihitung manual:
     * <pre>
     *  | 2n/(r-l)   0        (r+l)/(r-l)    0          |
     *  | 0         2n/(t-b)  (t+b)/(t-b)    0          |
     *  | 0          0       -(f+n)/(f-n)  -2fn/(f-n)   |
     *  | 0          0        -1             0          |
     * </pre>
     */
    public static Matrix4f calculateFrustumMatrix(float l, float r, float b, float t, float n, float f) {
        Matrix4f m = new Matrix4f();
        m.zero();
        m.m00(2.0f * n / (r - l));
        m.m11(2.0f * n / (t - b));
        m.m20((r + l) / (r - l));
        m.m21((t + b) / (t - b));
        m.m22(-(f + n) / (f - n));
        m.m32(-(2.0f * f * n) / (f - n));
        m.m23(-1.0f);
        m.m33(0.0f);
        return m;
    }

    /**
     * Indikator 3 - Off-Axis (Asymmetric Frustum) untuk stereo.
     * Kedua mata memiliki sumbu pandang paralel, tetapi jendela frustum digeser
     * sehingga kedua frustum berimpit tepat pada bidang konvergensi (zero parallax).
     *
     * @param eyeOffsetX          posisi mata relatif pusat kamera di sumbu u (kiri = -IPD/2, kanan = +IPD/2)
     * @param convergenceDistance jarak bidang layar / zero parallax dari kamera
     */
    public Matrix4f calculateOffAxisMatrix(float eyeOffsetX, float convergenceDistance) {
        float tanHalf = (float) Math.tan(Math.toRadians(fovDegree) / 2.0);
        float top = zNear * tanHalf;
        float bottom = -top;
        float halfWidthAtConvergence = aspectRatio * convergenceDistance * tanHalf;
        float scale = zNear / convergenceDistance; // proyeksikan dari bidang konvergensi ke near plane
        float left = (-halfWidthAtConvergence - eyeOffsetX) * scale;
        float right = (halfWidthAtConvergence - eyeOffsetX) * scale;
        return calculateFrustumMatrix(left, right, bottom, top, zNear, zFar);
    }

    /** Setengah tinggi volume pandang pada jarak tertentu (dipakai agar proyeksi paralel seukuran perspektif). */
    public float getHalfHeightAt(float distance) {
        return distance * (float) Math.tan(Math.toRadians(fovDegree) / 2.0);
    }

    // ---------------- Getter / Setter (dengan validasi) ----------------

    public float getFovDegree()  { return fovDegree; }
    public float getAspectRatio(){ return aspectRatio; }
    public float getZNear()      { return zNear; }
    public float getZFar()       { return zFar; }

    /** Setter FOV untuk pengujian interaktif (termasuk efek Dolly Zoom). */
    public void setFovDegree(float fovDegree) {
        this.fovDegree = Math.max(MIN_FOV, Math.min(MAX_FOV, fovDegree));
    }

    public void setAspectRatio(float aspectRatio) {
        if (aspectRatio > 0 && Float.isFinite(aspectRatio)) this.aspectRatio = aspectRatio;
    }

    public void setZNear(float zNear) {
        this.zNear = Math.max(MIN_NEAR, Math.min(zFar - 0.1f, zNear));
    }

    public void setZFar(float zFar) {
        this.zFar = Math.min(MAX_FAR, Math.max(zNear + 0.1f, zFar));
    }
}
