# 3D Visual Realism Suite (Java OOP + Modern OpenGL/GLSL)

Tugas Mandiri Grafika Komputer, **Minggu ke-11: Rendering Faces pada Visual Realism**.
Pengembangan dari "3D Viewing Interactive Visualizer" (Minggu ke-10), seluruh fitur Minggu 10
(kamera orbital, 7 proyeksi, stereo anaglyph) tetap berfungsi.
Dibuat dengan Java 17+, JOML 1.10.5, dan LWJGL 3.3.3 (GLFW + OpenGL + STB), shader GLSL 330 core.

## Fitur Minggu 11 dan Pemetaan ke Rubrik

| Indikator | Tombol | Implementasi |
|---|---|---|
| **0. Mesh 3D bermateri** | `H` ganti model | `MeshFactory` + `MeshBuilder` + `TexturedMesh`: Sphere, Torus, Rumah 3D, Cube. Tiap vertex punya posisi, **normal vertex**, dan **UV** |
| **1. Shading Models (20%)** | `B` | `phong.frag`: ambient + diffuse + specular **per-pixel**. Phong `(R.V)^n` dan Blinn-Phong `(N.H)^n` (halfway vector) berganti real-time |
| **2. Flat vs Smooth (20%)** | `F` | Flat: normal **face** `N = normalize((B-A)x(C-A))`, 3 vertex per segitiga. Smooth: **vertex normal averaging** `N_v = normalize(sum w_i N_face_i)` (index buffer). `V` = wireframe agar tessellasi terlihat |
| **3. Hidden Surface Removal (20%)** | `Z`, `C` | `HSRManager`: Z-Buffer (`GL_DEPTH_TEST`, `GL_LESS`) dan Back-Face Culling (`GL_CULL_FACE`, `GL_BACK`, `GL_CCW`). Z-fighting: depth 24-bit, near 0.1, `glPolygonOffset` pada lantai/outline |
| **4. Texture Mapping (20%)** | `I`, `K` | `Texture2D` memuat PNG lewat **STBImage** + mipmap. `K`: Nearest vs Bilinear + Mipmap (`GL_LINEAR_MIPMAP_LINEAR`, plus anisotropic bila tersedia). Gambar di-flip vertikal agar V tidak terbalik |
| **5. Shadow Mapping (20%)** | `P`, `8`/`9`, `0`, `-`/`=` | `ShadowMapFBO` (depth texture 2048x2048) + `depth.vert/frag` (pass 1) + pembandingan `d_current > d_shadow + bias` di `phong.frag` (pass 2). Bias: slope-scaled `max(0.0025(1-N.L), 0.0004)`, normal-offset, `glPolygonOffset`, dan PCF 3x3 |

Catatan teknis penting:
* Shading memakai **cahaya directional** (arah datang diatur lewat azimuth/elevation), sehingga shadow map memakai proyeksi **ortografik** seperti di modul.
* Blinn-Phong memakai eksponen 4x eksponen Phong (`shininess` 32 vs 128). Sudut N-H kira-kira separuh sudut R-V, jadi lebar highlight setara dan perbedaan sungguhan tampak di sudut pandang miring (terutama pada lantai).
* Pembobotan normal vertex memakai **sudut interior** segitiga (angle-weighted). Vertex seam UV di-*weld* lewat posisi sehingga tidak ada garis jahitan pada shading.
* `B` sebelumnya adalah toggle outline pada Minggu 10. Sekarang outline dipindah ke `V` (wireframe).
* Tombol `8` / `9` mengatur **seluruh** komponen bias sekaligus. `bias = 0` memunculkan **Shadow Acne**, nilai besar (sekitar x6 ke atas) memunculkan **Peter Panning**. Ini berguna untuk bagian "Pengaruh Shadow Bias" di laporan.

## Struktur Project

