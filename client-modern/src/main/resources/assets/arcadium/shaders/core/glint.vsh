#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 glintUv;
out vec4 glintTint;
out float sphericalDistance;
out float cylindricalDistance;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    glintUv = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    glintTint = Color;
    sphericalDistance = fog_spherical_distance(Position);
    cylindricalDistance = fog_cylindrical_distance(Position);
}
