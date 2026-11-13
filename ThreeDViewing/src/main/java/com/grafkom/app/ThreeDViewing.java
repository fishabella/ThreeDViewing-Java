package com.grafkom.app;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.PerspectiveProjection;
import com.grafkom.projection.ProjectionTaxonomyManager;
import com.grafkom.projection.ProjectionTaxonomyManager.ProjectionType;
import com.grafkom.render.HSRManager;
import com.grafkom.render.HudRenderer;
import com.grafkom.render.RenderSettings;
import com.grafkom.render.Texture2D.FilterMode;
import com.grafkom.render.SceneRenderer;
import com.grafkom.render.StereoAnaglyphRenderer;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (pengembangan 3D Viewing Interactive Visualizer, Minggu ke-10).
 *
 * Menyatukan seluruh komponen:
 *   - com.grafkom.camera     : Camera3D (LookAt manual + orbital)
 *   - com.grafkom.projection : PerspectiveProjection, ProjectionTaxonomyManager
 *   - com.grafkom.shader     : ShaderProgram (GLSL 330 core: phong.vert/frag, depth.vert/frag)
 *   - com.grafkom.render     : SceneRenderer, HSRManager, Texture2D, ShadowMapFBO, RenderSettings,
 *                              StereoAnaglyphRenderer, HudRenderer
 *   - com.grafkom.model      : MeshBuilder, MeshFactory, TexturedMesh (normal vertex + UV)
 *   - com.grafkom.app        : ThreeDViewing (window & loop), InputController (listener)
 *
 * Argumen opsional:  --capture-all [folder]
 *   Menyimpan screenshot kelima indikator Minggu 11 (Phong vs Blinn-Phong, Flat vs Smooth, HSR on/off,
 *   Texture Nearest vs Bilinear Mipmap, Shadow Mapping + bias) secara otomatis lalu keluar.
 */
public class ThreeDViewing {

    public static final float DEFAULT_RADIUS = 7.0f;
    public static final float DEFAULT_FOV = 60.0f;

    private long window;
    private int fbWidth = 1280, fbHeight = 720;

    private final Camera3D camera;
    private final PerspectiveProjection projection;
    private final ProjectionTaxonomyManager taxonomy = new ProjectionTaxonomyManager();
    private final StereoAnaglyphRenderer stereo = new StereoAnaglyphRenderer();
    private SceneRenderer scene;   // dibuat di init(): butuh konteks OpenGL aktif
    private HudRenderer hud;

    private boolean showHelp = true;
    private boolean autoRotate = false;
    private boolean screenshotRequested = false;
    private String statusMessage = "";
    private double statusUntil = 0;
    private float fps = 0;

