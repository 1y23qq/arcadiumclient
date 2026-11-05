// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class GpuVoxelSkinDraw {
    private static final GlVertexFormat VERTEX = GlVertexFormat.builder(36)
            .addElement("aPosition", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aTexCoord", 12, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("aNormal", 20, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aVertexColor", 32, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false).build();
    private static int program, instancesUniform;
    private static boolean failed;
    private final GpuVoxelSkinNativeAccess api;
    private final GpuVoxelSkinGeometryCache cache;
    private Object buffers;
    private GlMutableBuffer commands;
    private ByteBuffer upload;
    private IntBuffer uploadWords;
    private int uploadedWords;
    private long uploadedGeneration = -1;
    private final int[] oldBindings = new int[2];
    private final long[] oldStarts = new long[2], oldSizes = new long[2];

    GpuVoxelSkinDraw(GpuVoxelSkinNativeAccess api, GpuVoxelSkinGeometryCache cache) { this.api = api; this.cache = cache; }

    static boolean ready() {
        if (failed) return false;
        if (program != 0) return true;
        if (!GL.getCapabilities().OpenGL43) return false;
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), candidate = GL20.glCreateProgram();
        try (var stream = GpuVoxelSkinDraw.class.getResourceAsStream("/assets/arcadium/shaders/voxel_skin_commands.comp")) {
            if (stream == null) throw new IllegalStateException("Missing voxel command shader");
            GL20.glShaderSource(shader, new String(stream.readAllBytes(), StandardCharsets.UTF_8)); GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate, shader); GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            instancesUniform = GL20.glGetUniformLocation(candidate, "uInstances");
            if (instancesUniform < 0) throw new IllegalStateException("Missing voxel command uniform");
            program = candidate; return true;
        } catch (Throwable error) {
            GL20.glDeleteProgram(candidate); failed = true; GpuVoxelSkinPipeline.fail(error); return false;
        } finally { GL20.glDeleteShader(shader); }
    }

    void draw(CommandList device, IntBuffer payload, int count) throws Throwable {
        if (count == 0) return;
        if (count < 0 || count > 65536 || payload.remaining() != count * 19) throw new IllegalStateException("Invalid voxel instance batch");
        atlas(device);
        api.upload.invoke(buffers, device, payload);
        GlMutableBuffer instanceBuffer = (GlMutableBuffer)api.instanceBuffer.get(buffers);
        GlVertexArrayTessellation tessellation = (GlVertexArrayTessellation)api.tessellation.get(buffers);
        try {
            prepareCommands(device, instanceBuffer, count);
            device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, commands);
            tessellation.bind(device);
            try {
                GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, 0L, count, 16);
                GpuVoxelSkinPipeline.drawCalls++; GpuVoxelSkinPipeline.submittedInstances += count;
            }
            finally { tessellation.unbind(device); }
        } catch (Throwable failure) {
            LogManager.getLogger("Arcadium/GPU Voxel Skins").warn("Voxel command generation failed; replaying native instance draws", failure);
            replay(instanceBuffer, tessellation, device, count);
            GpuVoxelSkinPipeline.cpuRecoveries++;
            GpuVoxelSkinPipeline.fail(failure);
        } finally { device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null); }
        for (int index = payload.position() + 17; index < payload.limit(); index += 19) GpuVoxelSkinPipeline.verticesDrawn += payload.get(index);
    }

    private void atlas(CommandList device) throws Throwable {
        int words = cache.sizeWords();
        if (words == 0) throw new IllegalStateException("Empty voxel atlas was queued");
        if (buffers == null) {
            ensureUpload(words); uploadWords.clear(); uploadWords.put(cache.words(), 0, words);
            FloatBuffer view = upload.asFloatBuffer(); view.limit(words);
            buffers = api.buffers.invoke(view, VERTEX, api.instanceFormat);
            api.initialize.invoke(buffers, device);
            uploadedWords = words; uploadedGeneration = cache.generation(); GpuVoxelSkinPipeline.vertexUploads++;
            return;
        }
        if (uploadedWords == words && uploadedGeneration == cache.generation()) return;
        GlMutableBuffer vertex = (GlMutableBuffer)api.vertexBuffer.get(buffers);
        boolean replaced = uploadedGeneration != cache.generation() || vertex.getSize() < (long)words * 4;
        int first = replaced ? 0 : uploadedWords;
        if (replaced) {
            int capacity = Math.min(GpuVoxelSkinGeometryCache.DEFAULT_WORD_BUDGET, Math.max(words, Math.max(4096, (int)(vertex.getSize() / 2))));
            device.allocateStorage(vertex, (long)capacity * 4, GlBufferUsage.STATIC_DRAW);
        }
        ensureUpload(words); uploadWords.clear(); uploadWords.position(first); uploadWords.put(cache.words(), first, words - first);
        uploadWords.position(first).limit(words);
        int previous = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, vertex.handle());
            GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, (long)first * 4, uploadWords);
        } finally { GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, previous); }
        uploadedWords = words; uploadedGeneration = cache.generation(); GpuVoxelSkinPipeline.vertexUploads++;
    }

    private void ensureUpload(int words) {
        if (uploadWords != null && uploadWords.capacity() >= words) return;
        int capacity = Math.min(GpuVoxelSkinGeometryCache.DEFAULT_WORD_BUDGET, Math.max(words, uploadWords == null ? 4096 : uploadWords.capacity() * 2));
        upload = BufferUtils.createByteBuffer(capacity * 4); uploadWords = upload.asIntBuffer();
    }

    private void prepareCommands(CommandList device, GlMutableBuffer instances, int count) {
        if (!ready()) throw new IllegalStateException("Voxel command shader is unavailable");
        if (commands == null) commands = device.createMutableBuffer();
        device.allocateStorage(commands, (long)count * 16, GlBufferUsage.STREAM_DRAW);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), generic = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for (int binding = 0; binding < 2; binding++) {
            int buffer = oldBindings[binding] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, binding);
            oldStarts[binding] = buffer == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, binding);
            oldSizes[binding] = buffer == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, binding);
        }
        try {
            GL20.glUseProgram(program); GL30.glUniform1ui(instancesUniform, count);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, instances.handle());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, commands.handle());
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL43.glDispatchCompute((count + 63) / 64, 1, 1);
            GL42.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT);
        } finally {
            for (int binding = 0; binding < 2; binding++) {
                if (oldBindings[binding] == 0 || oldSizes[binding] == 0) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, oldBindings[binding]);
                else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, binding, oldBindings[binding], oldStarts[binding], oldSizes[binding]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, generic); GL20.glUseProgram(oldProgram);
        }
    }

    private void replay(GlMutableBuffer instanceBuffer, GlVertexArrayTessellation tessellation, CommandList device, int count) {
        int previous = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        IntBuffer data = BufferUtils.createIntBuffer(count * 19);
        try {
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, instanceBuffer.handle());
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0L, data);
        } finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previous); }
        tessellation.bind(device);
        try {
            for (int index = 0; index < count; index++) {
                int first = data.get(index * 19 + 16), vertices = data.get(index * 19 + 17);
                if (first < 0 || vertices < 0 || (long)(first + vertices) * GpuVoxelSkinMath.VERTEX_WORDS > cache.sizeWords()) throw new IllegalStateException("Invalid recovered voxel atlas range");
                GL42.glDrawArraysInstancedBaseInstance(GL11.GL_QUADS, first, vertices, 1, index);
            }
        } finally { tessellation.unbind(device); }
    }

    void delete(CommandList commands) {
        try { if (buffers != null) api.delete.invoke(buffers, commands); }
        catch (Throwable error) { GpuVoxelSkinPipeline.fail(error); }
        finally {
            buffers = null; uploadedWords = 0; uploadedGeneration = -1; uploadWords = null; upload = null;
            if (this.commands != null) { commands.deleteBuffer(this.commands); this.commands = null; }
        }
    }
    static void resetProgram() {
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0; failed = false;
    }
}
