#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;

uniform vec2 pixelSize;
uniform float totalAlpha;
uniform float bloomThreshold;

out vec4 fragColor;

// Isoliert die Helligkeit pro Pixel; verhindert das Einbluten unbefilterter Nachbar-Slices
vec3 sampleThreshold(vec2 uv) {
    vec3 c = texture(DiffuseSampler, clamp(uv, 0.0, 1.0)).rgb;
    float b = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return c * smoothstep(bloomThreshold, bloomThreshold + 0.2, b);
}

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);
    float offset = pixelSize.x * 2.5 * totalAlpha;

    vec3 blur = sampleThreshold(texCoord) * 0.227027;
    blur += sampleThreshold(texCoord + vec2(offset * 1.38461538, 0.0)) * 0.31621622;
    blur += sampleThreshold(texCoord - vec2(offset * 1.38461538, 0.0)) * 0.31621622;
    blur += sampleThreshold(texCoord + vec2(offset * 3.23076923, 0.0)) * 0.07027027;
    blur += sampleThreshold(texCoord - vec2(offset * 3.23076923, 0.0)) * 0.07027027;

    fragColor = vec4(blur, texel.a);
}