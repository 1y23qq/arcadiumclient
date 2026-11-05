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

Pylon creates the SDL OpenGL window and owns its context. Compose/Skiko obtains its GL interface from that context. The default surface is unchanged. The private `MetalCompositeSurface.prototype(scene, originalSurface, colorEncoding)` factory decorates that configured surface before display creation. It borrows SDL's Cocoa window on Pylon's main thread and uses the same scene's Metal device and queue. It must not call `SDL_Metal_CreateView` on the live OpenGL window, since that API converts SDL's graphics ownership.

The presentation ABI uses a reusable 32-byte current-frame descriptor for the completed default framebuffer and `GL_BACK`. Native prepare runs with the original CGL context; publish and child-view changes run on the main thread. Failed or busy presentation must hide the child layer before swapping the same frame through OpenGL. Fallback remains active until restart. Cleanup releases GL storage before removing the native child view and destroying the delegated SDL window; failed cleanup keeps the parent scene alive for a retry.

Java lifecycle tests and the actual Windows unsupported-platform FFM checks cover that boundary. The Cocoa native test renders three current frames, checks their full pixels, resizes, mirrors the GL swap interval and requires actual presented callbacks. It also checks MRT state and resource teardown. Its result is distinct from the Java/Pylon and Minecraft integrations.

An optional probe compiles the six backend sources against the exact shipped Pylon JAR, including its embedded LWJGL classes and platform native libraries:

```text
python3 client-runtime/native/renderer/build.py --arch arm64 --pylon-jar packages/arcadium_common/assets/distribution/ArcadiumPylon-1.8.9.jar --pylon-sha256 4d8e0778d6b7d4f7056e071ce8667c46eb1229764c9c58bb28687aedeae91fd1
```

The optional JAR must match both its explicit checksum and the source component catalog. On macOS the probe uses `-XstartOnFirstThread`, the real `DisplaySdl.setSurface` and `create` calls, current displayed-frame IDs, resize, retained SDL/GL ownership, sticky GL fallback and ordered teardown. Other platforms compile the optional probe without claiming a Metal runtime result. Without the two Pylon arguments, the core ABI build needs no Pylon dependency.

Transporting a composited GL frame is not a Metal world renderer. Actual Minecraft, Skiko/Compose, Cera, HUD, blur and input coverage still require application-level verification. Direct Metal world passes and Metal-backed Skiko composition remain unfinished. Existing OpenGL and Intel Mac support remain available; no backend chooser or automatic activation is added here.

API references:

- [Apple Metal device](https://developer.apple.com/documentation/metal/mtlcreatesystemdefaultdevice())
- [Apple instanced draw](https://developer.apple.com/documentation/metal/mtlrendercommandencoder/drawprimitives(type:vertexstart:vertexcount:instancecount:))
- [SDL Metal view ownership](https://wiki.libsdl.org/SDL3/SDL_Metal_CreateView)
- [SDL Cocoa window property](https://wiki.libsdl.org/SDL3/SDL_GetWindowProperties)
- [Apple OpenGL and Metal interoperability](https://developer.apple.com/documentation/metal/mixing-metal-and-opengl-rendering-in-a-view)
