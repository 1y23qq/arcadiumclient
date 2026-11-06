# Ordered scene passes

The optional `arc_scene_pass_abi()` contract is version 1. Existing scene ABI 1 entry points and the 32-byte presentation frame retain their contracts. A host checks all optional symbols and the pass ABI before selecting this path.

`arc_scene_pipeline_state` is 48 bytes. It defines depth test/write and comparison, culling and front winding, independent RGB/alpha blend factors and operations, and an RGBA write mask (bits 1/2/4/8). IDs are 1 through 64. Unknown flags, factors, operations and reserved bits are rejected. Native records require four-byte alignment, including sliced direct buffers. Shaders retain the existing mesh/actor/uniform buffer indices 0/1/2 and material texture index 0. Fragment uniforms also use index 2.

`arc_scene_pass` is 96 bytes. It owns a contiguous range of the unchanged 24-byte draw records. Ranges collectively own every submitted command exactly once in packet order. Each pass has a 256-byte-aligned uniform window, viewport, optional scissor, clear color/depth and normalized depth interval. Rectangles use lower-left coordinates; the Metal encoder converts their vertical origin. Uniform buffers remain bounded to 16 KiB; pipeline and pass counts are each bounded to 64.

Color and depth load actions occupy two bits each in `load_actions`. Values are load=0, clear=1, discard=2; depth starts at bit 2. Store bits are color=1 and depth=2. Clear operations cover the attachment independently of draw masks and scissor. A load requires a previously stored, defined attachment. Resize/reset invalidates that history. Final color must be defined and stored; depth readback additionally requires stored depth. A depth-testing pass cannot use discarded depth.

The first implementation uses one bounded BGRA8 linear color target and Depth32Float target shared by the ordered passes. It retains the current mesh/actor layout and RGBA8 nearest-material shader contract. Multiple targets, samplers, atlases, shader-family mappings, native Minecraft scene extraction, direct Skiko Metal integration and frame presentation require their own capabilities and validation.

Submission preflights every pass, pipeline and resource range before encoding. In-flight mutation returns `ARC_BUSY`. Reset releases the optional pipeline table. Native queries 7/8/9 report pipeline count, completed pass count and stored-attachment bits. `read_depth` is explicit diagnostic readback with a bounded temporary buffer and timeout; rendering submission performs no readback or CPU wait.

The native `metal_ordered_passes` test uses the same packed records in an actual core OpenGL reference and Metal. It compares every RGBA component (maximum difference 1) and every raw Depth32Float word. Independent changed-depth, blend, scissor, pass-order and color-mask cases must produce observable differences. These results qualify the tested pass contract; Minecraft world, Cera, Skiko, presentation and performance have separate gates.
