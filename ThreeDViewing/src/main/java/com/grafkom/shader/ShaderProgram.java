package com.grafkom.shader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * Pembungkus program GLSL: memuat vertex + fragment shader dari classpath
 * (src/main/resources/shaders/), mengompilasi, me-link, dan menyediakan setter uniform.
 */
public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();
    private final float[] matBuf = new float[16];

    /** @param baseName nama file tanpa ekstensi, mis. "phong" → /shaders/phong.vert + /shaders/phong.frag */
    public ShaderProgram(String baseName) {
        int vs = compile(GL_VERTEX_SHADER, load("/shaders/" + baseName + ".vert"), baseName + ".vert");
        int fs = compile(GL_FRAGMENT_SHADER, load("/shaders/" + baseName + ".frag"), baseName + ".frag");
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Link shader '" + baseName + "' gagal:\n" + glGetProgramInfoLog(programId));
        }
        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static String load(String resource) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Resource shader tidak ditemukan: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca shader " + resource, e);
        }
    }

    private static int compile(int type, String source, String label) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Kompilasi shader '" + label + "' gagal:\n" + glGetShaderInfoLog(id));
        }
        return id;
    }

    public void use() { glUseProgram(programId); }
    public static void unuse() { glUseProgram(0); }
    public void dispose() { glDeleteProgram(programId); }

    private int loc(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setInt(String name, int v)         { glUniform1i(loc(name), v); }
    public void setBool(String name, boolean v)    { glUniform1i(loc(name), v ? 1 : 0); }
    public void setFloat(String name, float v)     { glUniform1f(loc(name), v); }
    public void setVec3(String name, Vector3f v)   { glUniform3f(loc(name), v.x, v.y, v.z); }
    public void setVec3(String name, float x, float y, float z) { glUniform3f(loc(name), x, y, z); }
    public void setMat4(String name, Matrix4f m)   { glUniformMatrix4fv(loc(name), false, m.get(matBuf)); }
}
