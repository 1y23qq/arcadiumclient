// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class TerrainCommandBuffers implements AutoCloseable {
    static final long BYTES = (long) TerrainCommandPlan.SECTIONS * TerrainCommandPlan.WORDS * 4
            + TerrainCommandPlan.SECTIONS * 4L + TerrainCommandPlan.MAX_COMMANDS * 20L + 4;
    private GlMutableBuffer meshes, order, commands, count;
    private final ByteBuffer orderUpload = BufferUtils.createByteBuffer(TerrainCommandPlan.SECTIONS * 4);
    private final byte[] lastOrder = new byte[TerrainCommandPlan.SECTIONS];
    private final int[] savedBindings = new int[4];
    private final long[] savedStarts = new long[4], savedSizes = new long[4];
    private final int verticesPerPrimitive, elementsPerPrimitive;
    private int visibleCount = -1, cameraKey;
    private boolean initialized, closed, lastCull;

    TerrainCommandBuffers(CommandList device, int[] mesh, int verticesPerPrimitive, int elementsPerPrimitive) {
        this.verticesPerPrimitive = verticesPerPrimitive;
        this.elementsPerPrimitive = elementsPerPrimitive;
        try {
            meshes = device.createMutableBuffer(); order = device.createMutableBuffer();
            commands = device.createMutableBuffer(); count = device.createMutableBuffer();
            ByteBuffer upload = BufferUtils.createByteBuffer(mesh.length * 4);
            upload.asIntBuffer().put(mesh);
            device.uploadData(meshes, upload, GlBufferUsage.STATIC_DRAW);
            device.allocateStorage(order, TerrainCommandPlan.SECTIONS * 4L, GlBufferUsage.DYNAMIC_DRAW);
            device.allocateStorage(commands, TerrainCommandPlan.MAX_COMMANDS * 20L, GlBufferUsage.DYNAMIC_DRAW);
            device.allocateStorage(count, 4, GlBufferUsage.DYNAMIC_DRAW);
            GpuTerrainCommands.templateUploads++;
        } catch (Throwable error) { close(); throw error; }
    }

    GlMutableBuffer commands() { return commands; }
    int countHandle() { return count.handle(); }

    boolean prepare(CommandList device, byte[] sections, int length, int key, int cameraX, int cameraY, int cameraZ, boolean cull) {
        if (closed) throw new IllegalStateException("Closed terrain command buffers");
        if (length < 0 || length > TerrainCommandPlan.SECTIONS || length > sections.length) throw new IllegalArgumentException("Invalid terrain visibility list");
        boolean changed = visibleCount != length || !Arrays.equals(lastOrder, 0, length, sections, 0, length);
        if (!initialized || changed) {
            System.arraycopy(sections, 0, lastOrder, 0, length);
            visibleCount = length;
            orderUpload.clear();
            for (int index = 0; index < length; index++) orderUpload.putInt(sections[index] & 255);
            if (length != 0) {
                orderUpload.flip();
                device.uploadData(order, orderUpload, GlBufferUsage.DYNAMIC_DRAW);
                GpuTerrainCommands.visibilityUploads++;
            }
        }
        if (initialized && !changed && cameraKey == key && lastCull == cull) {
            GpuTerrainCommands.commandsReused++;
            return false;
        }
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int genericStorage = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for (int index = 0; index < savedBindings.length; index++) {
            int buffer = savedBindings[index] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, index);
            savedStarts[index] = buffer == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, index);
            savedSizes[index] = buffer == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, index);
        }
        try {
            int program = TerrainCommandBackend.program();
            GL20.glUseProgram(program);
            GL20.glUniform3i(TerrainCommandBackend.cameraUniform, cameraX, cameraY, cameraZ);
            GL30.glUniform1ui(TerrainCommandBackend.sectionsUniform, length);
            GL30.glUniform1ui(TerrainCommandBackend.cullUniform, cull ? 1 : 0);
            GL30.glUniform1ui(TerrainCommandBackend.verticesUniform, verticesPerPrimitive);
            GL30.glUniform1ui(TerrainCommandBackend.elementsUniform, elementsPerPrimitive);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, meshes.handle());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, order.handle());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, commands.handle());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, count.handle());
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            GL43.glDispatchCompute(1, 1, 1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_COMMAND_BARRIER_BIT);
            cameraKey = key; lastCull = cull; initialized = true;
            GpuTerrainCommands.dispatches++;
        } finally {
            for (int index = 0; index < savedBindings.length; index++) {
                if (savedBindings[index] == 0 || savedSizes[index] == 0) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, savedBindings[index]);
                else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, index, savedBindings[index], savedStarts[index], savedSizes[index]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, genericStorage);
            GL20.glUseProgram(previousProgram);
        }
        return true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (meshes != null) meshes.delete();
        if (order != null) order.delete();
        if (commands != null) commands.delete();
        if (count != null) count.delete();
    }
}
