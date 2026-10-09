#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D OriginalSampler;

in vec2 texCoord;

uniform vec2 pixelSize;
uniform float totalAlpha;
uniform float bloomIntensity;

out vec4 fragColor;

void main() {
    vec4 orig = texture(OriginalSampler, texCoord);
    float offset = pixelSize.y * 2.5 * totalAlpha;

    vec3 blur = texture(DiffuseSampler, texCoord).rgb * 0.227027;
    blur += texture(DiffuseSampler, clamp(texCoord + vec2(0.0, offset * 1.38461538), 0.0, 1.0)).rgb * 0.31621622;
    blur += texture(DiffuseSampler, clamp(texCoord - vec2(0.0, offset * 1.38461538), 0.0, 1.0)).rgb * 0.31621622;
    blur += texture(DiffuseSampler, clamp(texCoord + vec2(0.0, offset * 3.23076923), 0.0, 1.0)).rgb * 0.07027027;
    blur += texture(DiffuseSampler, clamp(texCoord - vec2(0.0, offset * 3.23076923), 0.0, 1.0)).rgb * 0.07027027;

    // bloomIntensity skaliert das Glühen stufenlos
    fragColor = vec4(orig.rgb + blur * totalAlpha * bloomIntensity, orig.a);
}