#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float harmonizeHue, harmonizeAlpha, waveFrequency, wavePhase, colorSafeMode;

in vec2 texCoord;
out vec4 fragColor;

vec3 hue2rgb(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// Perspektivische Projektion verzerrt Z-Werte nahe 1.0; Re-Linearisierung stellt echte Block-Distanzen her.
float getMeters(float rawDepth) {
    float n = 0.05, f = 256.0;
    return (2.0 * n * f) / (f + n - (rawDepth * 2.0 - 1.0) * (f - n));
}

void main() {
    vec4 incolor = texture(DiffuseSampler, texCoord);
    if (harmonizeAlpha <= 0.001) { fragColor = incolor; return; }

    float rawDepth = texture(DepthSampler, texCoord).r;
    if (rawDepth >= 0.99999) { fragColor = incolor; return; }

    float meters = getMeters(rawDepth);
    float wave = pow(sin(meters * (waveFrequency * 0.1) - wavePhase) * 0.5 + 0.5, 2.0);

    vec3 targetColor = hue2rgb(fract(harmonizeHue));
    if (colorSafeMode > 0.5) targetColor *= incolor.rgb;

    fragColor = vec4(mix(incolor.rgb, targetColor, wave * harmonizeAlpha), incolor.a);
}