#version 330 core
// ==========================================================
// FILE: src/main/resources/shaders/phong.frag (Fragment Shader)
//  Indikator 1: Phong / Blinn-Phong per-pixel (ambient + diffuse + specular)
//  Indikator 4: sampling tekstur dengan koordinat UV
//  Indikator 5: Shadow Mapping (pass 2) + slope-scaled bias + PCF 3x3
// ==========================================================
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 Tint;

uniform vec3  lightDir;        // arah MENUJU cahaya (vektor satuan) - cahaya directional
uniform vec3  viewPos;
uniform vec3  lightColor;
uniform float specularStrength;
uniform float shininess;       // eksponen specular Phong; Blinn-Phong memakai 4x (lihat catatan)
uniform bool  useBlinnPhong;

uniform sampler2D textureSampler;
uniform bool  useTexture;

uniform sampler2D shadowMap;
uniform mat4  lightSpaceMatrix;
uniform bool  useShadows;
uniform float biasScale;       // pengali bias (dapat di-tuning saat runtime)
uniform float shadowTexelWorld;// ukuran 1 texel shadow map dalam satuan dunia

uniform bool  unlit;           // true: warna polos tanpa pencahayaan (wireframe overlay)
uniform vec3  unlitColor;
uniform float desaturation;    // untuk mode anaglyph

// Konstanta bias sesuai modul: bias = max(0.05 * (1 - N.L), 0.005), diskalakan ke kedalaman ortografik
const float MAX_BIAS = 0.0025;
const float MIN_BIAS = 0.0004;

// Mengembalikan fraksi "dalam bayangan" (0 = terang, 1 = gelap penuh)
float shadowFraction(vec3 N, vec3 L) {
    float NdotL = max(dot(N, L), 0.0);

    // Normal-offset: geser posisi sepanjang normal agar permukaan miring tidak menguji dirinya sendiri
    // (ikut diskalakan biasScale, sehingga biasScale = 0 benar-benar tanpa bias apa pun)
    vec3 offsetPos = FragPos + N * shadowTexelWorld * 1.5 * (1.0 - NdotL) * biasScale;
    vec4 lp = lightSpaceMatrix * vec4(offsetPos, 1.0);
    vec3 proj = lp.xyz / lp.w;            // perspective divide (ortografik: w = 1)
    proj = proj * 0.5 + 0.5;              // NDC [-1,1] -> [0,1]

    if (proj.z > 1.0) return 0.0;         // di luar far plane cahaya -> tidak berbayang

    // Slope-scaled bias: makin miring permukaan terhadap cahaya, makin besar bias
    float bias = max(MAX_BIAS * (1.0 - NdotL), MIN_BIAS) * biasScale;

    // PCF 3x3: rata-rata 9 sampel agar tepi bayangan halus (anti-aliasing)
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    float shadow = 0.0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float closest = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
            shadow += (proj.z - bias > closest) ? 1.0 : 0.0;   // d_current > d_shadow + bias
        }
    }
    return shadow / 9.0;
}

void main() {
    if (unlit) {
        FragColor = vec4(unlitColor, 1.0);
        return;
    }

    vec3 norm = normalize(Normal);
    if (!gl_FrontFacing) norm = -norm;    // sisi belakang (bila culling mati) dihitung sebagai permukaan dalam

    vec3 L = normalize(lightDir);
    vec3 V = normalize(viewPos - FragPos);

    // 1. Ambient
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse (Lambert)
    float diff = max(dot(norm, L), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular: Phong (R.V)^n  vs  Blinn-Phong (N.H)^n
    //    Sudut N-H kira-kira separuh sudut R-V, sehingga Blinn-Phong memakai eksponen ~4x agar
    //    lebar highlight pada pandangan tegak lurus setara; perbedaan tersisa tampak di sudut miring.
    float spec = 0.0;
    if (diff > 0.0) {
        if (useBlinnPhong) {
            vec3 halfwayDir = normalize(L + V);
            spec = pow(max(dot(norm, halfwayDir), 0.0), shininess * 4.0);
        } else {
            vec3 reflectDir = reflect(-L, norm);
            spec = pow(max(dot(V, reflectDir), 0.0), shininess);
        }
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Bayangan: di area bayangan hanya komponen ambient (= 0.2) yang tersisa
    float shadow = useShadows ? shadowFraction(norm, L) : 0.0;

    // 5. Warna dasar: tekstur (UV) atau warna polos
    vec3 baseColor = Tint.rgb;
    if (useTexture) {
        baseColor = mix(Tint.rgb, texture(textureSampler, TexCoords).rgb, Tint.a);
    }

    vec3 result = (ambient + (1.0 - shadow) * (diffuse + specular)) * baseColor;

    // Desaturasi sebagian (dipakai saat stereo anaglyph)
    float lum = dot(result, vec3(0.299, 0.587, 0.114));
    result = mix(vec3(lum), result, 1.0 - desaturation);

    FragColor = vec4(result, 1.0);
}
