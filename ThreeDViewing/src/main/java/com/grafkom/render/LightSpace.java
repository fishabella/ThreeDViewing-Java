package com.grafkom.render;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.ProjectionTaxonomyManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Matematika cahaya directional untuk Shadow Mapping (tanpa OpenGL, sehingga bisa diuji).
 *
 * <pre>
 *   dir        = (cos(el) sin(az), sin(el), cos(el) cos(az))     arah MENUJU cahaya
 *   lightPos   = center + dir * distance
 *   V_light    = LookAt(lightPos, center, up)                    (perhitungan manual Camera3D)
 *   P_light    = Ortho(-E, E, -E, E, near, far)                  (manual, cahaya directional)
 *   LightSpace = P_light * V_light      →  Pos_light = LightSpace * Pos_world
 * </pre>
 */
public final class LightSpace {

    public static final float DISTANCE = 15.0f;
    public static final float HALF_EXTENT = 6.0f;    // setengah lebar volume ortografik cahaya
    public static final float NEAR = 1.0f, FAR = 35.0f;
    public static final Vector3f CENTER = new Vector3f(0f, -0.3f, 0f);

    private LightSpace() { }

    /** Arah menuju cahaya (vektor satuan). */
    public static Vector3f direction(float azimuthDeg, float elevationDeg, Vector3f dest) {
        float az = (float) Math.toRadians(azimuthDeg);
        float el = (float) Math.toRadians(elevationDeg);
        return dest.set((float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.cos(az)));
    }

    /** Matriks light-space = P_ortho * V_light. */
    public static Matrix4f matrix(float azimuthDeg, float elevationDeg, Matrix4f dest) {
        Vector3f dir = direction(azimuthDeg, elevationDeg, new Vector3f());
        Vector3f lightPos = new Vector3f(dir).mul(DISTANCE).add(CENTER);
        Matrix4f view = Camera3D.calculateLookAtMatrix(lightPos, CENTER, new Vector3f(0, 1, 0));
        Matrix4f ortho = ProjectionTaxonomyManager.calculateOrthographicMatrix(
                -HALF_EXTENT, HALF_EXTENT, -HALF_EXTENT, HALF_EXTENT, NEAR, FAR);
        return dest.set(ortho).mul(view);
    }
}
