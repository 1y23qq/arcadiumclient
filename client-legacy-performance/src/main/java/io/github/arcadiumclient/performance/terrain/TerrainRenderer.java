// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

public final class TerrainRenderer {
    private static final System.Logger LOG = System.getLogger("ArcadiumTerrain");
    private static final boolean PROFILE = Boolean.getBoolean("arcadium.legacyPerformance.profileTerrain")
            || Boolean.getBoolean("arcadium.featureTest1710.benchmark");
    private static boolean enabled = !Boolean.getBoolean("arcadium.legacyPerformance.disableTerrainBatch");
    private static boolean forceNativeSubmission;
    private static boolean capabilitiesChecked;
    private static TerrainSlots<Mesh> meshes;
    private static final Map<Region, List<Block>> arenas = new HashMap<>();
    private static ByteBuffer staging = directBytes(1024 * 1024);
    private static IntBuffer stagingInts = staging.asIntBuffer();
    private static IntBuffer firsts = directBytes(2048).asIntBuffer();
    private static IntBuffer counts = directBytes(2048).asIntBuffer();
    private static long uploads, uploadedBytes, sectionsDrawn, drawCalls, drawNanos, capacityBytes;
    private static long nativeSections, nativeBatches, nativeNanos;

    private record Region(int x, int z, int layer) {}
    private record Mesh(Block block, ArenaAllocator.Range range, int count, int mode, int format,
            int u, int v, int color, int normal, int light) {}

    private static final class Block {
        private final Region region;
        private final int buffer;
        private final ArenaAllocator allocator;

