// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class MetalSceneBindingsProbe {
    private static ByteBuffer floats(float... values) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(values); return bytes;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void pixel(ByteBuffer image, int red, int green, int blue) {
        int offset = (32 * 64 + 32) * 4;
        require(Math.abs(Byte.toUnsignedInt(image.get(offset)) - red) <= 1
                && Math.abs(Byte.toUnsignedInt(image.get(offset + 1)) - green) <= 1
                && Math.abs(Byte.toUnsignedInt(image.get(offset + 2)) - blue) <= 1
                && Byte.toUnsignedInt(image.get(offset + 3)) == 255, "Metal FFM readback differs from the submitted scene");
    }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Pass the native library and reference shader");
        var layout = new ResidentSceneBackend.Layout(2, 80, 20, 64);
        MetalResidentSceneBackend backend;
        try {
            backend = MetalResidentSceneBackend.open(Path.of(arguments[0]), layout, Files.readString(Path.of(arguments[1])), "vertexMain", "fragmentMain");
        } catch (IllegalStateException failure) {
            if (!failure.getMessage().contains("No Metal device is available")) throw failure;
            System.out.println("SKIP: This machine exposes no Metal device"); System.exit(77); return;
        }
        try (backend) {
            backend.putMesh(1, floats(-1, -1, 0, .5f, .5f, 1, -1, 0, .5f, .5f, 0, 1, 0, .5f, .5f));
            ByteBuffer material = ByteBuffer.allocateDirect(4).put(new byte[]{51, 102, (byte) 204, (byte) 255}).flip();
            backend.putMaterial(2, 1, 1, material);
            ByteBuffer actor = floats(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1);
            ByteBuffer camera = floats(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);
            ByteBuffer command = ByteBuffer.allocateDirect(24).order(ByteOrder.LITTLE_ENDIAN);
            command.putInt(1).putInt(2).putInt(0).putInt(3).putInt(1).putInt(1).flip();
            backend.writeActors(80, actor);
            require(backend.submit(64, 64, camera, command, 1), "The first frame was unexpectedly busy");
            ByteBuffer image = ByteBuffer.allocateDirect(64 * 64 * 4);
            backend.readRgba(image); pixel(image, 51, 102, 204);
            backend.writeActors(144, floats(1, .5f, 1, 1));
            require(backend.submit(64, 64, camera, command, 1), "A completed frame was unexpectedly busy");
            backend.awaitFrame(); backend.readRgba(image); pixel(image, 51, 51, 204);
            var statistics = backend.statistics();
            require(statistics.submittedFrames() == 2 && statistics.completedFrames() == 2, "Frame counters differ across the ABI");
            require(statistics.uploadedActorBytes() == 96, "A dirty update uploaded the full scene");
            var wrongThread = new AtomicReference<Throwable>();
            Thread reader = new Thread(() -> { try { backend.statistics(); } catch (Throwable failure) { wrongThread.set(failure); } });
            reader.start(); reader.join();
            require(wrongThread.get() instanceof IllegalStateException, "A worker reached the render-thread backend");
            backend.removeMaterial(2); backend.removeMesh(1); backend.reset();
            backend.putMesh(1, floats(-1, -1, 0, .5f, .5f, 1, -1, 0, .5f, .5f, 0, 1, 0, .5f, .5f));
            backend.putMaterial(2, 1, 1, material); backend.writeActors(80, actor);
            require(backend.submit(64, 64, camera, command, 1), "The close-after-submit frame was unexpectedly busy");
        }
        backend.close();
        try { backend.awaitFrame(); throw new AssertionError("A closed backend accepted a GPU operation"); }
        catch (IllegalStateException expected) {}
        System.out.println("Metal FFM passed: opaque resident actor layout, static resources, base instance, dirty update, RGBA, counters, thread ownership and lifecycle");
    }
}
