#version 330 core
// ==========================================================
// FILE: src/main/resources/shaders/depth.frag
// Hanya kedalaman (gl_FragCoord.z) yang disimpan ke Depth Texture - tidak ada output warna.
// ==========================================================

void main() {
    // kosong: depth ditulis otomatis oleh fixed-function depth write
}
