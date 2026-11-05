// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.*;

public final class GpuResidentPlayerState {
    public static volatile boolean enabled;
    public static final int SLOTS = 3, ACTOR_BYTES = 1 << 20, POSE_BYTES = 4 << 20;
    public static final long MAX_BYTES = (long) SLOTS * (ACTOR_BYTES + POSE_BYTES);
    public static long binds, sameRevisionReuses, wordsWritten, wordsReused, dirtyRanges, busyFallbacks,
            capacityFallbacks, mappingFailures, fencesCreated, slotSwitches;
    private static final Slot[] slots = new Slot[SLOTS];
    private static int active = -1, bound = -1, cursor;
    private static long batch, mappingGeneration, allocatedBytes;
    private static boolean tested, supported, failed;
    private static volatile String state = "Waiting for player data";
    private GpuResidentPlayerState() {}

    public static void beginBatch() { batch++; active = bound = -1; }

    public static boolean bind(FloatBuffer actors, FloatBuffer poses, long revision) {
        bound = -1;
        if (!enabled || failed) return false;
        if (actors == null || poses == null || actors.remaining() == 0
                || actors.remaining() > ACTOR_BYTES / 4 || poses.remaining() > POSE_BYTES / 4) {
            capacityFallbacks++; state = "Input is empty or exceeds the resident budget; using streaming";
            return false;
        }
        try {
            if (!available()) return false;
            if (active >= 0) {
                Slot current = slots[active];
                if (current.batch == batch && current.revision == revision && current.actorCount == actors.remaining() && current.poseCount == poses.remaining()) {
                    bindSlot(active); sameRevisionReuses++; return true;
                }
            }
            int selected = -1;
            if (active >= 0 && free(slots[active])) selected = active;
            else for (int offset = 0; offset < SLOTS; offset++) {
                int candidate = (cursor + offset) % SLOTS;
                if (slots[candidate] == null || free(slots[candidate])) { selected = candidate; break; }
            }
            if (selected < 0) {
                busyFallbacks++; state = "All resident slots are in flight; using streaming";
                return false;
            }
            Slot slot = slots[selected];
            if (slot == null) { slot = new Slot(); slots[selected] = slot; }
            slot.allocate();
            int actorChanges = slot.actorShadow.write(actors, slot.actorWords, slot.generation);
            int poseChanges = slot.poseShadow.write(poses, slot.poseWords, slot.generation);
            VarHandle.storeStoreFence();
            wordsWritten += (long) actorChanges + poseChanges;
            wordsReused += (long) actors.remaining() + poses.remaining() - actorChanges - poseChanges;
            dirtyRanges += slot.actorShadow.dirtyRanges() + slot.poseShadow.dirtyRanges();
            slot.batch = batch; slot.revision = revision; slot.actorCount = actors.remaining(); slot.poseCount = poses.remaining();
            if (active != selected) slotSwitches++;
            cursor = (selected + 1) % SLOTS;
            bindSlot(selected);
            return true;
        } catch (Throwable error) {
            fail(error); return false;
        }
    }

