package com.grafkom.app;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.PerspectiveProjection;
import com.grafkom.projection.ProjectionTaxonomyManager.ProjectionType;
import com.grafkom.render.StereoAnaglyphRenderer;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Listener keyboard & mouse (GLFW callbacks) untuk mengontrol kamera orbital,
 * frustum, taksonomi proyeksi, dan stereo.
 *
 * - Tombol yang ditahan (orbit/zoom) diproses tiap frame di update(dt) agar gerakan
 *   halus dan tidak bergantung pada key-repeat sistem operasi.
 * - Tombol aksi (ganti mode, toggle) diproses di key callback.
 */
public class InputController {

    private static final Map<Long, InputController> INSTANCES = new HashMap<>();

    private static final float KEY_ORBIT_SPEED = 90.0f;   // derajat per detik
    private static final float MOUSE_SENSITIVITY = 0.35f; // derajat per pixel
    private static final float FOV_STEP = 2.0f;

    private final long window;
    private final ThreeDViewing app;

    private boolean dragging = false;
    private double lastX, lastY;

    public InputController(long window, ThreeDViewing app) {
        this.window = window;
        this.app = app;
    }

    static InputController of(long window) {
        return INSTANCES.get(window);
    }

    public void install() {
        INSTANCES.put(window, this);
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> onKey(key, action, mods));
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> onMouseButton(button, action));
        glfwSetCursorPosCallback(window, (w, x, y) -> onCursorMove(x, y));
        glfwSetScrollCallback(window, (w, xoff, yoff) -> app.getCamera().zoom((float) Math.pow(0.9, yoff)));
        glfwSetFramebufferSizeCallback(window, (w, width, height) -> app.onFramebufferResize(width, height));
    }

    /** Polling tombol yang ditahan - dipanggil setiap frame. */
    public void update(float dt) {
        Camera3D cam = app.getCamera();
        float dAz = 0, dEl = 0;
        if (down(GLFW_KEY_A) || down(GLFW_KEY_LEFT))  dAz -= KEY_ORBIT_SPEED * dt;
        if (down(GLFW_KEY_D) || down(GLFW_KEY_RIGHT)) dAz += KEY_ORBIT_SPEED * dt;
        if (down(GLFW_KEY_W) || down(GLFW_KEY_UP))    dEl += KEY_ORBIT_SPEED * dt;
        if (down(GLFW_KEY_S) || down(GLFW_KEY_DOWN))  dEl -= KEY_ORBIT_SPEED * dt;
        if (dAz != 0 || dEl != 0) cam.orbit(dAz, dEl);

        if (down(GLFW_KEY_Q)) cam.zoom((float) Math.pow(0.4, dt)); // mendekat
        if (down(GLFW_KEY_E)) cam.zoom((float) Math.pow(2.5, dt)); // menjauh
    }

    private boolean down(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    private void onMouseButton(int button, int action) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            dragging = (action == GLFW_PRESS);
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(window, x, y);
            lastX = x[0];
            lastY = y[0];
        }
    }

    private void onCursorMove(double x, double y) {
        if (dragging) {
            float dx = (float) (x - lastX);
            float dy = (float) (y - lastY);
            app.getCamera().orbit(-dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
        }
        lastX = x;
        lastY = y;
    }

    private void onKey(int key, int action, int mods) {
        if (action == GLFW_RELEASE) return;
        boolean repeat = (action == GLFW_REPEAT);
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0;

        PerspectiveProjection proj = app.getProjection();
        StereoAnaglyphRenderer stereo = app.getStereo();

        // Tombol 1..7 -> pilih jenis proyeksi (enum switcher)
        if (!repeat && key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
            ProjectionType t = ProjectionType.fromKeyNumber(key - GLFW_KEY_0);
            if (t != null) { app.selectProjection(t); return; }
        }
        if (!repeat && key >= GLFW_KEY_KP_1 && key <= GLFW_KEY_KP_9) {
            ProjectionType t = ProjectionType.fromKeyNumber(key - GLFW_KEY_KP_0);
            if (t != null) { app.selectProjection(t); return; }
        }

        switch (key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);

            // ---- FOV & Dolly Zoom ----
            case GLFW_KEY_LEFT_BRACKET  -> changeFov(-FOV_STEP, shift);
            case GLFW_KEY_RIGHT_BRACKET -> changeFov(+FOV_STEP, shift);

            // ---- Frustum clipping ----
            case GLFW_KEY_N      -> proj.setZNear(proj.getZNear() * 0.8f);
            case GLFW_KEY_M      -> proj.setZNear(proj.getZNear() * 1.25f);
            case GLFW_KEY_COMMA  -> proj.setZFar(proj.getZFar() * 0.85f);
            case GLFW_KEY_PERIOD -> proj.setZFar(proj.getZFar() * 1.18f);

            // ---- Stereo ----
            case GLFW_KEY_X -> { if (!repeat) { stereo.toggle(); app.flash("Stereo anaglyph " + (stereo.isEnabled() ? "ON - pakai kacamata merah-cyan" : "OFF")); } }
            case GLFW_KEY_T -> { if (!repeat) { stereo.toggleMode(); app.flash("Mode stereo: " + stereo.getMode()); } }
            case GLFW_KEY_Y -> { if (!repeat) stereo.toggleSwapEyes(); }
            case GLFW_KEY_J -> stereo.setIpd(stereo.getIpd() - 0.01f);
            case GLFW_KEY_L -> stereo.setIpd(stereo.getIpd() + 0.01f);
            case GLFW_KEY_U -> stereo.setConvergenceFactor(stereo.getConvergenceFactor() - 0.05f);
            case GLFW_KEY_O -> stereo.setConvergenceFactor(stereo.getConvergenceFactor() + 0.05f);

            // ---- Lain-lain ----
            case GLFW_KEY_SPACE -> { if (!repeat) app.toggleAutoRotate(); }
            case GLFW_KEY_R     -> { if (!repeat) app.resetCamera(); }
            case GLFW_KEY_H     -> { if (!repeat) app.nextModel(); }
            case GLFW_KEY_G     -> { if (!repeat) app.getScene().toggleGrid(); }

            // ---- Visual Realism Suite (Minggu 11) ----
            case GLFW_KEY_B -> { if (!repeat) app.toggleShadingModel(); }   // Indikator 1: Phong <-> Blinn-Phong
            case GLFW_KEY_F -> { if (!repeat) app.toggleFlatSmooth(); }     // Indikator 2: Flat <-> Smooth
            case GLFW_KEY_Z -> { if (!repeat) app.toggleDepthTest(); }      // Indikator 3: Z-Buffer
            case GLFW_KEY_C -> { if (!repeat) app.toggleCulling(); }        // Indikator 3: Back-Face Culling
            case GLFW_KEY_I -> { if (!repeat) app.toggleTexture(); }        // Indikator 4: tekstur on/off
            case GLFW_KEY_K -> { if (!repeat) app.toggleFilterMode(); }     // Indikator 4: Nearest <-> Bilinear Mipmap
            case GLFW_KEY_P -> { if (!repeat) app.toggleShadows(); }        // Indikator 5: shadow mapping on/off
            case GLFW_KEY_8 -> app.adjustShadowBias(1 / 1.25f);             // Indikator 5: bias -
            case GLFW_KEY_9 -> app.adjustShadowBias(1.25f);                 // Indikator 5: bias +
            case GLFW_KEY_0 -> { if (!repeat) app.toggleLightAnimation(); }
            case GLFW_KEY_MINUS -> app.adjustLightElevation(-3.0f);
            case GLFW_KEY_EQUAL -> app.adjustLightElevation(+3.0f);
            case GLFW_KEY_V -> { if (!repeat) app.toggleWireframe(); }
            case GLFW_KEY_F1    -> { if (!repeat) app.toggleHelp(); }
            case GLFW_KEY_F12   -> { if (!repeat) app.requestScreenshot(); }
            default -> { }
        }
    }

    /**
     * Ubah FOV. Bila dolly = true (Shift), radius kamera diubah sehingga
     * r * tan(FOV/2) konstan → ukuran objek tetap, tetapi distorsi perspektif berubah
     * (efek "Vertigo" / Dolly Zoom).
     */
    private void changeFov(float delta, boolean dolly) {
        PerspectiveProjection proj = app.getProjection();
        Camera3D cam = app.getCamera();
        float oldFov = proj.getFovDegree();
        proj.setFovDegree(oldFov + delta);
        float newFov = proj.getFovDegree();
        if (dolly && newFov != oldFov) {
            double k = Math.tan(Math.toRadians(oldFov) / 2) / Math.tan(Math.toRadians(newFov) / 2);
            float newR = (float) (cam.getRadius() * k);
            if (newR < Camera3D.MIN_RADIUS || newR > Camera3D.MAX_RADIUS) {
                proj.setFovDegree(oldFov); // batas radius tercapai
                return;
            }
            cam.setRadiusImmediate(newR);
            app.flash(String.format(java.util.Locale.US, "Dolly Zoom: FOV %.0f, r %.2f", newFov, newR));
        }
    }
}
