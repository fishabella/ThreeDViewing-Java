package com.grafkom.render;

import com.grafkom.model.MeshFactory;
import com.grafkom.model.TexturedMesh;
import com.grafkom.render.Texture2D.FilterMode;
import com.grafkom.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Renderer scene "3D Visual Realism Suite".
 *
 * <pre>
 *  renderShadowPass()  → Pass 1 (Depth Pass) : scene dari sudut pandang cahaya → Shadow Map (FBO)
 *  render()            → Pass 2 (Lighting)   : scene dari kamera, GLSL Phong/Blinn-Phong + tekstur + bayangan
 * </pre>
 * render() sengaja membaca matriks PROJECTION/MODELVIEW yang sudah di-load pemanggil
 * (kamera biasa, tiap proyeksi taksonomi, atau tiap mata stereo anaglyph) sehingga seluruh fitur
 * Minggu ke-10 tetap berfungsi. Grid, sumbu, dan penanda cahaya masih memakai pipeline fixed-function.
 */
public class SceneRenderer {

    private static final float GROUND_HALF = 30.0f;
    private static final float SHADOW_HALF_EXTENT = LightSpace.HALF_EXTENT;
    private static final Vector3f SHADOW_CENTER = LightSpace.CENTER;
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final RenderSettings settings = new RenderSettings();
    private final HSRManager hsr = new HSRManager();

    private final ShaderProgram phong;
    private final ShaderProgram depthShader;
    private final Texture2D brick;
    private final Texture2D checker;
    private final ShadowMapFBO shadowFbo;

    private final List<TexturedMesh> models;
    private final TexturedMesh ground;
    private int modelIndex = 0;

    private boolean showGrid = false;
    private float desaturation = 0.0f;

    private final Matrix4f lightSpace = new Matrix4f();
    private final Vector3f lightDir = new Vector3f();
    private final float[] matBuf = new float[16];

    /** Harus dibuat SETELAH konteks OpenGL aktif (GL.createCapabilities). */
    public SceneRenderer() {
        phong = new ShaderProgram("phong");
        depthShader = new ShaderProgram("depth");
        brick = new Texture2D("/textures/brick.png");
        checker = new Texture2D("/textures/checker.png");
        shadowFbo = new ShadowMapFBO();
        models = List.of(
                MeshFactory.createSphere(1.0f, 24, 48),
                MeshFactory.createTorus(0.95f, 0.42f, 48, 24),
                MeshFactory.createHouse(),
                MeshFactory.createCube(1.0f));
        ground = MeshFactory.createGround(GROUND_HALF, 2.0f);
        applyFilterMode();
        updateLightMatrices();
    }

    // =====================================================================
    //  CAHAYA
    // =====================================================================

    public void updateLight(float dt) {
        if (settings.isLightAnimating()) settings.setLightAzimuthDeg(settings.getLightAzimuthDeg() + 25.0f * dt);
    }

    /** Hitung arah cahaya (menuju cahaya) dan matriks light-space = P_ortho * V_light (lihat LightSpace). */
    private void updateLightMatrices() {
        LightSpace.direction(settings.getLightAzimuthDeg(), settings.getLightElevationDeg(), lightDir);
        LightSpace.matrix(settings.getLightAzimuthDeg(), settings.getLightElevationDeg(), lightSpace);
    }

    // =====================================================================
    //  PASS 1 : DEPTH PASS (SHADOW MAP)
    // =====================================================================

    /** Render depth scene dari sudut pandang cahaya ke Shadow Map. Panggil SEKALI per frame sebelum pass kamera. */
    public void renderShadowPass(int fbWidth, int fbHeight) {
        updateLightMatrices();
        if (!settings.isShadowsEnabled()) return;

        shadowFbo.bindForWriting();
        glEnable(GL_DEPTH_TEST);           // pass depth selalu memakai depth test, terlepas dari tombol Z
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        float bias = settings.getShadowBiasScale();
        if (bias > 0f) {
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(2.0f * bias, 4.0f * bias);   // slope-scaled depth bias di sisi hardware (ikut skala bias)
        }

        depthShader.use();
        depthShader.setMat4("lightSpaceMatrix", lightSpace);
        depthShader.setMat4("model", IDENTITY);
        models.get(modelIndex).render(settings.isSmoothShading());   // lantai hanya penerima, tidak ikut dilempar

        glDisable(GL_POLYGON_OFFSET_FILL);
        ShaderProgram.unuse();
        shadowFbo.unbind(fbWidth, fbHeight);
    }

    // =====================================================================
    //  PASS 2 : LIGHTING PASS
    // =====================================================================

