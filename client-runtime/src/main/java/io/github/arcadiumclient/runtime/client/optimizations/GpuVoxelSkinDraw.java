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
    static final int PHYSICAL_VERTEX_WORDS=1024*1024;
    private static final GlVertexFormat VERTEX = GlVertexFormat.builder(36)
            .addElement("aPosition", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aTexCoord", 12, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("aNormal", 20, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("aVertexColor", 32, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false).build();
    private static int program, instancesUniform;
    private static boolean failed;
    private final GpuVoxelSkinNativeAccess api;
    private final GpuVoxelSkinGeometryCache cache;
    private final GpuVoxelSkinProjectionStore projection;
    private Object physicalInstances;
    private ByteBuffer physicalReplay;
    private IntBuffer physicalReplayWords;
    private Object buffers;
    private GlMutableBuffer commands;
    private ByteBuffer upload;
    private IntBuffer uploadWords;
    private int uploadedWords;
    private long uploadedGeneration = -1;
    private final int[] oldBindings = new int[2];
    private final long[] oldStarts = new long[2], oldSizes = new long[2];

    GpuVoxelSkinDraw(GpuVoxelSkinNativeAccess api, GpuVoxelSkinGeometryCache cache) { this(api,cache,null); }
    GpuVoxelSkinDraw(GpuVoxelSkinNativeAccess api, GpuVoxelSkinGeometryCache cache,GpuVoxelSkinProjectionStore projection) { this.api = api; this.cache = cache;this.projection=projection; }
    void physicalInstances(Object instances){if(projection!=null)physicalInstances=instances;}
    void preparePhysical(CommandList device){if(commands==null){commands=device.createMutableBuffer();device.allocateStorage(commands,(long)GpuVoxelSkinPhysicalProjection.ROW_LIMIT*16,GlBufferUsage.STREAM_DRAW);}}
    boolean warmPhysicalGeometry(CommandList device)throws Throwable {
        boolean ready=buffers!=null&&uploadedWords==cache.sizeWords()&&uploadedGeneration==cache.generation();
        if(ready)return true;
        int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try{atlas(device);}finally{GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);GL20.glUseProgram(program);}
        return false;
    }

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
        if(projection!=null){drawPhysical(device,payload,count);return;}
        atlas(device);
        api.upload.invoke(buffers, device, payload);
        GlMutableBuffer instanceBuffer = (GlMutableBuffer)api.instanceBuffer.get(buffers);
        GlVertexArrayTessellation tessellation = (GlVertexArrayTessellation)api.tessellation.get(buffers);
        if(projection!=null)projection.upload(physicalInstances,count);
        boolean submitted=false;
        try {
            prepareCommands(device, instanceBuffer, count);
            device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, commands);
            tessellation.bind(device);
            try {
                if(projection!=null){indexAttribute();projection.bind(count);}
                GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, 0L, count, 16);
                submitted=true;
                GpuVoxelSkinPipeline.drawCalls++; GpuVoxelSkinPipeline.submittedInstances += count;
            }
            finally { if(projection!=null)projection.unbind();tessellation.unbind(device); }
        } catch (Throwable failure) {
            if(projection!=null&&submitted)throw failure;
            LogManager.getLogger("Arcadium/GPU Voxel Skins").warn("Voxel command generation failed; replaying native instance draws", failure);
            replay(instanceBuffer, tessellation, device, count);
            GpuVoxelSkinPipeline.cpuRecoveries++;
            GpuVoxelSkinPipeline.fail(failure);
        } finally { device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);physicalInstances=null; }
        for (int index = payload.position() + 17; index < payload.limit(); index += 19) GpuVoxelSkinPipeline.verticesDrawn += payload.get(index);
    }
    private void drawPhysical(CommandList device,IntBuffer payload,int count)throws Throwable {
        if(count>GpuVoxelSkinPhysicalProjection.ROW_LIMIT)throw new IllegalStateException("Physical voxel row budget exceeded");
        if(physicalReplay==null){physicalReplay=BufferUtils.createByteBuffer(GpuVoxelSkinPhysicalProjection.ROW_LIMIT*19*4);physicalReplayWords=physicalReplay.asIntBuffer();}
        physicalReplayWords.clear();physicalReplayWords.put(payload.duplicate());physicalReplayWords.flip();
        atlas(device);
        GlMutableBuffer instanceBuffer=(GlMutableBuffer)api.instanceBuffer.get(buffers);GlVertexArrayTessellation tessellation=(GlVertexArrayTessellation)api.tessellation.get(buffers);
        boolean attempted=false;
        try {
            api.upload.invoke(buffers,device,payload);projection.upload(physicalInstances,count);prepareCommands(device,instanceBuffer,count);
            device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER,commands);tessellation.bind(device);
            try{
                indexAttribute();projection.bind(count);attempted=true;GpuVoxelSkinPhysicalProjection.mdiAttempts++;
                GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS,0L,count,16);GpuVoxelSkinPipeline.drawCalls++;GpuVoxelSkinPipeline.submittedInstances+=count;
            }finally{projection.unbind();tessellation.unbind(device);}
        }catch(Throwable failure){
            if(attempted)throw failure;
            LogManager.getLogger("Arcadium/GPU Voxel Skins").warn("Physical voxel upload/command failure; replaying owned affine rows",failure);
            replayPhysical(device,instanceBuffer,tessellation,count);GpuVoxelSkinPipeline.cpuRecoveries++;GpuVoxelSkinPipeline.fail(failure);
        }finally{projection.unbind();device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER,null);physicalInstances=null;}
        for(int index=payload.position()+17;index<payload.limit();index+=19)GpuVoxelSkinPipeline.verticesDrawn+=payload.get(index);
    }
    private void replayPhysical(CommandList device,GlMutableBuffer instanceBuffer,GlVertexArrayTessellation tessellation,int count){
        // Private captures already contain the full affine; no unresolved pose markers are replayed.
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT|GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        device.allocateStorage(instanceBuffer,(long)GpuVoxelSkinPhysicalProjection.ROW_LIMIT*19*4,GlBufferUsage.STREAM_DRAW);
        physicalReplay.position(0).limit(count*19*4);int previous=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,instanceBuffer.handle());GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0L,physicalReplay);}
        finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,previous);}
        GpuVoxelSkinPhysicalProjection.fallbackPayloadUploads++;tessellation.bind(device);
        int enabled=GL20.glGetVertexAttribi(14,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED);IntBuffer value=BufferUtils.createIntBuffer(4);GL30.glGetVertexAttribIuiv(14,GL20.GL_CURRENT_VERTEX_ATTRIB,value);
        try{
            GL20.glDisableVertexAttribArray(14);projection.beginReplay(physicalInstances,count);
            for(int index=0;index<count;index++){
                int first=physicalReplayWords.get(index*19+16),vertices=physicalReplayWords.get(index*19+17);
                if(first<0||vertices<0||(long)(first+vertices)*GpuVoxelSkinMath.VERTEX_WORDS>cache.sizeWords())throw new IllegalStateException("Invalid physical replay geometry range");
                projection.replayMatrix(index);GL30.glVertexAttribI1ui(14,index);GpuVoxelSkinPhysicalProjection.replayAttempts++;
                GL42.glDrawArraysInstancedBaseInstance(GL11.GL_QUADS,first,vertices,1,index);
            }
            GpuVoxelSkinPipeline.submittedInstances+=count;GpuVoxelSkinPipeline.drawCalls+=count;
        }finally{
            projection.unbind();GL30.glVertexAttribI4ui(14,value.get(0),value.get(1),value.get(2),value.get(3));
            if(enabled!=0)GL20.glEnableVertexAttribArray(14);else GL20.glDisableVertexAttribArray(14);tessellation.unbind(device);
        }
    }

    private void atlas(CommandList device) throws Throwable {
        int words = cache.sizeWords();
        if (words == 0) throw new IllegalStateException("Empty voxel atlas was queued");
        if (buffers == null) {
            ensureUpload(words); uploadWords.clear(); uploadWords.put(cache.words(), 0, words);
            FloatBuffer view = upload.asFloatBuffer(); view.limit(words);
            buffers = api.buffers.invoke(view, VERTEX, api.instanceFormat);
            api.initialize.invoke(buffers, device);
            if(projection!=null){
                GlMutableBuffer vertex=(GlMutableBuffer)api.vertexBuffer.get(buffers);
                device.allocateStorage(vertex,(long)PHYSICAL_VERTEX_WORDS*4,GlBufferUsage.STATIC_DRAW);
                uploadWords.position(0).limit(words);int old=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
                try{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,vertex.handle());GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0L,uploadWords);}finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,old);}
            }
            uploadedWords = words; uploadedGeneration = cache.generation(); GpuVoxelSkinPipeline.vertexUploads++;
            return;
        }
        if (uploadedWords == words && uploadedGeneration == cache.generation()) return;
        GlMutableBuffer vertex = (GlMutableBuffer)api.vertexBuffer.get(buffers);
        boolean replaced = uploadedGeneration != cache.generation() || vertex.getSize() < (long)words * 4;
        int first = replaced ? 0 : uploadedWords;
        if(projection!=null&&words>PHYSICAL_VERTEX_WORDS)throw new IllegalStateException("Physical voxel atlas capacity exceeded");
        if (replaced&&projection==null) {
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
        if(projection==null)device.allocateStorage(commands, (long)count * 16, GlBufferUsage.STREAM_DRAW);
        else if(count>GpuVoxelSkinPhysicalProjection.ROW_LIMIT||commands.getSize()<(long)count*16)throw new IllegalStateException("Physical command capacity was not prepared");
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
            GL42.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT|(projection==null?0:GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT));
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
            if(projection!=null){indexAttribute();projection.bind(count);}
            for (int index = 0; index < count; index++) {
                int first = data.get(index * 19 + 16), vertices = data.get(index * 19 + 17);
                if (first < 0 || vertices < 0 || (long)(first + vertices) * GpuVoxelSkinMath.VERTEX_WORDS > cache.sizeWords()) throw new IllegalStateException("Invalid recovered voxel atlas range");
                GL42.glDrawArraysInstancedBaseInstance(GL11.GL_QUADS, first, vertices, 1, index);
            }
        } finally { if(projection!=null)projection.unbind();tessellation.unbind(device); }
    }

    void delete(CommandList commands) {
        try { if (buffers != null) api.delete.invoke(buffers, commands); }
        catch (Throwable error) { GpuVoxelSkinPipeline.fail(error); }
        finally {
            if(projection!=null)projection.delete(commands);
            buffers = null; uploadedWords = 0; uploadedGeneration = -1; uploadWords = null; upload = null;
            physicalReplay=null;physicalReplayWords=null;physicalInstances=null;
            if (this.commands != null) { commands.deleteBuffer(this.commands); this.commands = null; }
        }
    }
    private void indexAttribute(){
        int old=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,commands.handle());GL30.glVertexAttribIPointer(14,1,GL11.GL_UNSIGNED_INT,16,12L);GL33.glVertexAttribDivisor(14,1);GL20.glEnableVertexAttribArray(14);}
        finally{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,old);}
    }
    static void resetProgram() {
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0; failed = false;
    }
}
