package com.grafkom.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBEasyFont.*;

/**
 * HUD (Head-Up Display) teks 2D di atas scene memakai stb_easy_font.
 * Menampilkan status kamera, proyeksi, stereo, dan petunjuk kontrol.
 */
public class HudRenderer {

    private ByteBuffer vertexBuffer = BufferUtils.createByteBuffer(64 * 1024);

    /**
     * @param text   teks multi-baris ('\n')
     * @param x,y    posisi pojok kiri atas panel (pixel framebuffer)
     * @param scale  skala huruf
     */
    public void drawPanel(String text, float x, float y, float scale, int fbWidth, int fbHeight) {
        text = sanitize(text);
        int quadsEstimate = text.length() * 270;
        if (vertexBuffer.capacity() < quadsEstimate) vertexBuffer = BufferUtils.createByteBuffer(quadsEstimate);
        vertexBuffer.clear();

        // Simpan state enable (depth test / culling milik scene, mis. saat tombol Z / C dimatikan)
        glPushAttrib(GL_ENABLE_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // glOrtho dengan sumbu Y terbalik membalik winding quad HUD
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, fbWidth, fbHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(x, y, 0);
        glScalef(scale, scale, 1f);

        // Panel latar semi transparan
        int lines = text.split("\n", -1).length;
        float w = stb_easy_font_width(text) + 12;
        float h = lines * 10 + 8;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.02f, 0.03f, 0.06f, 0.70f);
        glBegin(GL_QUADS);
        glVertex2f(-6, -5); glVertex2f(w - 6, -5); glVertex2f(w - 6, h - 5); glVertex2f(-6, h - 5);
        glEnd();
        glDisable(GL_BLEND);

        int quads = stb_easy_font_print(0, 0, text, null, vertexBuffer);
        glColor3f(0.92f, 0.95f, 1.0f);
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, vertexBuffer);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);

        glPopAttrib();
    }

    /** Lebar teks dalam pixel (sebelum skala). */
    public static int textWidth(String text) {
        return stb_easy_font_width(sanitize(text));
    }

    /**
     * stb_easy_font hanya mengenal karakter ASCII 32..126 dan '\n'.
     * Karakter lain (mis. '\r' dari line separator Windows, atau huruf non-ASCII)
     * dapat membuat native code membaca memori di luar tabel font → JVM crash.
     */
    private static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c >= 32 && c <= 126)) sb.append(c);
            else if (c != '\r') sb.append('?');
        }
        return sb.toString();
    }
}
