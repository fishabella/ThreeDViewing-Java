package com.grafkom.render;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30.*;

/**
 * Indikator 5 - Framebuffer Object (FBO) berisi Depth Texture sebagai Shadow Map.
 *
 * Render Pass 1 menggambar scene dari sudut pandang cahaya ke FBO ini (hanya depth, tanpa warna);
 * Render Pass 2 membaca texture-nya di fragment shader untuk menentukan area bayangan.
 * Wrap = CLAMP_TO_BORDER dengan border depth 1.0 → area di luar frustum cahaya dianggap TIDAK berbayang.
 */
public class ShadowMapFBO {

    public static final int SHADOW_WIDTH = 2048, SHADOW_HEIGHT = 2048;

    private final int fboId;
    private final int depthTextureId;

    public ShadowMapFBO() {
        fboId = glGenFramebuffers();
        depthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_WIDTH, SHADOW_HEIGHT, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureId, 0);
        glDrawBuffer(GL_NONE);   // tidak ada color attachment
        glReadBuffer(GL_NONE);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow map FBO tidak lengkap, status = 0x" + Integer.toHexString(status));
        }
    }

    public void bindForWriting() {
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void unbind(int screenWidth, int screenHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
    }

    /** Ikat depth texture ke texture unit tertentu untuk dibaca fragment shader. */
    public void bindForReading(int unit) {
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
    }

    public int getDepthTextureID() { return depthTextureId; }

    public void dispose() {
        glDeleteFramebuffers(fboId);
        glDeleteTextures(depthTextureId);
    }
}
