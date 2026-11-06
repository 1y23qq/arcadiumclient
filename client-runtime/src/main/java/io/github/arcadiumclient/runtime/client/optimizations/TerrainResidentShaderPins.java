// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Map;

final class TerrainResidentShaderPins {
    private TerrainResidentShaderPins(){}
    static final Map<String,String> SOURCES=Map.ofEntries(
        Map.entry("argentum:clouds.frag","6ffdd39cea8481163ca7cba378c48595c0c999026265006998e492e868203973"),
        Map.entry("argentum:clouds.vert","113ec8f70a151c459b16d927b93444cad8c9e31f9cf090b577e7efe29370238c"),
        Map.entry("argentum:entity_instancing.frag","3d666a259d5dabbce045d0119b2d58f916424ffc967a0b93e5a2e50ec9e47b3d"),
        Map.entry("argentum:entity_instancing.vert","357ab22c211148962f7218b63bdaeaafb38074b565d82b25e8b55203143581df"),
        Map.entry("argentum:entity_shadow.frag","60c4dbf3751db3d46c092b9f5a4f028c00ab441ad7d1dde4f7348fa0b9142066"),
        Map.entry("argentum:entity_shadow.vert","c5b59aed4fb2c5ace4f5570d0eb5b189d8eaca4d57572023e3c9a56f9327a7c4"),
        Map.entry("argentum:weather.frag","1874f1f49e3f8a17b1e5f979c264ae9e7116904b58aeb7ced3d920bd9ef6c0e9"),
        Map.entry("argentum:weather.vert","bce587a0e0234105d38635e4943ed0dfc3fc39993fe774dc0d9f98391c617882"),
        Map.entry("sodium:blocks/block_layer_opaque.fsh","4e5a128e08aebe24f1c2f8c67c170b24d0d8872fad127187d27fa459898ed172"),
        Map.entry("sodium:blocks/block_layer_opaque.vsh","74c63d4657005b9af9d7ccd1fe66bba4e2b9283744e7daf1dfc2ac827d1f4e6e"),
        Map.entry("sodium:clouds/clouds.fsh","dee42a1f3de7ba7b4c6139f8f6ed8fff192887f03c253cf93af04cd66b9871ac"),
        Map.entry("sodium:clouds/clouds.vsh","a20073752eff8f966731a7c90552debb8c54adc71dfe770d23a7b32f0a3fed9a"),
        Map.entry("sodium:include/chunk_material.glsl","18a5dc878965244d36adcc84fe59b6d446ac97f951a46a7326d38ba1075d3ab2"),
        Map.entry("sodium:include/chunk_matrices.glsl","84a67cab782c023f46c754cd50a6dc894cc9bafe689734f7a8e7cc2c9698cca4"),
        Map.entry("sodium:include/chunk_vertex.glsl","e0ab8f9ac30cffe990989ac6e787a1d655907ee25a43a87ea9a237483c60e81f"),
        Map.entry("sodium:include/fog.glsl","e2cd5e3c6bceebc0690a4b15ab8c481ea6f4957a961c7549831f73d3b4cd5538")
    );
}