```
ThreeDViewing/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/grafkom/
    │   │   ├── app/        ThreeDViewing (window, loop, HUD, --capture-all), InputController (keyboard & mouse)
    │   │   ├── camera/     Camera3D            (LookAt manual + orbital)
    │   │   ├── projection/ PerspectiveProjection, ProjectionTaxonomyManager
    │   │   ├── shader/     ShaderProgram       (kompilasi/link GLSL, uniform)
    │   │   ├── model/      MeshBuilder, MeshFactory, TexturedMesh   (+ Mesh3D lama, tidak dipakai lagi)
    │   │   └── render/     SceneRenderer, HSRManager, Texture2D, ShadowMapFBO, LightSpace,
    │   │                   RenderSettings, StereoAnaglyphRenderer, HudRenderer
    │   └── resources/
    │       ├── shaders/    phong.vert, phong.frag, depth.vert, depth.frag
    │       └── textures/   brick.png, checker.png
    └── test/java/com/grafkom/
        ├── ViewingMathTest.java    matriks manual == JOML (Minggu 10)
        └── MeshGeometryTest.java   winding CCW, normal flat/smooth, UV tidak mirror/terbalik (Minggu 11)
```

## Cara Menjalankan

Syarat: JDK 17+ dan Maven (atau NetBeans / IntelliJ / Eclipse), GPU/driver dengan **OpenGL 3.3+ (profil compatibility)**.
Windows dan Linux didukung. **macOS tidak didukung**, karena program ini mencampur pipeline fixed-function (grid, HUD) dengan shader 330.

```bash
mvn clean compile exec:java          # jalankan aplikasi
mvn test                             # unit test (matriks + geometri mesh)
mvn clean package                    # target/ThreeDViewing-1.0-SNAPSHOT-all.jar
java -jar target/ThreeDViewing-1.0-SNAPSHOT-all.jar
mvn exec:java -Dexec.args="--capture-all screenshots"   # screenshot otomatis 15 gambar untuk laporan
```

`--capture-all` menyimpan: Phong vs Blinn-Phong, Flat vs Smooth (+ wireframe), HSR on/off, Texture Nearest vs Bilinear Mipmap,
dan Shadow Mapping (bias normal, bias nol, bias besar, shadow off), sesuai poin 4 format laporan.

## Kontrol

| Tombol | Fungsi |
|---|---|
| **B** | Phong ↔ Blinn-Phong |
| **F** | Flat ↔ Smooth shading |
| **Z** | Z-Buffer depth test ON/OFF |
| **C** | Back-Face Culling ON/OFF |
| **I** | Tekstur ON/OFF |
| **K** | Filtering: Nearest ↔ Bilinear + Mipmap |
| **P** | Shadow Mapping ON/OFF |
| **8** / **9** | Shadow bias turun/naik (0 = acne, besar = peter panning) |
| **0** | Animasi cahaya (shadow bergerak real-time) |
| **-** / **=** | Tinggi cahaya (elevasi) turun/naik |
| **V** | Overlay wireframe segitiga |
| **H** | Ganti model: Sphere, Torus, Rumah 3D, Cube |
| G | Grid & sumbu ON/OFF (default OFF) |
| Mouse kiri drag / WASD / panah | Orbit kamera |
| Scroll / Q, E | Zoom |
| Spasi | Auto-rotate |
| R | Reset kamera |
| `[` `]` | FOV turun/naik (Shift = Dolly Zoom) |
| N, M / `,` `.` | Near / far plane |
| 1 sampai 7 | Jenis proyeksi (ortografik, isometrik, cavalier, cabinet, perspektif 1/2/3 titik hilang) |
| X, T, J L, U O, Y | Stereo anaglyph, mode, IPD, konvergensi, tukar mata |
| F1 / F12 / Esc | Bantuan / screenshot / keluar |

Saran pengujian cepat:
1. Sphere → `B` (highlight Phong vs Blinn-Phong), `F` + `V` (faset vs halus).
2. Rumah (`H` dua kali) → `Z` mati lalu `C` mati + `V` (efek HSR).
3. Turunkan kamera ke dekat lantai (`W`/`S`), lalu `K` (aliasing lantai kotak-kotak vs mipmap).
4. Tekan `0` untuk menggerakkan cahaya, lalu `8`/`9` untuk melihat acne dan peter panning.

