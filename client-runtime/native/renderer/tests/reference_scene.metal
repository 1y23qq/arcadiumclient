// SPDX-License-Identifier: GPL-3.0-only
#include <metal_stdlib>
using namespace metal;

struct VertexOutput {
    float4 position [[position]];
    float2 uv;
    float4 tint;
};

vertex VertexOutput vertexMain(uint vertexId [[vertex_id]], uint actorId [[instance_id]],
                              device const float *vertices [[buffer(0)]],
                              device const float *actors [[buffer(1)]],
                              constant float4x4 &camera [[buffer(2)]]) {
    uint v = vertexId * 5, a = actorId * 20;
    float4x4 model(float4(actors[a], actors[a + 1], actors[a + 2], actors[a + 3]),
                   float4(actors[a + 4], actors[a + 5], actors[a + 6], actors[a + 7]),
                   float4(actors[a + 8], actors[a + 9], actors[a + 10], actors[a + 11]),
                   float4(actors[a + 12], actors[a + 13], actors[a + 14], actors[a + 15]));
    VertexOutput output;
    output.position = camera * model * float4(vertices[v], vertices[v + 1], vertices[v + 2], 1);
    output.position.z = (output.position.z + output.position.w) * 0.5;
    output.uv = float2(vertices[v + 3], vertices[v + 4]);
    output.tint = float4(actors[a + 16], actors[a + 17], actors[a + 18], actors[a + 19]);
    return output;
}

fragment float4 fragmentMain(VertexOutput input [[stage_in]], texture2d<float> material [[texture(0)]]) {
    constexpr sampler nearestSampler(coord::normalized, address::clamp_to_edge, filter::nearest);
    return material.sample(nearestSampler, input.uv) * input.tint;
}