    /** Gambar scene memakai matriks PROJECTION & MODELVIEW yang sedang aktif. */
    public void render() {
        hsr.apply();

        Matrix4f projection = readMatrix(GL_PROJECTION_MATRIX);
        Matrix4f view = readMatrix(GL_MODELVIEW_MATRIX);
        Vector3f eye = new Matrix4f(view).invert().getTranslation(new Vector3f());

        phong.use();
        phong.setMat4("projection", projection);
        phong.setMat4("view", view);
        phong.setVec3("viewPos", eye);
        phong.setVec3("lightDir", lightDir);
        phong.setVec3("lightColor", 1f, 1f, 1f);
        phong.setFloat("shininess", 32.0f);
        phong.setBool("useBlinnPhong", settings.isBlinnPhong());
        phong.setBool("useTexture", settings.isTextureEnabled());
        phong.setBool("useShadows", settings.isShadowsEnabled());
        phong.setFloat("biasScale", settings.getShadowBiasScale());
        phong.setFloat("shadowTexelWorld", 2.0f * SHADOW_HALF_EXTENT / ShadowMapFBO.SHADOW_WIDTH);
        phong.setMat4("lightSpaceMatrix", lightSpace);
        phong.setFloat("desaturation", desaturation);
        phong.setBool("unlit", false);
        phong.setInt("textureSampler", 0);
        phong.setInt("shadowMap", 1);
        phong.setMat4("model", IDENTITY);
        shadowFbo.bindForReading(1);

        // ---- Lantai: polygon offset mendorong lantai sedikit ke belakang → grid/garis di atasnya tidak z-fighting
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f);
        phong.setFloat("specularStrength", 0.30f);
        checker.bind(0);
        ground.render(true);
        glDisable(GL_POLYGON_OFFSET_FILL);

        // ---- Model aktif ----
        TexturedMesh model = models.get(modelIndex);
        phong.setFloat("specularStrength", 0.80f);
        brick.bind(0);
        model.render(settings.isSmoothShading());

        // ---- Overlay wireframe (segitiga mesh) ----
        if (settings.isWireframe()) {
            phong.setBool("unlit", true);
            phong.setVec3("unlitColor", 0.05f, 0.05f, 0.08f);
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glEnable(GL_POLYGON_OFFSET_LINE);
            glPolygonOffset(-1.0f, -1.0f);
            glLineWidth(1.0f);
            model.render(settings.isSmoothShading());
            glDisable(GL_POLYGON_OFFSET_LINE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }

        ShaderProgram.unuse();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        // ---- Fixed-function: grid, sumbu, penanda cahaya (matriks GL masih aktif) ----
        if (showGrid) {
            drawGrid(8.0f, 0.5f, MeshFactory.GROUND_Y);
            drawAxes(2.4f);
        }
        drawLightMarker();
    }

    private Matrix4f readMatrix(int which) {
        glGetFloatv(which, matBuf);
        return new Matrix4f().set(matBuf);
    }

    /** Grid lantai pada bidang y = height. */
    private void drawGrid(float half, float step, float height) {
        glLineWidth(1.0f);
        glBegin(GL_LINES);
        int n = Math.round(half / step);
        for (int i = -n; i <= n; i++) {
            float p = i * step;
            float c = (i % 4 == 0) ? 0.18f : 0.32f;
            glColor3f(c, c, c + 0.06f);
            glVertex3f(p, height, -half); glVertex3f(p, height, half);
            glVertex3f(-half, height, p); glVertex3f(half, height, p);
        }
        glEnd();
    }

    /** Sumbu dunia: X merah, Y hijau, Z biru. */
    private void drawAxes(float len) {
        glLineWidth(2.5f);
        glBegin(GL_LINES);
        glColor3f(1.0f, 0.3f, 0.3f); glVertex3f(0, 0, 0); glVertex3f(len, 0, 0);
        glColor3f(0.3f, 1.0f, 0.3f); glVertex3f(0, 0, 0); glVertex3f(0, len, 0);
        glColor3f(0.35f, 0.55f, 1.0f); glVertex3f(0, 0, 0); glVertex3f(0, 0, len);
        glEnd();
    }

    /** Titik kuning = arah datangnya cahaya (cahaya directional) + garis sinar menuju objek. */
    private void drawLightMarker() {
        Vector3f p = new Vector3f(lightDir).mul(5.0f).add(SHADOW_CENTER);
        glLineWidth(1.0f);
        glBegin(GL_LINES);
        glColor3f(0.95f, 0.85f, 0.25f);
        glVertex3f(p.x, p.y, p.z);
        glVertex3f(SHADOW_CENTER.x, SHADOW_CENTER.y, SHADOW_CENTER.z);
        glEnd();
        glPointSize(11.0f);
        glBegin(GL_POINTS);
        glColor3f(1.0f, 0.95f, 0.4f);
        glVertex3f(p.x, p.y, p.z);
        glEnd();
    }

    // =====================================================================
    //  AKSI / STATE
    // =====================================================================

    public void setFilterMode(FilterMode mode) {
        settings.setFilterMode(mode);
        applyFilterMode();
    }

    public void toggleFilterMode() {
        setFilterMode(settings.getFilterMode() == FilterMode.NEAREST ? FilterMode.BILINEAR_MIPMAP : FilterMode.NEAREST);
    }

    private void applyFilterMode() {
        brick.setFilterMode(settings.getFilterMode());
        checker.setFilterMode(settings.getFilterMode());
    }

    public void nextModel() { modelIndex = (modelIndex + 1) % models.size(); }
    public void selectModel(int i) { modelIndex = Math.floorMod(i, models.size()); }
    public TexturedMesh getModel() { return models.get(modelIndex); }

    public RenderSettings getSettings() { return settings; }
    public HSRManager getHsr() { return hsr; }
    public boolean isShowGrid() { return showGrid; }
    public void toggleGrid() { showGrid = !showGrid; }
    public void setDesaturation(float d) { this.desaturation = Math.max(0f, Math.min(1f, d)); }

    public void dispose() {
        phong.dispose();
        depthShader.dispose();
        brick.dispose();
        checker.dispose();
        shadowFbo.dispose();
        models.forEach(TexturedMesh::dispose);
        ground.dispose();
    }
}
