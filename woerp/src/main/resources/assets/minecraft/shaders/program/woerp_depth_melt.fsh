#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

uniform float RadiationDose;
uniform float BodyDose;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * (d + 1e-10))), d / (q.x + 1e-10), q.x);
}

float getYellowWeight(vec3 col) {
    vec3 hsv = rgb2hsv(col);
    float diff = abs(hsv.x - 0.165);
    if (diff > 0.5) diff = 1.0 - diff;
    return (1.0 - smoothstep(0.02, 0.065, diff)) * smoothstep(0.38, 0.65, hsv.y) * smoothstep(0.18, 0.38, hsv.z);
}

vec3 punchYellow(vec3 col, float pulse) {
    float lum = max(col.r, col.g);
    float contrasted = clamp((lum - 0.30) * 1.7 + 0.30, 0.05, 1.0);
    float boosted = clamp(pow(contrasted, 0.55) * 1.45, 0.0, 1.0) * pulse;
    return vec3(clamp(boosted * 1.25, 0.0, 1.0), clamp(boosted * 1.15, 0.0, 0.98), 0.0);
}

void main() {
    vec4 orig = texture(DiffuseSampler, texCoord);

    // Entkoppelt die Farbsaettigung von Logarithmen: Erzeugt bei 125, 250, 375 und 500 exakt 24%, 47%, 71% und 95% Grau
    float lum = dot(orig.rgb, vec3(0.2126, 0.7152, 0.0722));
    float desat = (RadiationDose > 0.01) ? clamp((RadiationDose / 500.0) * 0.95, 0.0, 0.95) : 0.0;
    vec3 scene = mix(orig.rgb, vec3(lum), desat);

    // Transformiert die 500er-Koerperdosis in den vom Split-Algorithmus benoetigten 0.0-1.0 Offset-Bereich
    if (BodyDose > 0.01) {
        float normBody = clamp(BodyDose / 500.0, 0.0, 1.0);
        vec2 split = vec2(sin(Time * 2.0 + texCoord.y * 8.0), cos(Time * 2.0 + texCoord.x * 8.0)) * 0.015 * (normBody * 2.0);
        vec3 colC = texture(DiffuseSampler, clamp(texCoord + split, 0.0, 1.0)).rgb * vec3(0.0, 1.0, 1.0);
        vec3 colY = texture(DiffuseSampler, clamp(texCoord + vec2(-split.y, split.x), 0.0, 1.0)).rgb * vec3(1.0, 1.0, 0.0);
        vec3 colM = texture(DiffuseSampler, clamp(texCoord - split, 0.0, 1.0)).rgb * vec3(1.0, 0.0, 1.0);
        scene = mix(scene, (colC + colY + colM) * 0.5, normBody * 0.8);
    }

    vec2 maskData = texture(MaskSampler, texCoord).rg;
    float directMask = maskData.r;
    float blockR = maskData.g;
    float pulse = 0.85 + 0.35 * (sin(Time * 3.5) * 0.5 + 0.5);

    float directW = 0.0;
    vec3 directPunched = vec3(0.0);
    if (directMask > 0.005) {
        float yw = getYellowWeight(orig.rgb);
        directW = yw * directMask;
        directPunched = punchYellow(orig.rgb, pulse);
    }

    float effR = (blockR > 0.01) ? blockR : 0.10;
    float downShift = effR * 0.18;
    float wobbleX = sin(Time * 3.2 + texCoord.y * 25.0) * (effR * 0.07);
    float wobbleY = cos(Time * 4.0 + texCoord.x * 25.0) * (effR * 0.04);
    vec2 ghostUV = texCoord + vec2(wobbleX, downShift + wobbleY);

    vec2 ghostMaskData = texture(MaskSampler, ghostUV).rg;
    float ghostMask = ghostMaskData.r;

    float ghostW = 0.0;
    vec3 ghostPunched = vec3(0.0);
    if (ghostMask > 0.005) {
        vec3 ghostCol = texture(DiffuseSampler, ghostUV).rgb;
        float yw = getYellowWeight(ghostCol);
        ghostW = yw * ghostMask;
        ghostPunched = punchYellow(ghostCol, pulse * 0.85) * 0.65;
    }

    float totalMelt = max(directW, ghostW * 0.85);

    if (totalMelt > 0.005) {
        vec3 activeSlime = (directW >= ghostW) ? directPunched : ghostPunched;
        vec3 emissiveGlow = vec3(1.15, 1.05, 0.0) * pow(totalMelt, 1.4) * 0.45;
        fragColor = vec4(clamp(mix(scene, activeSlime, totalMelt * 0.9) + emissiveGlow, 0.0, 1.0), 1.0);
    } else {
        fragColor = vec4(scene, 1.0);
    }
}