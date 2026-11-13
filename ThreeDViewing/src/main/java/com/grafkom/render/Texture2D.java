package com.grafkom.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Indikator 4 - Texture Mapping. Memuat PNG/JPG dengan STBImage dari classpath
 * (src/main/resources/textures/) dan mengunggahnya ke GPU beserta mipmap.
 *
 * Gambar dibalik vertikal saat dimuat (stbi_set_flip_vertically_on_load) karena baris pertama
 * file gambar adalah bagian ATAS, sedangkan OpenGL menganggap v = 0 di bagian bawah.
 * Tanpa pembalikan ini tekstur tampak terbalik arah V.
 */
public class Texture2D {

    /** Mode filtering tekstur. */
    public enum FilterMode {
        NEAREST("Nearest (GL_NEAREST)"),
        BILINEAR_MIPMAP("Bilinear + Mipmap (GL_LINEAR_MIPMAP_LINEAR)");

        private final String label;
        FilterMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final int textureId;
    private final String name;
    private int width, height;

    public Texture2D(String resourcePath) {
        this.name = resourcePath;
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        ByteBuffer pixels = null;
        boolean stbOwned = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), ch = stack.mallocInt(1);
            ByteBuffer file = readResource(resourcePath);
            if (file != null) {
                stbi_set_flip_vertically_on_load(true);
                pixels = stbi_load_from_memory(file, w, h, ch, 4);
                if (pixels != null) {
                    width = w.get(0);
                    height = h.get(0);
                    stbOwned = true;
                } else {
                    System.err.println("Gagal decode tekstur " + resourcePath + ": " + stbi_failure_reason());
                }
            } else {
                System.err.println("Tekstur tidak ditemukan di classpath: " + resourcePath);
            }
        }
        if (pixels == null) { // fallback agar aplikasi tetap jalan: papan catur magenta/hitam
            width = height = 64;
            pixels = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    boolean on = ((x / 8) + (y / 8)) % 2 == 0;
                    pixels.put((byte) (on ? 255 : 0)).put((byte) 0).put((byte) (on ? 255 : 0)).put((byte) 255);
                }
            }
            pixels.flip();
        }

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glGenerateMipmap(GL_TEXTURE_2D);
        if (stbOwned) stbi_image_free(pixels);

        setFilterMode(FilterMode.BILINEAR_MIPMAP);
    }

    /** Ganti mode filtering (Nearest vs Bilinear + Mipmap, memakai anisotropic bila tersedia). */
    public void setFilterMode(FilterMode mode) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        boolean aniso = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
        if (mode == FilterMode.NEAREST) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            if (aniso) glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            if (aniso) {
                float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8.0f, max));
            }
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void dispose() { glDeleteTextures(textureId); }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getName() { return name; }

    private static ByteBuffer readResource(String path) {
        try (InputStream in = Texture2D.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
            buf.put(bytes).flip();
            return buf;
        } catch (IOException e) {
            return null;
        }
    }
}
