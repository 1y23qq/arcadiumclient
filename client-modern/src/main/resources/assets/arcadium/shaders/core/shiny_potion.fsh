#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 pattern = texture(Sampler0, texCoord0);
    float brightness = max(pattern.r, max(pattern.g, pattern.b));
    if (pattern.a * vertexColor.a < 0.1) discard;
    fragColor = vec4(vertexColor.rgb * brightness * ColorModulator.rgb * GlintAlpha, pattern.a * vertexColor.a);
}
