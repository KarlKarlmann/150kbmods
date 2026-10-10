#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float RadiationDose;
uniform float Time;
uniform int EmitterCount;
uniform float EmitterUVs[8];

in vec2 texCoord;
out vec4 fragColor;

float getMeters(float rawDepth) {
    float n = 0.05, f = 256.0;
    return (2.0 * n * f) / (f + n - (rawDepth * 2.0 - 1.0) * (f - n));
}

void main() {
    vec4 orig = texture(DiffuseSampler, texCoord);
    if (RadiationDose <= 0.001) { fragColor = orig; return; }

    float rawDepth = texture(DepthSampler, texCoord).r;
    if (rawDepth >= 0.99999) { fragColor = orig; return; }

    // Punktuellen CMY-Aufbrechungsdruck durch nahe Emitter berechnen
    float splitForce = 0.0;
    vec2 splitDir = vec2(0.0);

    for (int i = 0; i < EmitterCount; i++) {
        vec2 eUV = vec2(EmitterUVs[i * 2], EmitterUVs[i * 2 + 1]);
        vec2 delta = texCoord - eUV;
        float dist = length(delta);

        // Aufbrechung nimmt radial um das Emitterzentrum zu
        if (dist < 0.35) {
            float factor = pow(1.0 - (dist / 0.35), 2.0);
            splitForce += factor;
            splitDir += normalize(delta + vec2(0.0001)) * factor;
        }
    }

    if (splitForce > 0.01) {
        float offsetMag = splitForce * 0.035 * RadiationDose;
        
        // 120-Grad Winkelversatz für Cyan, Yellow und Magenta
        vec2 offC = splitDir * offsetMag;
        vec2 offY = vec2(-splitDir.y, splitDir.x) * offsetMag;
        vec2 offM = -splitDir * offsetMag;

        // Drei Samples mit CMY-Maskierung
        vec3 colC = texture(DiffuseSampler, clamp(texCoord + offC, 0.0, 1.0)).rgb * vec3(0.0, 1.0, 1.0);
        vec3 colY = texture(DiffuseSampler, clamp(texCoord + offY, 0.0, 1.0)).rgb * vec3(1.0, 1.0, 0.0);
        vec3 colM = texture(DiffuseSampler, clamp(texCoord + offM, 0.0, 1.0)).rgb * vec3(1.0, 0.0, 1.0);

        // Exakte Rekonstruktion durch Mitteln der additiven Kanäle
        vec3 cmyColor = (colC + colY + colM) * 0.5;
        fragColor = vec4(mix(orig.rgb, cmyColor, clamp(splitForce, 0.0, 1.0)), orig.a);
    } else {
        fragColor = orig;
    }
}