package com.grafkom.render;

import com.grafkom.render.Texture2D.FilterMode;

/**
 * Seluruh pengaturan interaktif "3D Visual Realism Suite" dalam satu objek,
 * sehingga SceneRenderer, InputController, dan HUD berbagi state yang sama.
 */
public class RenderSettings {

    public static final float MIN_LIGHT_ELEVATION = 10.0f;
    public static final float MAX_LIGHT_ELEVATION = 85.0f;
    public static final float MIN_BIAS_SCALE = 0.0f;
    public static final float MAX_BIAS_SCALE = 20.0f;

    // Indikator 1: tombol B
    private boolean blinnPhong = true;
    // Indikator 2: tombol F
    private boolean smoothShading = true;
    // Indikator 4: tombol I (texture on/off) dan K (filtering)
    private boolean textureEnabled = true;
    private FilterMode filterMode = FilterMode.BILINEAR_MIPMAP;
    // Indikator 5: tombol P (shadow on/off), 8 / 9 (bias), 0 (animasi cahaya), - / = (tinggi cahaya)
    private boolean shadowsEnabled = true;
    private float shadowBiasScale = 1.0f;
    private float lightAzimuthDeg = -15.0f;
    private float lightElevationDeg = 48.0f;
    private boolean lightAnimating = false;
    // Tombol V: overlay wireframe segitiga
    private boolean wireframe = false;

    public boolean isBlinnPhong() { return blinnPhong; }
    public void toggleBlinnPhong() { blinnPhong = !blinnPhong; }
    public void setBlinnPhong(boolean v) { blinnPhong = v; }
    public String getShadingModelName() { return blinnPhong ? "Blinn-Phong (N.H)" : "Phong (R.V)"; }

    public boolean isSmoothShading() { return smoothShading; }
    public void toggleSmoothShading() { smoothShading = !smoothShading; }
    public void setSmoothShading(boolean v) { smoothShading = v; }

    public boolean isTextureEnabled() { return textureEnabled; }
    public void toggleTexture() { textureEnabled = !textureEnabled; }
    public void setTextureEnabled(boolean v) { textureEnabled = v; }

    public FilterMode getFilterMode() { return filterMode; }
    public void setFilterMode(FilterMode m) { filterMode = m; }

    public boolean isShadowsEnabled() { return shadowsEnabled; }
    public void toggleShadows() { shadowsEnabled = !shadowsEnabled; }
    public void setShadowsEnabled(boolean v) { shadowsEnabled = v; }

    public float getShadowBiasScale() { return shadowBiasScale; }
    public void setShadowBiasScale(float s) { shadowBiasScale = Math.max(MIN_BIAS_SCALE, Math.min(MAX_BIAS_SCALE, s)); }

    public float getLightAzimuthDeg() { return lightAzimuthDeg; }
    public void setLightAzimuthDeg(float a) { lightAzimuthDeg = a % 360f; }
    public float getLightElevationDeg() { return lightElevationDeg; }
    public void setLightElevationDeg(float e) {
        lightElevationDeg = Math.max(MIN_LIGHT_ELEVATION, Math.min(MAX_LIGHT_ELEVATION, e));
    }
    public boolean isLightAnimating() { return lightAnimating; }
    public void toggleLightAnimating() { lightAnimating = !lightAnimating; }

    public boolean isWireframe() { return wireframe; }
    public void toggleWireframe() { wireframe = !wireframe; }
    public void setWireframe(boolean v) { wireframe = v; }
}
