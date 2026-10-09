#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;

uniform float saturation;  // -1.0 = Entsättigt (S/W), 0.0 = Normal, +1.0 = Intensiviert
uniform float hueRotation; // 0.0 bis 1.0 (0 bis 360 Grad Farbkreis-Drehung)
uniform float inversion;   // 0.0 = Normal, 1.0 = HSL-Invertiert

out vec4 fragColor;

// --- HILFSFUNKTIONEN (INLINED) ---

float getBrightness(vec3 c) {
    return c.r * 0.3086 + c.g * 0.6084 + c.b * 0.0820;
}

vec3 getDesaturatedColor(vec3 c) {
    return vec3(getBrightness(c));
}

vec3 getIntensifiedColor(vec3 color) {
    float s = 2.0;
    float cR = 0.3086, cG = 0.6084, cB = 0.0820;
    float rr = (1.0 - s) * cR + s, rg = (1.0 - s) * cG, rb = (1.0 - s) * cB;
    float gr = (1.0 - s) * cR, gg = (1.0 - s) * cG + s, gb = (1.0 - s) * cB;
    float br = (1.0 - s) * cR, bg = (1.0 - s) * cG, bb = (1.0 - s) * cB + s;
    vec3 rVec = vec3(
        color.r * rr + color.g * rg + color.b * rb,
        color.r * gr + color.g * gg + color.b * gb,
        color.r * br + color.g * bg + color.b * bb
    );
    return clamp(rVec * rVec * 10.0, 0.0, 1.0);
}

vec3 getRotatedColor(vec3 color, float rot) {
    vec3 returnColor = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float colorAffected = mod(float(i) + rot * 3.0, 3.0);
        int col1 = int(floor(colorAffected));
        returnColor[col1] += color[i] * (1.0 - (colorAffected - float(col1)));
        returnColor[int(mod(float(col1 + 1), 3.0))] += color[i] * (colorAffected - float(col1));
    }
    return returnColor;
}

vec3 rgb2hsl(vec3 c) {
    float maxC = max(max(c.r, c.g), c.b);
    float minC = min(min(c.r, c.g), c.b);
    float delta = maxC - minC;
    float l = (maxC + minC) * 0.5;
    float h = 0.0, s = 0.0;

    if (delta > 1e-5) {
        s = l < 0.5 ? delta / (maxC + minC) : delta / (2.0 - maxC - minC);
        if (maxC == c.r) h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
        else if (maxC == c.g) h = (c.b - c.r) / delta + 2.0;
        else h = (c.r - c.g) / delta + 4.0;
        h /= 6.0;
    }
    return vec3(h, s, l);
}

float hue2rgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

vec3 hsl2rgb(vec3 hsl) {
    if (hsl.y == 0.0) return vec3(hsl.z);
    float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;
    float p = 2.0 * hsl.z - q;
    return vec3(
        hue2rgb(p, q, hsl.x + 1.0 / 3.0),
        hue2rgb(p, q, hsl.x),
        hue2rgb(p, q, hsl.x - 1.0 / 3.0)
    );
}

vec3 getInvertedColor(vec3 color) {
    vec3 hsl = rgb2hsl(color);
    hsl.x = fract(hsl.x + 0.5);
    hsl.z = 1.0 - hsl.z;
    return hsl2rgb(hsl);
}

// --- MAIN ---

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);
    vec3 outcolor = texel.rgb;

    if (saturation == 0.0 && hueRotation == 0.0 && inversion == 0.0) {
        fragColor = texel;
        return;
    }

    if (saturation < 0.0) {
        outcolor = mix(outcolor, getDesaturatedColor(outcolor), -saturation);
    } else if (saturation > 0.0) {
        outcolor = mix(outcolor, getIntensifiedColor(outcolor), saturation);
    }

    if (hueRotation > 0.0) {
        outcolor = mix(outcolor, getRotatedColor(outcolor, fract(hueRotation)), clamp(hueRotation, 0.0, 1.0));
    }

    if (inversion > 0.0) {
        outcolor = mix(outcolor, getInvertedColor(outcolor), clamp(inversion, 0.0, 1.0));
    }

    fragColor = vec4(outcolor, texel.a);
}