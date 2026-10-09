#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

uniform float strength;
uniform float totalAlpha;
uniform float depthCutoff;

out vec4 fragColor;

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);
    
    // Depth-Weighting isoliert Handmodell und HUD vor der Wellenbewegung
    float depth = texture(DepthSampler, texCoord).r;
    float depthWeight = smoothstep(depthCutoff, depthCutoff + 0.2, depth);
    
    float waveX = sin(texCoord.y * 14.0) * cos(texCoord.x * 10.0);
    float waveY = cos(texCoord.x * 14.0) * sin(texCoord.y * 11.0);
    
    // strength steuert UV-Versatz direkt (max. 0.025 UV = ~27px bei 1080p)
    vec2 offset = vec2(waveX, waveY) * strength * depthWeight;

    vec3 newColor = texture(DiffuseSampler, clamp(texCoord + offset, 0.0, 1.0)).rgb;
    fragColor = vec4(mix(texel.rgb, newColor, totalAlpha), texel.a);
}