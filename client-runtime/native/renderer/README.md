# Resident renderer prototype

This is an original, isolated Metal backend prototype for Arcadium's persistent scene work. It does not replace the game's OpenGL renderer or claim a measured FPS improvement.

The C ABI accepts an opaque actor byte stride, static mesh/material IDs, dirty byte ranges, frame uniforms and grouped draw commands. Mesh/material data and actor buffers remain allocated across frames. A single GPU frame may be in flight; callers receive `ARC_BUSY` rather than queueing extra frames. Each scene has a 512 MiB resident budget, and uploads cannot overlap a running frame. Shader code owns actor and vertex interpretation.

`ResidentSceneBackend` is the portable Java boundary. `MetalResidentSceneBackend` binds this ABI through Java 25 FFM and enforces render-thread ownership. Native libraries remain mapped for the process lifetime so a late GPU completion handler cannot execute from an unloaded library. Closing a scene releases its GPU resources. No native library is loaded automatically.

Build and verify with JDK 25 and CMake:

```text
python3 client-runtime/native/renderer/build.py --arch arm64
python3 client-runtime/native/renderer/build.py --arch x64
```

The macOS build targets macOS 13.0 for ARM and 12.0 for Intel. Its native and Java device tests draw textured instances, check actual RGBA pixels, update only 16 actor bytes, validate nonzero base instances, resize and invalidate stale resources. A machine with no Metal device reports a skip, which is not runtime verification. Windows builds only the explicit unsupported-platform library and verifies the native ABI and portable allocation/command bounds.

The test shader uses a 20-byte position/UV vertex, an 80-byte matrix/tint actor and a 64-byte camera matrix. These are test layouts, not a required game packet format. Draw commands are six little-endian 32-bit fields: mesh ID, material ID (0 means white), first vertex, vertex count, first actor and actor count.

Pylon currently creates an SDL OpenGL window and owns its context. Compose/Skiko obtains its GL interface from that context. Full Metal presentation therefore still requires a Metal `GpuSurface` selected before window creation, main-thread SDL Metal view ownership, a shared Metal device/queue for UI composition, and ports of the required Cera/legacy GL passes. Attaching a Metal view to the current GL window is deliberately not performed by this prototype. Existing OpenGL and Intel Mac support remain available.

API references:

- [Apple Metal device](https://developer.apple.com/documentation/metal/mtlcreatesystemdefaultdevice())
- [Apple instanced draw](https://developer.apple.com/documentation/metal/mtlrendercommandencoder/drawprimitives(type:vertexstart:vertexcount:instancecount:))
- [SDL Metal view ownership](https://wiki.libsdl.org/SDL3/SDL_Metal_CreateView)
