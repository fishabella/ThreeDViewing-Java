#version 330 core
// ==========================================================
// FILE: src/main/resources/shaders/phong.vert (Vertex Shader)
// Meneruskan posisi dunia, normal, UV, dan tint ke Fragment Shader.
// Seluruh perhitungan pencahayaan dilakukan PER-PIXEL di phong.frag.
// ==========================================================
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoords;
layout (location = 3) in vec4 aTint;      // rgb = warna dasar, a = bobot tekstur (0 = polos, 1 = bertekstur)

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoords;
out vec4 Tint;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    FragPos   = vec3(model * vec4(aPos, 1.0));
    Normal    = mat3(transpose(inverse(model))) * aNormal;   // Normal Matrix
    TexCoords = aTexCoords;   // diinterpolasi perspective-correct (u/w, v/w, 1/w) oleh rasterizer
    Tint      = aTint;
    gl_Position = projection * view * vec4(FragPos, 1.0);
}
