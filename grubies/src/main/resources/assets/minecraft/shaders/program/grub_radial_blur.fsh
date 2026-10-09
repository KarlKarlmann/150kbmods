#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;

uniform float blurStrength; // Stärke des Blurs nach außen hin
uniform float focusRadius;  // Scharfer Bereich in der Mitte (0.0 = überall Blur, 0.3 = Fokusbereich)
uniform float totalAlpha;

out vec4 fragColor;

void main() {
    vec4 orig = texture(DiffuseSampler, texCoord);

    // Early exit
    if (totalAlpha <= 0.001 || blurStrength <= 0.001) {
        fragColor = orig;
        return;
    }

    vec2 center = vec2(0.5);
    vec2 toCenter = center - texCoord;
    float dist = length(toCenter);

    // Der Blur nimmt von der Mitte nach außen hin stufenlos zu
    float factor = smoothstep(focusRadius, focusRadius + 0.4, dist) * blurStrength;

    if (factor <= 0.001) {
        fragColor = orig;
        return;
    }

    vec3 color = vec3(0.0);
    float samples = 10.0;
    vec2 dir = toCenter * factor * 0.04;

    // Ziehe Radial-Samples von außen zur Mitte
    for (float i = 0.0; i < samples; i += 1.0) {
        vec2 uv = clamp(texCoord + dir * (i / samples), 0.0, 1.0);
        color += texture(DiffuseSampler, uv).rgb;
    }

    vec3 finalColor = color / samples;
    fragColor = vec4(mix(orig.rgb, finalColor, totalAlpha), orig.a);
}