    public ThreeDViewing() {
        camera = new Camera3D(new Vector3f(0, 0, DEFAULT_RADIUS), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        projection = new PerspectiveProjection(DEFAULT_FOV, 16f / 9f, 0.1f, 100.0f);
        ProjectionType start = taxonomy.getCurrent();
        camera.animateTo(start.getPresetAzimuth(), start.getPresetElevation());
        camera.snapToDesired();
    }

    public static void main(String[] args) {
        new ThreeDViewing().run(args);
    }

    public void run(String[] args) {
        init();
        try {
            if (args.length > 0 && args[0].equals("--capture-all")) {
                captureAll(args.length > 1 ? args[1] : "screenshots");
            } else {
                printControls();
                loop();
            }
        } finally {
            if (scene != null) scene.dispose();
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            glfwTerminate();
            GLFWErrorCallback cb = glfwSetErrorCallback(null);
            if (cb != null) cb.free();
        }
    }

    // =====================================================================
    //  INISIALISASI WINDOW & OPENGL
    // =====================================================================

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW gagal diinisialisasi");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4); // anti-aliasing MSAA
        glfwWindowHint(GLFW_DEPTH_BITS, 24); // Z-buffer 24-bit: mengurangi Z-fighting

        window = glfwCreateWindow(1280, 720, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Gagal membuat window GLFW");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // v-sync
        GL.createCapabilities();
        System.out.println("OpenGL: " + glGetString(GL_VERSION) + " | GPU: " + glGetString(GL_RENDERER));
        if (!GL.getCapabilities().OpenGL33) {
            throw new IllegalStateException("Dibutuhkan OpenGL 3.3+ (GLSL 330) - versi saat ini: " + glGetString(GL_VERSION));
        }
        hud = new HudRenderer();
        scene = new SceneRenderer();

        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        onFramebufferResize(w[0], h[0]);

        new InputController(window, this).install();

        glEnable(0x809D); // GL_MULTISAMPLE (state depth test & culling diatur HSRManager)
        glEnable(GL_LINE_SMOOTH);
        glfwShowWindow(window);
    }

    /** Dipanggil saat ukuran framebuffer berubah: perbarui viewport & aspect ratio (anti distorsi). */
    void onFramebufferResize(int width, int height) {
        if (width <= 0 || height <= 0) return; // window di-minimize
        fbWidth = width;
        fbHeight = height;
        projection.setAspectRatio((float) width / height);
    }

    // =====================================================================
    //  MAIN LOOP
    // =====================================================================

    private void loop() {
        double last = glfwGetTime();
        double titleTimer = 0;
        int frames = 0;
        InputController input = InputController.of(window);

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(0.1, now - last);
            last = now;

            glfwPollEvents();
            input.update(dt);
            if (autoRotate) camera.orbit(20.0f * dt, 0);
            camera.update(dt);
            scene.updateLight(dt);

            renderFrame();

            if (screenshotRequested) {
                screenshotRequested = false;
                String name = "screenshots/shot_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
                saveScreenshot(new File(name));
                flash("Screenshot disimpan: " + name);
            }
            glfwSwapBuffers(window);

            frames++;
            titleTimer += dt;
            if (titleTimer >= 0.5) {
                fps = (float) (frames / titleTimer);
                frames = 0;
                titleTimer = 0;
                glfwSetWindowTitle(window, String.format(Locale.US,
                        "3D Visual Realism Suite | %s | %s | Z-Buffer %s | Cull %s | Shadow %s | %.0f FPS",
                        scene.getSettings().getShadingModelName(),
                        scene.getSettings().isSmoothShading() ? "Smooth" : "Flat",
                        scene.getHsr().isDepthTestEnabled() ? "ON" : "OFF",
                        scene.getHsr().isBackFaceCullingEnabled() ? "ON" : "OFF",
                        scene.getSettings().isShadowsEnabled() ? "ON" : "OFF", fps));
            }
        }
    }

