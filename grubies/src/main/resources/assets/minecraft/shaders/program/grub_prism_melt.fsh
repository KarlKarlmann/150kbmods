#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;

uniform float distance;          // Versatz X
uniform float verticalDistance;  // Versatz Y (Melt)
uniform float stretch;           // Gebogene Linsen-Verzerrung
uniform float targetHue;         // Ziel-Farbe (0.0 = Rot, 0.33 = Grün, 0.66 = Blau)
uniform float hueTolerance;      // 0.0 = Alle Pixel (Classic Double Vision), > 0.0 = Nur Ziel-Farbe
uniform float totalAlpha;

out vec4 fragColor;

// RGB -> HSV Konvertierung
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * (d + 1e-10))), d / (q.x + 1e-10), q.x);
}

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);

    // Early-Exit: Wenn der Effektdruck 0 ist oder kein Versatz anliegt
    if (totalAlpha <= 0.001 || (distance == 0.0 && verticalDistance == 0.0)) {
        fragColor = texel;
        return;
    }

    // 1. Gebogene Linsen-Verzerrung (Stretch)
    float s = abs(stretch) < 0.001 ? 1.0 : stretch;
    vec2 curvedUV = (texCoord - vec2(0.5)) / vec2(s, 1.0) + vec2(0.5);

    // 2. Masken-Berechnung
    float mask = 1.0;

    // Nur wenn hueTolerance > 0 ist, wird auf bestimmte Farbtöne gefiltert.
    // Bei hueTolerance = 0.0 bleibt mask = 1.0 (wirkt auf das GESAMTE Bild).
    if (hueTolerance > 0.001) {
        vec3 hsv = rgb2hsv(texel.rgb);
        float diff = abs(hsv.x - targetHue);
        if (diff > 0.5) diff = 1.0 - diff; // Zirkulärer Farbraum-Abgleich (0.0 = 1.0)

        mask = 1.0 - smoothstep(hueTolerance * 0.5, hueTolerance, diff);
        // Verhindert, dass farblose Pixel (Stein/Erde/Weiß/Grau) mitziehen
        mask *= smoothstep(0.15, 0.35, hsv.y);
    }

    // 3. Verschiebung anwenden
    vec2 offset = vec2(distance, verticalDistance) * mask;

    // 4. Verschobenen Original-Pixel lesen
    vec3 shiftedColor = texture(DiffuseSampler, clamp(curvedUV + offset, 0.0, 1.0)).rgb;

    // 5. Finales Ergebnis mischen
    vec3 finalColor = mix(texel.rgb, shiftedColor, totalAlpha);

    fragColor = vec4(finalColor, texel.a);
}