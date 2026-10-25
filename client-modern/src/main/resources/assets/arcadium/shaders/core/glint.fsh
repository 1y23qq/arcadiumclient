#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:fog.glsl>
uniform sampler2D Sampler0;
in vec2 glintUv;
in vec4 glintTint;
in float sphericalDistance;
in float cylindricalDistance;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, glintUv);
    float intensity = max(sampleColor.r, max(sampleColor.g, sampleColor.b));
    vec4 tint = ColorModulator;
#ifndef ARCADIUM_ARMOR
    tint *= glintTint;
#endif
    if (sampleColor.a * tint.a < 0.1) discard;
    float visibility = 1.0 - total_fog_value(sphericalDistance, cylindricalDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    fragColor = vec4(tint.rgb * intensity * visibility * GlintAlpha, sampleColor.a * tint.a);
}
