// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class TerrainCommands072NativeQa {
    private static int ticks;
    private static boolean finished;
    private TerrainCommands072NativeQa() {}

    public static void tick() {
        String output = System.getProperty("arcadium.terrain072.output");
        if (output == null || finished || ++ticks < 80) return;
        finished = true;
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            String command = System.getProperty("sun.java.command", "");
            if (!command.contains("--username ArcadiumTest") || !command.contains("--accessToken 0")
                    || command.contains("--server ")) throw new IllegalStateException("Use the isolated offline fixture");
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.put("version", GL11.glGetString(GL11.GL_VERSION));
            if (!TerrainCommandBackend.available()) throw new IllegalStateException("Compute/indirect-count support is required for this test");
            GpuTerrainCommands.enabled = true;
            int previousError = GL11.glGetError();
            report.put("initialGlError", previousError);
            Random random = new Random(0x072abcdeL);
            int[] mesh = mesh(random);
            int checkedMasks = 0;
            for (int sample = 0; sample < 2000; sample++) {
                int x = random.nextInt(-200, 260), y = random.nextInt(-160, 230), z = random.nextInt(-200, 260);
                int sx = random.nextInt(8), sy = random.nextInt(4), sz = random.nextInt(8);
                int expected = BatchAssembler.getVisibleFaces(x, y, z, sx, sy, sz);
                int actual = TerrainCommandPlan.visibleFaces(x, y, z, sx * 16, sy * 16, sz * 16);
                if (expected != actual) throw new AssertionError("Native face visibility differs");
                checkedMasks++;
            }
            report.put("nativeVisibilityCases", checkedMasks);
            RenderDevice.enterManagedCode();
            int checked = 0, reuses = 0;
            int savedGeneric = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            int[] originalBindings = new int[4], testBuffers = new int[4];
            long[] originalStarts = new long[4], originalSizes = new long[4];
            for (int index = 0; index < 4; index++) {
                originalBindings[index] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, index);
                if (originalBindings[index] != 0) {
                    originalStarts[index] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, index);
                    originalSizes[index] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, index);
                }
            }
            try (var device = RenderDevice.INSTANCE.createCommandList()) {
                int alignment = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
                if (alignment < 1) throw new AssertionError("Invalid SSBO offset alignment");
                for (int index = 0; index < 4; index++) {
                    testBuffers[index] = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, testBuffers[index]);
                    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, alignment * 3L + 256L, GL15.GL_STATIC_DRAW);
                    GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, index, testBuffers[index], alignment, alignment * 2L);
                }
                for (int generation = 0; generation < 2; generation++) {
                    if (generation == 1) mesh[12 + 6] += 6;
                    try (var buffers = new TerrainCommandBuffers(device, mesh, 4, 6)) {
                        for (int sample = 0; sample < 64; sample++) {
                            byte[] order = shuffled(random);
                            int length = sample == 0 ? 0 : sample == 1 ? 256 : random.nextInt(1, 257);
                            int x = random.nextInt(-130, 260), y = random.nextInt(-130, 180), z = random.nextInt(-130, 260);
                            int key = TerrainCommandPlan.cameraKey(x, y, z, 0, 0, 0);
                            boolean cull = (sample & 1) != 0;
                            int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                            int oldStorage = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
                            int[] bindings = new int[4];
                            long[] starts = new long[4], sizes = new long[4];
                            for (int binding = 0; binding < 4; binding++) {
                                bindings[binding] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, binding);
                                starts[binding] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, binding);
                                sizes[binding] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, binding);
                                if (starts[binding] == 0 || sizes[binding] == 0) throw new AssertionError("Ranged SSBO precondition was not exercised");
                            }
                            if (!buffers.prepare(device, order, length, key, x, y, z, cull)) throw new AssertionError("Changed scene did not regenerate commands");
                            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != oldProgram
                                    || GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) != oldStorage) throw new AssertionError("Generic GL state changed");
                            for (int binding = 0; binding < 4; binding++) if (GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, binding) != bindings[binding]
                                    || GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, binding) != starts[binding]
                                    || GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, binding) != sizes[binding]) throw new AssertionError("Indexed GL binding range changed");
                            int[] expected = TerrainCommandPlan.reference(mesh, order, length, x, y, z, cull, 4, 6);
                            int[] actual = read(buffers);
                            if (!Arrays.equals(expected, actual)) throw new AssertionError("GPU command mismatch in generation " + generation + ", case " + sample + ": expected " + Arrays.toString(expected) + ", actual " + Arrays.toString(actual));
                            checked++;
                            if (buffers.prepare(device, order, length, key, x, y, z, cull)) throw new AssertionError("Unchanged scene was dispatched again");
                            reuses++;
                            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("GPU command preparation generated a GL error");
                        }
                    }
                }
                GpuTerrainCommands.reset();
            } finally {
                for (int index = 0; index < 4; index++) {
                    if (originalBindings[index] == 0 || originalSizes[index] == 0) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, originalBindings[index]);
                    else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, index, originalBindings[index], originalStarts[index], originalSizes[index]);
                    if (testBuffers[index] != 0) GL15.glDeleteBuffers(testBuffers[index]);
                }
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, savedGeneric);
                RenderDevice.exitManagedCode();
            }
            report.put("gpuCommandComparisons", checked);
            report.put("unchangedCommandReuses", reuses);
            report.put("templateGenerations", 2);
            report.put("dispatches", GpuTerrainCommands.dispatches);
            report.put("visibilityUploads", GpuTerrainCommands.visibilityUploads);
            report.put("zeroReadbacksInProduction", true);
            report.put("nonzeroSsboRangesPreserved", true);
            report.put("passed", true);
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.put("passed", false); report.put("failure", failure.toString());
        }
        try {
            Path target = Path.of(output); Files.createDirectories(target.getParent());
            Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        System.exit(Boolean.TRUE.equals(report.get("passed")) ? 0 : 1);
    }

    private static int[] read(TerrainCommandBuffers buffers) {
        int old = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try {
            IntBuffer count = BufferUtils.createIntBuffer(1);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffers.countHandle());
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0L, count);
            int size = count.get(0);
            if (size < 0 || size > TerrainCommandPlan.MAX_COMMANDS) throw new AssertionError("Invalid GPU draw count: " + size);
            IntBuffer data = BufferUtils.createIntBuffer(Math.max(1, size * 5));
            if (size != 0) {
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffers.commands().handle());
                GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0L, data);
            }
            int[] result = new int[size * 5]; data.get(result);
            return result;
        } finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, old); }
    }

    private static byte[] shuffled(Random random) {
        byte[] result = new byte[256];
        for (int index = 0; index < result.length; index++) result[index] = (byte) index;
        for (int index = result.length - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1); byte value = result[index]; result[index] = result[swap]; result[swap] = value;
        }
        return result;
    }

    private static int[] mesh(Random random) {
        int[] result = new int[256 * 20]; int vertex = 0;
        for (int section = 0; section < 256; section++) {
            int row = section * 20;
            result[row] = LocalSectionIndex.unpackX(section) * 16;
            result[row + 1] = LocalSectionIndex.unpackY(section) * 16;
            result[row + 2] = LocalSectionIndex.unpackZ(section) * 16;
            for (int face = 0; face < 7; face++) {
                int count = random.nextInt(17) * 6;
                result[row + 4 + face] = vertex; result[row + 12 + face] = count;
                vertex += count / 6 * 4;
                if (random.nextBoolean()) vertex += 4;
            }
        }
        return result;
    }
}