    private static void bindSlot(int index) {
        Slot slot = slots[index];
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, slot.actors);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 6, slot.poses);
        active = bound = index; binds++;
        state = "Persistent coherent player buffers active";
    }

    public static void submitted() {
        if (bound < 0 || failed) return;
        try {
            Slot slot = slots[bound];
            long fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0) throw new IllegalStateException("Player fence creation failed");
            if (slot.fence != 0) GL32.glDeleteSync(slot.fence);
            slot.fence = fence; fencesCreated++;
        } catch (Throwable error) { fail(error); }
    }

    private static boolean free(Slot slot) {
        if (slot.fence == 0) return true;
        int result = GL32.glClientWaitSync(slot.fence, 0, 0L);
        if (result == GL32.GL_TIMEOUT_EXPIRED) return false;
        if (result != GL32.GL_ALREADY_SIGNALED && result != GL32.GL_CONDITION_SATISFIED)
            throw new IllegalStateException("Player fence query failed");
        GL32.glDeleteSync(slot.fence); slot.fence = 0;
        return true;
    }

    private static boolean available() {
        if (!tested) {
            tested = true;
            var caps = GL.getCapabilities();
            supported = caps.OpenGL43 && (caps.OpenGL44 || caps.GL_ARB_buffer_storage);
            if (!supported) state = "Persistent buffer storage is unavailable; using streaming";
        }
        return supported;
    }

    private static void fail(Throwable error) {
        if (!failed) LogManager.getLogger("Arcadium/Resident Players").warn("Resident player buffers disabled; using streaming", error);
        mappingFailures++; failed = true; bound = -1;
        state = "Disabled after an error; using streaming";
    }

    public static void reset() {
        GpuResolvedInstanceCache.reset();
        Throwable failure = null;
        for (int index = 0; index < SLOTS; index++) {
            Slot slot = slots[index];
            if (slot != null) {
                try { slot.close(); }
                catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                finally { slots[index] = null; }
            }
        }
        active = bound = -1; cursor = 0; batch++;
        tested = supported = failed = false;
        state = "Waiting for player data";
        if (failure != null) fail(failure);
    }

    public static long residentBytes() { return allocatedBytes; }
    public static String status() {
        return enabled ? "Resident player buffers: " + state + " | Written/reused words: " + wordsWritten + "/" + wordsReused
                + " | Busy fallback: " + busyFallbacks + " | GPU bytes: " + allocatedBytes + "/" + MAX_BYTES : "Resident player buffers: Off";
    }

    private static final class Slot {
        int actors, poses, actorCount, poseCount;
        long fence, generation, batch = Long.MIN_VALUE, revision;
        ByteBuffer actorMap, poseMap;
        IntBuffer actorWords, poseWords;
        boolean accounted;
        final ResidentFloatWords actorShadow = new ResidentFloatWords(), poseShadow = new ResidentFloatWords();

        void allocate() {
            if (actors != 0 && poses != 0 && actorWords != null && poseWords != null) return;
            int previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            try {
                actors = GL15.glGenBuffers(); actorMap = map(actors, ACTOR_BYTES); actorWords = actorMap.order(ByteOrder.nativeOrder()).asIntBuffer();
                poses = GL15.glGenBuffers(); poseMap = map(poses, POSE_BYTES); poseWords = poseMap.order(ByteOrder.nativeOrder()).asIntBuffer();
                generation = ++mappingGeneration; allocatedBytes += ACTOR_BYTES + POSE_BYTES; accounted = true;
            } catch (Throwable error) { close(); throw error; }
            finally { GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous); }
        }

        private static ByteBuffer map(int buffer, int bytes) {
            if (buffer == 0) throw new IllegalStateException("Resident player buffer allocation failed");
            int flags = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            if (GL.getCapabilities().OpenGL44) GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, bytes, flags);
            else ARBBufferStorage.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, bytes, flags);
            ByteBuffer mapping = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0L, bytes, flags, null);
            if (mapping == null) throw new IllegalStateException("Resident player mapping failed");
            return mapping;
        }

        void close() {
            if (actors == 0 && poses == 0 && fence == 0) return;
            int previous = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            try {
                if (previous == actors || previous == poses) previous = 0;
                try {
                    if (fence != 0) GL32.glDeleteSync(fence);
                } finally {
                    try { release(actors, actorMap); }
                    finally { release(poses, poseMap); }
                }
            } finally {
                actorShadow.invalidate(); poseShadow.invalidate(); actorWords = poseWords = null;
                actors = poses = 0; fence = 0; actorMap = poseMap = null;
                if (accounted) { allocatedBytes -= ACTOR_BYTES + POSE_BYTES; accounted = false; }
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous);
            }
        }

        private static void release(int buffer, ByteBuffer map) {
            if (buffer == 0) return;
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            try { if (map != null) GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER); }
            finally { GL15.glDeleteBuffers(buffer); }
        }
    }
}
