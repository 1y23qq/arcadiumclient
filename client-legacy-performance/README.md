# Arcadium 1.7.10 performance

This component is built for Ornithe Gen 2 and runs only on Minecraft 1.7.10. It does not change the 1.8.9 renderer or reduce rendering quality.

The legacy font renderer submits every glyph as its own immediate-mode triangle strip and binds the same atlas repeatedly. The mixin retains vanilla layout, glyph coordinates, font textures, formatting, shadows and Unicode page selection. It records the original four vertices, expands the same two triangles, and submits consecutive glyphs together. A texture change or a text decoration flushes pending glyphs in the original order. Colors remain floats, without additional quantization. Client arrays, buffer bindings and current color are restored after submission.

The Minecraft frame loop also stops calling `Thread.yield()` after presenting an uncapped frame. Frame limiting and VSync retain their original behavior.

Terrain geometry is captured after vanilla translucent sorting, then uploaded to shared region VBOs. Submission uses the original visible display-list ID sequence and region camera matrix, batching only consecutive compatible meshes. Vertex texture coordinates, colors, normals, lightmaps and primitive order are unchanged. Vanilla section translation and anti-crack scaling are folded into the uploaded positions. Opaque and translucent layers have separate storage. Repositioning, world changes, render-distance changes and renderer reloads release stale ranges. The original display lists remain compiled for immediate fallback on unsupported OpenGL or an upload failure. This does not change block visibility, particles, animation, lighting or render distance. Terrain RGBA output is not bitwise identical; runtime QA checks exact alpha and RGB bounds from native camera shifts of +/-1/8192 block, allowing one 8-bit RGB quantization level.

Each region and layer starts with a 4 MiB arena. Additional arenas grow to 8 MiB and then 16 MiB, retaining the small-scene allocation while reducing buffer switches in dense terrain. Existing buffers are not copied or reordered. A single mesh larger than the normal bound keeps its full payload in a quantum-aligned allocation. Empty arenas are released as before.

When fixed-function lighting is enabled, native lists preserve the original normal-matrix evaluation. Ordinary Minecraft terrain uses lightmaps with fixed-function lighting disabled and takes the batched path. The native fallback avoids even the one-channel rounding differences found in the deliberately lit framebuffer fixture.

Frustum checks retain the first-corner fast path, then test the support vertex instead of projecting up to seven additional corners. Calculations preserve vanilla double precision and plane-boundary behavior; unusual non-finite inputs retain the original corner decisions. Differential tests run the actual unmodified Minecraft frustum code against 100,000 randomized cases and explicit boundary, reversed-box and non-finite cases. Formatting also reuses the same lowercased text within a draw layer instead of rebuilding it for every color code.

The geometry tests verify original triangle order and raw float bits, terrain attributes and transforms, allocator fragmentation and display-list slot invalidation. The separate 1.7.10 game-branding QA fixture compares every RGBA pixel between the original and batched font paths on the same framebuffer, then checks that the batch is reached and reduces real draw submissions. Terrain pixel comparisons and whole-client frame-time comparisons remain separate runtime checks.

For comparison, `-Darcadium.legacyPerformance.disableTerrainBatch=true` retains native terrain submission. `-Darcadium.legacyPerformance.profileTerrain=true` records terrain submission nanoseconds; diagnostic getters expose actual uploads, uploaded bytes, submitted sections, draw calls and reserved GPU bytes.

Build with `gradlew.bat build`. The output is `build/libs/ArcadiumLegacyPerformance-0.4.0-1.7.10.jar`, packaged as `ArcadiumPerformance-1.7.10.jar`.

Research references:

- [Argentum](https://github.com/QuicksilverMC/Argentum): its modern rendering path targets 1.8.9 and cannot run unchanged on 1.7.10.
- [Vertex](https://github.com/maxmanolov/vertex), commit `3cdc12a0afec8a82f2d5c9e16054be4421750910`: its MIT renderer architecture guided shared-region batching. `ArenaAllocator` is adapted from Vertex with its original copyright and MIT license retained in `META-INF/licenses/Vertex-MIT.txt`. Terrain capture, submission hooks and coordinate transforms are implemented against the actual mapped 1.7.10 renderer.
- [Lomka](https://github.com/Starlevka/Lomka), commit `5d8bf834d5d3efa783ecad0c3d2fb15a5634550f`: the Ornithe source was reviewed for supported optimization areas. It is not bundled or copied into this component.

These patches do not include Minecraft class bodies or copied LGPL/GPL renderer implementations.
