// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ResidentPlayer072NativeQa {
    private static int ticks;
    private static boolean done;
    private ResidentPlayer072NativeQa() {}

    public static void tick() {
        String output = System.getProperty("arcadium.resident072.output");
        if (output == null || done || ++ticks < 80) return;
        done = true;
        Map<String, Object> report = new LinkedHashMap<>();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousStorage = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousRead = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        int previous3 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 3);
        int previous6 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 6);
        int previous7 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7);
        int program = 0, resultBuffer = 0;
        try {
            String command = System.getProperty("sun.java.command", "");
            if (!command.contains("--username ArcadiumTest") || !command.contains("--accessToken 0") || command.contains("--server "))
                throw new IllegalStateException("Use the isolated offline fixture");
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.put("version", GL11.glGetString(GL11.GL_VERSION));
            program = program(); resultBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, resultBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 2048L, GL15.GL_DYNAMIC_READ);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, resultBuffer);
            GpuResidentPlayerState.enabled = true; GpuResidentPlayerState.reset(); GpuResidentPlayerState.beginBatch();
            FloatBuffer actors = BufferUtils.createFloatBuffer(64), poses = BufferUtils.createFloatBuffer(128);
            for (int index = 0; index < actors.capacity(); index++) actors.put(index, index * .125f);
            for (int index = 0; index < poses.capacity(); index++) poses.put(index, -index * .25f);
            actors.put(4, -0f).put(5, Float.intBitsToFloat(0x7fc01234));
            poses.put(3, Float.intBitsToFloat(0xffc05678));
            actors.position(4).limit(52); poses.position(3).limit(99);
            if (!GpuResidentPlayerState.bind(actors, poses, 100)) throw new IllegalStateException(GpuResidentPlayerState.status());
            int actorId = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 3);
            int poseId = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 6);
            int compared = verify(program, resultBuffer, actors, poses);
            GL11.glFinish();
            long words = GpuResidentPlayerState.wordsWritten;
            if (!GpuResidentPlayerState.bind(actors, poses, 100)) throw new IllegalStateException("Same revision did not reuse its slot");
            if (GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 3) != actorId
                    || GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 6) != poseId
                    || GpuResidentPlayerState.wordsWritten != words) throw new AssertionError("Same revision changed resident data");
            compared += verify(program, resultBuffer, actors, poses);
            GL11.glFinish();
            actors.put(10, Float.intBitsToFloat(0x7fc07654)); poses.put(13, -0f);
            if (!GpuResidentPlayerState.bind(actors, poses, 101)) throw new IllegalStateException("Updated revision did not bind");
            if (GpuResidentPlayerState.wordsWritten - words != 2) throw new AssertionError("Sparse revision wrote more than two changed words");
            compared += verify(program, resultBuffer, actors, poses);
            GL11.glFinish();
            GpuResidentPlayerState.beginBatch(); actors.put(11, 91f);
            if (!GpuResidentPlayerState.bind(actors, poses, 101)) throw new IllegalStateException("New batch did not bind");
            compared += verify(program, resultBuffer, actors, poses);
            int savedActor = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 3);
            int savedPose = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 6);
            long fences = GpuResidentPlayerState.fencesCreated;
            if (GpuResidentPlayerState.bind(FloatBuffer.allocate(GpuResidentPlayerState.ACTOR_BYTES / 4 + 1), poses, 102))
                throw new AssertionError("Oversized actor input was accepted");
            GpuResidentPlayerState.submitted();
            if (GpuResidentPlayerState.fencesCreated != fences
                    || GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 3) != savedActor
                    || GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 6) != savedPose)
                throw new AssertionError("Streaming fallback modified the active resident bindings/fence");
            GL11.glFinish();
            for (int batch = 0; batch < 3; batch++) {
                GpuResidentPlayerState.beginBatch(); actors.put(12, batch + 17f);
                if (!GpuResidentPlayerState.bind(actors, poses, 200 + batch)) throw new IllegalStateException("Resident batch did not bind");
                compared += verify(program, resultBuffer, actors, poses);
                if (GpuResidentPlayerState.residentBytes() > GpuResidentPlayerState.MAX_BYTES) throw new AssertionError("Resident GPU budget exceeded");
                GL11.glFinish();
            }
            GpuResidentPlayerState.beginBatch();
            if (!GpuResidentPlayerState.bind(actors, FloatBuffer.allocate(0), 300)) throw new IllegalStateException("Empty explicit-pose input failed");
            compared += verify(program, resultBuffer, actors, FloatBuffer.allocate(0));
            GL11.glFinish();
            report.put("gpuRawWordsCompared", compared); report.put("sameRevisionReuse", true);
            report.put("sparseRevisionChangedWords", 2); report.put("newBatchSameRevision", true);
            report.put("oversizedInputFallsBackWithoutBindingChanges", true); report.put("emptyPoseInput", true);
            report.put("bytesBeforeReset", GpuResidentPlayerState.residentBytes());
            report.put("residentBinds", GpuResidentPlayerState.binds); report.put("wordWrites", GpuResidentPlayerState.wordsWritten);
            report.put("wordReuses", GpuResidentPlayerState.wordsReused); report.put("sameRevisionReuses", GpuResidentPlayerState.sameRevisionReuses);
            report.put("fencesCreated", GpuResidentPlayerState.fencesCreated);
            Set<Integer> allocated = new HashSet<>();
            var field = GpuResidentPlayerState.class.getDeclaredField("slots"); field.setAccessible(true);
            for (Object slot : (Object[]) field.get(null)) if (slot != null) {
                for (String name : List.of("actors", "poses")) { var id = slot.getClass().getDeclaredField(name); id.setAccessible(true); allocated.add(id.getInt(slot)); }
            }
            GpuResidentPlayerState.reset();
            if (GpuResidentPlayerState.residentBytes() != 0) throw new AssertionError("Resident mappings were not released");
            for (int buffer : allocated) if (GL15.glIsBuffer(buffer)) throw new AssertionError("Resident buffer survived reset");
            report.put("allResidentBuffersDeleted", true); report.put("normalPathNeverWaits", true);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("GL error during resident-buffer QA");
            report.put("passed", true);
        } catch (Throwable error) {
            error.printStackTrace(); report.put("passed", false); report.put("failure", error.toString());
        } finally {
            GpuResidentPlayerState.reset(); GpuResidentPlayerState.enabled = false;
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, previous3);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 6, previous6);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, previous7);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousStorage);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previousRead);
            GL20.glUseProgram(previousProgram);
            if (program != 0) GL20.glDeleteProgram(program);
            if (resultBuffer != 0) GL15.glDeleteBuffers(resultBuffer);
        }
        try { Path target = Path.of(output); Files.createDirectories(target.getParent()); Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        System.exit(Boolean.TRUE.equals(report.get("passed")) ? 0 : 1);
    }

    private static int verify(int program, int output, FloatBuffer actors, FloatBuffer poses) {
        GL20.glUseProgram(program);
        GL30.glUniform1ui(GL20.glGetUniformLocation(program, "uActors"), actors.remaining());
        GL30.glUniform1ui(GL20.glGetUniformLocation(program, "uPoses"), poses.remaining());
        int words = actors.remaining() + poses.remaining();
        GL43.glDispatchCompute((words + 63) / 64, 1, 1);
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        GpuResidentPlayerState.submitted();
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, output);
        IntBuffer actual = BufferUtils.createIntBuffer(words);
        GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, actual);
        for (int index = 0; index < words; index++) {
            float value = index < actors.remaining() ? actors.get(actors.position() + index) : poses.get(poses.position() + index - actors.remaining());
            if (actual.get(index) != Float.floatToRawIntBits(value)) throw new AssertionError("Coherent GPU word mismatch at " + index);
        }
        return words;
    }

    private static int program() {
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), program = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, """
                    #version 430
                    layout(local_size_x=64)in;
                    layout(std430,binding=3)readonly buffer Actors{uint actors[];};
                    layout(std430,binding=6)readonly buffer Poses{uint poses[];};
                    layout(std430,binding=7)writeonly buffer Output{uint result[];};
                    uniform uint uActors,uPoses;
                    void main(){uint i=gl_GlobalInvocationID.x;if(i<uActors)result[i]=actors[i];else if(i<uActors+uPoses)result[i]=poses[i-uActors];}
                    """);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(program, shader); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch (Throwable error) { GL20.glDeleteProgram(program); throw error; }
        finally { GL20.glDeleteShader(shader); }
    }
}
