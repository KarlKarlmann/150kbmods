#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TargetHue;
uniform float HueTolerance;
uniform float MinSaturation;
uniform float MinValue;
uniform float MaxValue;

in vec2 texCoord;
out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * (d + 1e-10))), d / (q.x + 1e-10), q.x);
}

void main() {
    vec4 texel = texture(Sampler0, texCoord);
    if (texel.a < 0.01 || HueTolerance <= 0.0) { fragColor = vec4(0.0); return; }

    vec3 hsv = rgb2hsv(texel.rgb);

    // Verwirft farblose Pixel (Weiss/Grau) sowie dunkle Schatten und ausgebrannte Lichter vor der Hue-Berechnung
    if (hsv.y < MinSaturation || hsv.z < MinValue || hsv.z > MaxValue) discard;

    float diff = abs(hsv.x - TargetHue);
    if (diff > 0.5) diff = 1.0 - diff;

    if (diff > HueTolerance && HueTolerance < 1.0) discard;

    fragColor = texel * ColorModulator;
}