    /** Render satu frame lengkap (scene + HUD) ke back buffer. */
    private void renderFrame() {
        // Pass 1 (Depth Pass): scene dari sudut pandang cahaya → Shadow Map. Sekali per frame (juga untuk stereo).
        scene.renderShadowPass(fbWidth, fbHeight);

        // Pass 2 (Lighting Pass): scene dari kamera
        glViewport(0, 0, fbWidth, fbHeight);
        glClearColor(0.10f, 0.11f, 0.14f, 1.0f);
        glColorMask(true, true, true, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (stereo.isEnabled()) {
            // Desaturasi sebagian agar warna murni tetap terlihat oleh kedua mata
            scene.setDesaturation(0.55f);
            stereo.renderAnaglyph(camera, projection, scene::render);
        } else {
            scene.setDesaturation(0.0f);
            taxonomy.applyProjection(projection, camera.getRadius());
            glMatrixMode(GL_MODELVIEW);
            glLoadMatrixf(camera.calculateLookAtMatrix().get(new float[16]));
            scene.render();
        }

        float hudScale = Math.max(1.5f, Math.min(2.2f, fbHeight / 420f));
        hud.drawPanel(buildStatusText(), 14, 12, hudScale, fbWidth, fbHeight);
        if (showHelp) {
            String help = buildHelpText();
            float wPx = (HudRenderer.textWidth(help) + 6) * hudScale;
            hud.drawPanel(help, fbWidth - wPx - 14, 12, hudScale, fbWidth, fbHeight);
        }
        if (glfwGetTime() < statusUntil) {
            hud.drawPanel(statusMessage, 14, fbHeight - 24 * hudScale, hudScale, fbWidth, fbHeight);
        }
    }

    private String buildStatusText() {
        ProjectionType t = taxonomy.getCurrent();
        StringBuilder sb = new StringBuilder();
        sb.append("PROYEKSI [").append(t.getKeyNumber()).append("] ").append(t.getLabel()).append('\n');
        sb.append(String.format(Locale.US, "Kamera  r=%.2f  az=%.1f  el=%.1f\n",
                camera.getRadius(), normalizeAngle(camera.getAzimuthDeg()), camera.getElevationDeg()));
        Vector3f e = camera.getEye();
        sb.append(String.format(Locale.US, "Eye (%.2f, %.2f, %.2f)  At (0, 0, 0)  Up (0, 1, 0)\n", e.x, e.y, e.z));
        sb.append(String.format(Locale.US, "Frustum FOVy=%.1f  aspect=%.3f  near=%.2f  far=%.1f\n",
                projection.getFovDegree(), projection.getAspectRatio(), projection.getZNear(), projection.getZFar()));
        if (stereo.isEnabled()) {
            sb.append(String.format(Locale.US, "STEREO ANAGLYPH ON  mode=%s  IPD=%.3f  konvergensi=%.2f (x%.2f)\n",
                    stereo.getMode() == StereoAnaglyphRenderer.StereoMode.OFF_AXIS ? "Off-Axis (Asymmetric)" : "Toe-In",
                    stereo.getIpd(), stereo.getConvergenceDistance(camera), stereo.getConvergenceFactor()));
            String par = switch (stereo.getTargetParallax()) {
                case ZERO -> "ZERO parallax (objek di bidang layar)";
                case POSITIVE -> "POSITIVE parallax (objek di belakang layar)";
                case NEGATIVE -> "NEGATIVE parallax (objek melayang di depan layar)";
            };
            sb.append("Objek: ").append(par).append(stereo.isSwapEyes() ? "  [mata ditukar]" : "")
              .append("  | Kiri=Merah, Kanan=Cyan\n");
            if (t.isParallel()) sb.append("(stereo selalu memakai frustum perspektif)\n");
        } else {
            sb.append("Stereo: OFF (tekan X)\n");
        }
        RenderSettings rs = scene.getSettings();
        HSRManager hsr = scene.getHsr();
        sb.append(String.format(Locale.US, "Model: %s (%d segitiga)   %.0f FPS\n",
                scene.getModel().getName(), scene.getModel().getTriangleCount(), fps));
        sb.append("Shading [B]: ").append(rs.isBlinnPhong() ? "Blinn-Phong" : "Phong")
          .append("   [F]: ").append(rs.isSmoothShading() ? "Smooth (vertex normal avg)" : "Flat (face normal)").append('\n');
        sb.append("Z-Buffer [Z]: ").append(hsr.isDepthTestEnabled() ? "ON" : "OFF")
          .append("   Back-Face Culling [C]: ").append(hsr.isBackFaceCullingEnabled() ? "ON" : "OFF").append('\n');
        sb.append("Tekstur [I]: ").append(rs.isTextureEnabled() ? "ON" : "OFF")
          .append("   Filter [K]: ").append(rs.getFilterMode() == FilterMode.NEAREST ? "Nearest" : "Bilinear + Mipmap").append('\n');
        sb.append(String.format(Locale.US, "Shadow [P]: %s   Bias [8/9]: x%.2f   Cahaya az %.0f el %.0f%s",
                rs.isShadowsEnabled() ? "ON" : "OFF", rs.getShadowBiasScale(),
                rs.getLightAzimuthDeg(), rs.getLightElevationDeg(), rs.isLightAnimating() ? " (anim)" : ""));
        if (!showHelp) sb.append("   | F1 = bantuan");
        return sb.toString();
    }

    private String buildHelpText() {
        StringBuilder sb = new StringBuilder("KONTROL\n");
        sb.append("Mouse kiri drag / WASD / panah : orbit kamera\n");
        sb.append("Scroll / Q E : zoom (radius)\n");
        sb.append("Spasi : auto-rotate     R : reset kamera\n");
        sb.append("[ ]  : FOV -/+    Shift+[ ] : Dolly Zoom\n");
        sb.append("N M  : near -/+   , .  : far -/+\n");
        for (ProjectionType p : ProjectionType.values()) {
            sb.append(p.getKeyNumber()).append("    : ").append(p.getLabel()).append('\n');
        }
        sb.append("X : stereo anaglyph on/off   T : Off-Axis / Toe-In\n");
        sb.append("J L : IPD -/+    U O : konvergensi -/+   Y : tukar mata\n");
        sb.append("H : model (Sphere/Torus/Rumah/Cube)   G : grid\n");
        sb.append("B : Phong/Blinn   F : Flat/Smooth   V : wireframe\n");
        sb.append("Z : Z-Buffer   C : Back-Face Culling\n");
        sb.append("I : tekstur   K : Nearest/Bilinear Mipmap\n");
        sb.append("P : shadow   8 9 : bias -/+\n");
        sb.append("0 : animasi cahaya   - = : tinggi cahaya\n");
        sb.append("F12 : screenshot   F1 : sembunyikan bantuan   Esc : keluar");
        return sb.toString();
    }

    private void printControls() {
        System.out.println("=== 3D Visual Realism Suite ===");
        System.out.println(buildHelpText());
    }

    // =====================================================================
    //  AKSI (dipanggil oleh InputController)
    // =====================================================================

    void selectProjection(ProjectionType type) {
        taxonomy.setCurrent(type);
        camera.animateTo(type.getPresetAzimuth(), type.getPresetElevation());
        flash("Proyeksi: " + type.getLabel());
    }

    void resetCamera() {
        projection.setFovDegree(DEFAULT_FOV);
        projection.setZNear(0.1f);
        projection.setZFar(100f);
        camera.setDesiredRadius(DEFAULT_RADIUS);
        ProjectionType t = taxonomy.getCurrent();
        camera.animateTo(t.getPresetAzimuth(), t.getPresetElevation());
        flash("Kamera di-reset");
    }

    void nextModel() {
        scene.nextModel();
        flash("Model: " + scene.getModel().getName());
    }

    // ---- Visual Realism Suite ----

    void toggleShadingModel() {
        scene.getSettings().toggleBlinnPhong();
        flash("Shading model: " + scene.getSettings().getShadingModelName());
    }

    void toggleFlatSmooth() {
        scene.getSettings().toggleSmoothShading();
        flash(scene.getSettings().isSmoothShading()
                ? "Smooth Shading (normal vertex dirata-rata)" : "Flat Shading (normal per-face)");
    }

    void toggleDepthTest() {
        scene.getHsr().toggleDepthTest();
        flash("Z-Buffer Depth Test " + (scene.getHsr().isDepthTestEnabled() ? "ON" : "OFF (urutan gambar menentukan hasil)"));
    }

    void toggleCulling() {
        scene.getHsr().toggleBackFaceCulling();
        flash("Back-Face Culling " + (scene.getHsr().isBackFaceCullingEnabled() ? "ON" : "OFF"));
    }

    void toggleTexture() {
        scene.getSettings().toggleTexture();
        flash("Tekstur " + (scene.getSettings().isTextureEnabled() ? "ON" : "OFF"));
    }

    void toggleFilterMode() {
        scene.toggleFilterMode();
        flash("Filtering: " + scene.getSettings().getFilterMode().getLabel());
    }

    void toggleShadows() {
        scene.getSettings().toggleShadows();
        flash("Shadow Mapping " + (scene.getSettings().isShadowsEnabled() ? "ON" : "OFF"));
    }

    void adjustShadowBias(float factor) {
        RenderSettings rs = scene.getSettings();
        float v = rs.getShadowBiasScale();
        rs.setShadowBiasScale(factor > 1 ? Math.max(0.05f, v) * factor : (v * factor < 0.05f ? 0f : v * factor));
        flash(String.format(Locale.US, "Shadow bias x%.2f %s", rs.getShadowBiasScale(),
                rs.getShadowBiasScale() == 0f ? "(tanpa bias: Shadow Acne muncul)"
                        : rs.getShadowBiasScale() > 6f ? "(terlalu besar: Peter Panning)" : ""));
    }

    void toggleLightAnimation() {
        scene.getSettings().toggleLightAnimating();
        flash("Animasi cahaya " + (scene.getSettings().isLightAnimating() ? "ON" : "OFF"));
    }

    void adjustLightElevation(float deltaDeg) {
        RenderSettings rs = scene.getSettings();
        rs.setLightElevationDeg(rs.getLightElevationDeg() + deltaDeg);
    }

    void toggleWireframe() {
        scene.getSettings().toggleWireframe();
        flash("Wireframe " + (scene.getSettings().isWireframe() ? "ON" : "OFF"));
    }

    void flash(String msg) {
        statusMessage = msg;
        statusUntil = glfwGetTime() + 2.5;
    }

    void toggleHelp()        { showHelp = !showHelp; }
    void toggleAutoRotate()  { autoRotate = !autoRotate; flash("Auto-rotate " + (autoRotate ? "ON" : "OFF")); }
    void requestScreenshot() { screenshotRequested = true; }

    Camera3D getCamera()                    { return camera; }
    PerspectiveProjection getProjection()   { return projection; }
    StereoAnaglyphRenderer getStereo()      { return stereo; }
    SceneRenderer getScene()                { return scene; }

    // =====================================================================
    //  SCREENSHOT
    // =====================================================================

    private void saveScreenshot(File file) {
        ByteBuffer buf = BufferUtils.createByteBuffer(fbWidth * fbHeight * 4);
        glReadBuffer(GL_BACK);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, fbWidth, fbHeight, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(fbWidth, fbHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < fbHeight; y++) {
            for (int x = 0; x < fbWidth; x++) {
                int i = (x + (fbHeight - 1 - y) * fbWidth) * 4; // OpenGL origin di kiri-bawah
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try {
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            ImageIO.write(img, "png", file);
            System.out.println("Screenshot: " + file.getPath());
        } catch (IOException ex) {
            System.err.println("Gagal menyimpan screenshot: " + ex.getMessage());
        }
    }

    /** Ambil screenshot kelima indikator Minggu 11 secara otomatis (untuk lampiran laporan). */
    private void captureAll(String folder) {
        RenderSettings rs = scene.getSettings();
        HSRManager hsr = scene.getHsr();
        taxonomy.setCurrent(ProjectionType.PERSPECTIVE_3POINT);
        camera.animateTo(40.0f, 24.0f);
        camera.snapToDesired();
        rs.setLightAzimuthDeg(-15f);
        rs.setLightElevationDeg(48f);
        scene.selectModel(0); // Sphere

        // (a) Indikator 1 - Phong vs Blinn-Phong (tanpa tekstur & bayangan agar highlight terlihat jelas)
        rs.setTextureEnabled(false); rs.setShadowsEnabled(false); rs.setSmoothShading(true);
        rs.setBlinnPhong(false); shot(folder, "01_shading_phong");
        rs.setBlinnPhong(true);  shot(folder, "02_shading_blinn_phong");

        // (b) Indikator 2 - Flat vs Smooth (+ wireframe agar tessellasi tampak)
        rs.setSmoothShading(false); shot(folder, "03_flat_shading");
        rs.setSmoothShading(true);  shot(folder, "04_smooth_shading");
        rs.setWireframe(true);
        rs.setSmoothShading(false); shot(folder, "05_flat_shading_wireframe");
        rs.setSmoothShading(true);  rs.setWireframe(false);

        // (c) Indikator 3 - HSR (rumah bertekstur)
        scene.selectModel(2);
        rs.setTextureEnabled(true);
        shot(folder, "06_hsr_semua_on");
        hsr.setDepthTestEnabled(false); shot(folder, "07_zbuffer_off");
        hsr.setDepthTestEnabled(true);
        hsr.setBackFaceCullingEnabled(false); rs.setWireframe(true); shot(folder, "08_culling_off_wireframe");
        hsr.setBackFaceCullingEnabled(true);                          shot(folder, "09_culling_on_wireframe");
        rs.setWireframe(false);

        // (d) Indikator 4 - Texture Filtering (kamera rendah agar lantai miring → aliasing terlihat)
        camera.animateTo(40.0f, 9.0f);
        camera.snapToDesired();
        scene.selectModel(1);
        scene.setFilterMode(FilterMode.NEAREST);         shot(folder, "10_texture_nearest");
        scene.setFilterMode(FilterMode.BILINEAR_MIPMAP); shot(folder, "11_texture_bilinear_mipmap");

        // (e) Indikator 5 - Shadow Mapping dan pengaruh bias
        camera.animateTo(40.0f, 30.0f);
        camera.snapToDesired();
        scene.selectModel(0);
        rs.setShadowsEnabled(true);
        rs.setShadowBiasScale(1.0f);  shot(folder, "12_shadow_on_bias_normal");
        rs.setShadowBiasScale(0.0f);  shot(folder, "13_shadow_bias_nol");
        rs.setShadowBiasScale(20.0f); shot(folder, "14_shadow_bias_besar_peter_panning");
        rs.setShadowBiasScale(1.0f);
        rs.setShadowsEnabled(false);  shot(folder, "15_shadow_off");
        rs.setShadowsEnabled(true);
    }

    private void shot(String folder, String name) {
        renderFrame();
        saveScreenshot(new File(folder, name + ".png"));
        glfwSwapBuffers(window);
    }

    private static float normalizeAngle(float deg) {
        float a = deg % 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