---

# Minggu 10: 3D Viewing (tetap tersedia)

## Taksonomi Proyeksi (tombol 1 sampai 7)

| Key | Kelompok | Jenis | Implementasi |
|---|---|---|---|
| 1 | Paralel | Ortografik (tampak depan) | Matriks ortho manual |
| 2 | Paralel → Aksonometrik | Isometrik | Ortho + kamera az 45°, el 35.264° |
| 3 | Paralel → Oblique | Cavalier (k = 1) | Ortho × matriks shear, φ = 45° |
| 4 | Paralel → Oblique | Cabinet (k = ½) | Ortho × matriks shear, φ = 45° |
| 5 | Perspektif | 1 titik hilang | Frustum, kamera sejajar sumbu Z |
| 6 | Perspektif | 2 titik hilang | Frustum, kamera diputar 40° (horizontal) |
| 7 | Perspektif | 3 titik hilang | Frustum, kamera diputar 40° dan menunduk 32° |

Pergantian mode dianimasikan dengan halus. Setelah itu kamera tetap bisa diorbit bebas.

## Pemetaan ke Rubrik Minggu 10

**1. Camera (25%)**, di `Camera3D`
* Field `eye`, `target`, dan `up` bersifat private. Getter mengembalikan salinan, jadi state kamera tidak bisa diubah dari luar.
* `calculateLookAtMatrix()` dihitung manual dari basis u, v, n, sesuai rumus di modul.
* Navigasi orbital lewat koordinat sferis dengan interpolasi eksponensial. Gerakannya halus dan tidak bergantung FPS. Elevation dibatasi ±89° supaya `Up × n` tidak pernah nol.

**2. Proyeksi Perspektif (25%)**, di `PerspectiveProjection`
* Matriks perspektif dan frustum dihitung eksplisit, tanpa `gluPerspective`.
* FOV, near, dan far bisa diatur saat runtime. Ada juga efek Dolly Zoom.
* Aspect ratio diperbarui setiap kali jendela di-resize, sehingga objek tidak gepeng.

**3. Stereo View (25%)**, di `StereoAnaglyphRenderer`
* Memakai Off-Axis Asymmetric Frustum. Kedua mata digeser ±IPD/2 sepanjang vektor u dan sumbu pandangnya paralel.
* Color masking: mata kiri hanya menulis kanal merah, mata kanan hanya kanal hijau dan biru (cyan). Depth buffer dibersihkan di antara kedua pass.
* IPD dan konvergensi dinamis. HUD menunjukkan apakah objek berada di zero, positive, atau negative parallax.
* Mode Toe-In disediakan sebagai pembanding.

**4. Taksonomi Proyeksi (25%)**, di `ProjectionTaxonomyManager`
* 7 jenis proyeksi dalam `enum ProjectionType`, dipilih dengan tombol 1 sampai 7.

**Tambahan:** `ViewingMathTest` membuktikan matriks LookAt, perspektif, frustum, ortho, dan off-axis yang dihitung manual identik dengan implementasi referensi JOML.

## Perbaikan terhadap Kode Contoh di Modul

1. **`StereoAnaglyphRenderer`:** di modul, pass mata kiri memanggil `glClear(GL_COLOR_BUFFER_BIT)` saat color mask hanya merah. Akibatnya kanal hijau dan biru frame sebelumnya tidak terhapus. Di sini color buffer dibersihkan sekali di awal dengan mask penuh.
2. Di modul, stereo hanya memakai `glTranslatef` (frustum simetris). Di sini dipakai frustum asimetris (off-axis) supaya tidak ada parallax vertikal dan sesuai kriteria "Sangat Baik".
3. **`updateOrbitalPosition`:** di modul, rumus mengasumsikan target selalu di (0,0,0). Di sini posisi target ikut dijumlahkan.
4. **`ProjectionTaxonomyManager`:** di modul, mode Oblique belum diisi dan perspektif 2/3 titik belum ada. Di sini semua mode diimplementasikan dengan matriks manual.