        private Block(Region region, int bytes) {
            this.region = region;
            buffer = GL15.glGenBuffers();
            allocator = new ArenaAllocator(bytes, ArenaSizing.QUANTUM);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) bytes, GL15.GL_STATIC_DRAW);
            if (GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) != bytes) {
                GL15.glDeleteBuffers(buffer);
                throw new IllegalStateException("Unable to allocate terrain vertex buffer");
            }
            capacityBytes += bytes;
        }
    }

    private TerrainRenderer() {}

    public static boolean enabled() {
        if (!enabled) return false;
        if (!capabilitiesChecked) {
            capabilitiesChecked = true;
            enabled = GLContext.getCapabilities().OpenGL15;
            if (!enabled) LOG.log(System.Logger.Level.INFO, "Terrain batching requires OpenGL 1.5; using display lists.");
        }
        return enabled;
    }

    public static void configureLists(int base, int length) {
        reset();
        meshes = new TerrainSlots<>(base, length);
    }

    public static void upload(int id, int layer, int originX, int originY, int originZ, TerrainCapture capture) {
        if (!enabled() || meshes == null || capture == null) return;
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        Block block = null;
        ArenaAllocator.Range range = null;
        try {
            Mesh next;
            if (capture.vertexCount() == 0) {
                next = new Mesh(null, null, 0, capture.drawMode(), capture.format(), 0, 0, 0, 0, 0);
            } else {
                int size = capture.byteSize();
                Region region = new Region(originX & ~1023, originZ & ~1023, layer);
                List<Block> blocks = arenas.computeIfAbsent(region, key -> new ArrayList<>());
                for (Block candidate : blocks) {
                    range = candidate.allocator.allocate(size);
                    if (range != null) {
                        block = candidate;
                        break;
                    }
                }
                if (block == null) {
                    int previousCapacity = blocks.isEmpty() ? 0 : blocks.get(blocks.size() - 1).allocator.capacity();
                    int capacity = ArenaSizing.nextCapacity(size, previousCapacity);
                    block = new Block(region, capacity);
                    blocks.add(block);
                    range = block.allocator.allocate(size);
                }
                ensureStaging(size);
                stagingInts.clear();
                TerrainVertices.write(capture.vertices(), capture.vertexCount(), originX & 1023, originY,
                        originZ & 1023, stagingInts);
                staging.position(0).limit(size);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, block.buffer);
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) range.offset, staging);
                int tail = (capture.vertexCount() - 1) * TerrainVertices.INTS_PER_VERTEX;
                int[] vertices = capture.vertices();
                next = new Mesh(block, range, capture.vertexCount(), capture.drawMode(), capture.format(),
                        vertices[tail + 3], vertices[tail + 4], vertices[tail + 5], vertices[tail + 6], vertices[tail + 7]);
            }
            Mesh previous = meshes.set(id, next);
            block = null;
            range = null;
            release(previous);
            uploads++;
            uploadedBytes += capture.byteSize();
        } catch (RuntimeException | LinkageError error) {
            if (block != null && range != null) block.allocator.free(range);
            disable(error);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    public static void remove(int firstList) {
        if (meshes == null) return;
        for (int layer = 0; layer < 2; layer++) {
            Mesh previous = meshes.get(firstList + layer);
            if (previous != null) release(meshes.set(firstList + layer, null));
        }
    }

    public static void draw(IntBuffer lists) {
        long started = PROFILE ? System.nanoTime() : 0L;
        if (!enabled() || meshes == null || forceNativeSubmission || GL11.glIsEnabled(GL11.GL_LIGHTING)) {
            drawNative(lists, started);
            return;
        }
        int begin = lists.position();
        int end = lists.limit();
        for (int i = begin; i < end; i++) {
            if (meshes.get(lists.get(i)) == null) {
                drawNative(lists, started);
                return;
            }
        }
        ensureDrawCapacity(end - begin);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        int issued = 0;
        boolean fallback = false;
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            Mesh run = null;
            firsts.clear();
            counts.clear();
            for (int i = begin; i < end; i++) {
                Mesh mesh = meshes.get(lists.get(i));
                if (mesh.count == 0) continue;
                if (run != null && (run.block != mesh.block || run.format != mesh.format || run.mode != mesh.mode)) {
                    submit(run);
                    issued++;
                }
                run = mesh;
                firsts.put(mesh.range.offset / TerrainVertices.STRIDE);
                counts.put(mesh.count);
                sectionsDrawn++;
            }
            if (run != null) {
                submit(run);
                issued++;
            }
        } catch (RuntimeException | LinkageError error) {
            // Native lists remain compiled, so the next frame can recover without rebuilding.
            fallback = issued == 0;
            disable(error);
        } finally {
            GL11.glPopClientAttrib();
            GLX.clientActiveTexture(previousClientTexture);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            if (PROFILE) drawNanos += System.nanoTime() - started;
        }
        if (fallback) drawNative(lists, PROFILE ? System.nanoTime() : 0L);
    }

    private static void drawNative(IntBuffer lists, long started) {
        GL11.glCallLists(lists);
        if (PROFILE) {
            nativeSections += lists.remaining();
            nativeBatches++;
            nativeNanos += System.nanoTime() - started;
        }
    }

    private static void submit(Mesh mesh) {
        firsts.flip();
        counts.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.block.buffer);
        int format = mesh.format;
        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
        if ((format & TerrainCapture.TEXTURE) != 0) GL11.glTexCoordPointer(2, GL11.GL_FLOAT, TerrainVertices.STRIDE, 12L);
        clientState(GL11.GL_TEXTURE_COORD_ARRAY, (format & TerrainCapture.TEXTURE) != 0);
        GLX.clientActiveTexture(GLX.GL_TEXTURE1);
        if ((format & TerrainCapture.LIGHT) != 0) GL11.glTexCoordPointer(2, GL11.GL_SHORT, TerrainVertices.STRIDE, 28L);
        clientState(GL11.GL_TEXTURE_COORD_ARRAY, (format & TerrainCapture.LIGHT) != 0);
        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
        if ((format & TerrainCapture.COLOR) != 0) GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, TerrainVertices.STRIDE, 20L);
        clientState(GL11.GL_COLOR_ARRAY, (format & TerrainCapture.COLOR) != 0);
        if ((format & TerrainCapture.NORMAL) != 0) GL11.glNormalPointer(GL11.GL_BYTE, TerrainVertices.STRIDE, 24L);
        clientState(GL11.GL_NORMAL_ARRAY, (format & TerrainCapture.NORMAL) != 0);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, TerrainVertices.STRIDE, 0L);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL14.glMultiDrawArrays(mesh.mode, firsts, counts);
        restoreCurrentAttributes(mesh);
        drawCalls++;
        firsts.clear();
        counts.clear();
    }

    private static void restoreCurrentAttributes(Mesh mesh) {
        if ((mesh.format & TerrainCapture.TEXTURE) != 0) {
            GL13.glMultiTexCoord2f(GLX.GL_TEXTURE0, Float.intBitsToFloat(mesh.u), Float.intBitsToFloat(mesh.v));
        }
        if ((mesh.format & TerrainCapture.LIGHT) != 0) {
            int light = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? mesh.light : Integer.rotateLeft(mesh.light, 16);
            GL13.glMultiTexCoord2f(GLX.GL_TEXTURE1, (short) light, (short) (light >>> 16));
        }
        if ((mesh.format & TerrainCapture.COLOR) != 0) {
            int color = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? mesh.color : Integer.reverseBytes(mesh.color);
            GL11.glColor4ub((byte) color, (byte) (color >>> 8), (byte) (color >>> 16), (byte) (color >>> 24));
        }
        if ((mesh.format & TerrainCapture.NORMAL) != 0) {
            int normal = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? mesh.normal : Integer.reverseBytes(mesh.normal);
            GL11.glNormal3b((byte) normal, (byte) (normal >>> 8), (byte) (normal >>> 16));
        }
    }

    private static void clientState(int state, boolean enable) {
        if (enable) GL11.glEnableClientState(state);
        else GL11.glDisableClientState(state);
    }

    private static void ensureStaging(int bytes) {
        if (staging.capacity() >= bytes) return;
        staging = directBytes(Math.max(bytes, staging.capacity() * 2));
        stagingInts = staging.asIntBuffer();
    }

    private static void ensureDrawCapacity(int entries) {
        if (firsts.capacity() >= entries) return;
        int capacity = Math.max(entries, firsts.capacity() * 2);
        firsts = directBytes(capacity * Integer.BYTES).asIntBuffer();
        counts = directBytes(capacity * Integer.BYTES).asIntBuffer();
    }

    private static ByteBuffer directBytes(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    private static void release(Mesh mesh) {
        if (mesh == null || mesh.block == null) return;
        Block block = mesh.block;
        block.allocator.free(mesh.range);
        if (block.allocator.allocationCount() != 0) return;
        GL15.glDeleteBuffers(block.buffer);
        capacityBytes -= block.allocator.capacity();
        List<Block> blocks = arenas.get(block.region);
        blocks.remove(block);
        if (blocks.isEmpty()) arenas.remove(block.region);
    }

    private static void disable(Throwable error) {
        enabled = false;
        LOG.log(System.Logger.Level.WARNING, "Terrain batching stopped; using the existing display lists.", error);
        reset();
    }

    public static void reset() {
        for (List<Block> blocks : arenas.values()) {
            for (Block block : blocks) GL15.glDeleteBuffers(block.buffer);
        }
        arenas.clear();
        capacityBytes = 0L;
        if (meshes != null) meshes.clear();
    }

    public static long uploads() { return uploads; }
    public static long uploadedBytes() { return uploadedBytes; }
    public static long sectionsDrawn() { return sectionsDrawn; }
    public static long drawCalls() { return drawCalls; }
    public static long drawNanos() { return drawNanos; }
    public static long capacityBytes() { return capacityBytes; }
    public static long nativeSections() { return nativeSections; }
    public static long nativeBatches() { return nativeBatches; }
    public static long nativeNanos() { return nativeNanos; }
    public static int arenaRegions() { return arenas.size(); }
    public static int arenaBlocks() {
        int count = 0;
        for (List<Block> blocks : arenas.values()) count += blocks.size();
        return count;
    }
    public static long reservedBytes() {
        long bytes = 0;
        for (List<Block> blocks : arenas.values()) for (Block block : blocks) bytes += block.allocator.liveBytes();
        return bytes;
    }
    public static int initialArenaBytes() { return ArenaSizing.INITIAL_BYTES; }
    public static int maximumNormalArenaBytes() { return ArenaSizing.MAX_NORMAL_BYTES; }
}
