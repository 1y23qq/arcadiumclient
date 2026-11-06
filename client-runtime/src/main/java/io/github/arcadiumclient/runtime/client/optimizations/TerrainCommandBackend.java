// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.lwjgl.opengl.*;

public final class TerrainCommandBackend {
    private static final Set<Batch> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int program;
    static int cameraUniform, sectionsUniform, cullUniform, verticesUniform, elementsUniform;
    private static long gpuBytes;
    private static boolean tested, supported, failed;
    private static volatile String state = "Waiting for terrain";
    static {
        GpuTerrainCommands.attach(new GpuTerrainCommands.Backend() {
            public String status() { return TerrainCommandBackend.status(); }
            public long residentBytes() { return TerrainCommandBackend.residentBytes(); }
            public void reset() { TerrainCommandBackend.reset(); }
        });
    }
    private TerrainCommandBackend() {}

    public static boolean supportedPass(TerrainRenderPass pass) {
        return !pass.isSorted() && !pass.isReverseOrder()
                && pass.primitiveType().getVerticesPerPrimitive() == 4
                && pass.primitiveType().getIndexBufferElementsPerPrimitive() == 6
                && (pass.name().equals("solid") || pass.name().equals("cutout_mipped") || pass.name().equals("decal"));
    }

    public static boolean wants(TerrainRenderPass pass) {
        return GpuTerrainCommands.enabled && !GpuTerrainCommands.residentRequested() && !failed && supportedPass(pass) && available();
    }

    public static boolean accept(CachedBatch cached, TerrainRenderPass pass) {
        if (cached == null) return true;
        if (cached.getBatch() instanceof Batch batch) return !batch.closed && wants(pass);
        if (cached.getBatch() == null || cached.getBatch().isEmpty()) return true;
        return !wants(pass) || !TerrainGpuBudget.fits(TerrainCommandBuffers.BYTES);
    }

    public static CachedBatch create(RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list,
                                     CameraTransform camera, TerrainRenderPass pass, boolean faceCulling,
                                     CommandList device, BatchAssembler.TessellationProvider tessellations) {
        if (!wants(pass)) return null;
        boolean visibleGeometry = false;
        for (int index = 0; index < list.getSectionsWithGeometryCount(); index++) {
            if (storage.getSliceMask(list.getSectionsWithGeometry()[index] & 255) != 0) { visibleGeometry = true; break; }
        }
        if (!visibleGeometry) return null;
        if (!TerrainGpuBudget.fits(TerrainCommandBuffers.BYTES)) {
            state = "GPU region memory budget reached; additional regions use Argentum";
            return null;
        }
        Batch batch = null;
        try {
            batch = new Batch(region, storage, pass, faceCulling);
            batch.update(list.getSectionsWithGeometry(), list.getSectionsWithGeometryCount(), camera.intX, camera.intY, camera.intZ);
            batch.upload(device);
            GlTessellation tessellation = tessellations.getTessellation(device, region, pass);
            GpuTerrainCommands.regionsCreated++;
            state = "Resident opaque regions active";
            return new Cached(batch, tessellation);
        } catch (Throwable error) {
            if (batch != null) batch.delete();
            fail(error);
            return null;
        }
    }

    static boolean available() {
        if (!tested) {
            tested = true;
            var caps = GL.getCapabilities();
            supported = caps.OpenGL43 && (caps.OpenGL46 || caps.GL_ARB_indirect_parameters);
            if (!supported) state = "Compute and indirect-count drawing are required; using Argentum";
        }
        return supported && !failed;
    }

    static int program() {
        if (program != 0) return program;
        if (!available()) throw new IllegalStateException("GPU terrain commands are unavailable");
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        int candidate = GL20.glCreateProgram();
        try (var stream = TerrainCommandBackend.class.getResourceAsStream("/assets/arcadium/shaders/terrain_commands.comp")) {
            if (stream == null) throw new IllegalStateException("Missing terrain command shader");
            GL20.glShaderSource(shader, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate, shader); GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            cameraUniform = uniform(candidate, "uCamera"); sectionsUniform = uniform(candidate, "uSections");
            cullUniform = uniform(candidate, "uCullFaces"); verticesUniform = uniform(candidate, "uVerticesPerPrimitive");
            elementsUniform = uniform(candidate, "uElementsPerPrimitive");
            program = candidate;
            return program;
        } catch (Throwable error) {
            GL20.glDeleteProgram(candidate);
            throw new IllegalStateException("Terrain command program failed", error);
        } finally { GL20.glDeleteShader(shader); }
    }

    private static int uniform(int id, String name) {
        int location = GL20.glGetUniformLocation(id, name);
        if (location < 0) throw new IllegalStateException("Missing terrain uniform: " + name);
        return location;
    }

    private static void fail(Throwable error) {
        if (!failed) LogManager.getLogger("Arcadium/GPU Terrain").warn("GPU terrain commands disabled; using Argentum", error);
        failed = true; state = "Disabled after error; using Argentum";
    }

    public static void reset() {
        while (!LIVE.isEmpty()) LIVE.iterator().next().delete();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0; tested = supported = failed = false;
        state = "Waiting for terrain";
    }

    public static long residentBytes() { return gpuBytes; }
    public static String status() {
        return GpuTerrainCommands.enabled ? "GPU terrain commands: " + state + " | Regions: " + LIVE.size() + " | Dispatches/reuse: "
                + GpuTerrainCommands.dispatches + "/" + GpuTerrainCommands.commandsReused + " | GPU bytes: " + gpuBytes : "GPU terrain commands: Off";
    }

    private static final class Cached extends CachedBatch {
        private final Batch batch;
        Cached(Batch batch, GlTessellation tessellation) {
            super(batch, tessellation, new byte[0], 0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            this.batch = batch;
        }
        @Override public boolean isValidFor(byte[] sections, int length, int x, int y, int z) {
            if (batch.closed || !GpuTerrainCommands.enabled || failed) return false;
            batch.update(sections, length, x, y, z);
            return true;
        }
    }

    private static final class Batch extends MultiDrawBatch {
        private final RenderRegion region;
        private final SectionRenderDataStorage storage;
        private final TerrainRenderPass pass;
        private final boolean faceCulling;
        private final int[] mesh = new int[TerrainCommandPlan.SECTIONS * TerrainCommandPlan.WORDS];
        private final byte[] sections = new byte[TerrainCommandPlan.SECTIONS];
        private final int verticesPerPrimitive, elementsPerPrimitive;
        private int length, cameraX, cameraY, cameraZ;
        private TerrainCommandBuffers buffers;
        private boolean closed;

        Batch(RenderRegion region, SectionRenderDataStorage storage, TerrainRenderPass pass, boolean faceCulling) {
            super(TerrainCommandPlan.MAX_COMMANDS);
            this.region = region; this.storage = storage; this.pass = pass; this.faceCulling = faceCulling;
            var primitive = storage.getPrimitiveType();
            verticesPerPrimitive = primitive.getVerticesPerPrimitive(); elementsPerPrimitive = primitive.getIndexBufferElementsPerPrimitive();
            if (verticesPerPrimitive < 1 || elementsPerPrimitive < 1 || RenderRegion.REGION_SIZE != TerrainCommandPlan.SECTIONS)
                throw new IllegalArgumentException("Unsupported terrain primitive or region layout");
            var strategy = SectionRenderDataUnsafe.Strategy.COMPACT;
            for (int section = 0; section < TerrainCommandPlan.SECTIONS; section++) {
                int offset = section * TerrainCommandPlan.WORDS;
                mesh[offset] = region.getOriginX() + LocalSectionIndex.unpackX(section) * 16;
                mesh[offset + 1] = region.getOriginY() + LocalSectionIndex.unpackY(section) * 16;
                mesh[offset + 2] = region.getOriginZ() + LocalSectionIndex.unpackZ(section) * 16;
                int mask = storage.getSliceMask(section), sectionElements = 0;
                if (mask == 0) continue;
                long row = storage.getDataPointer(section);
                for (int face = 0; face < TerrainCommandPlan.FACES; face++) {
                    if ((mask & 1 << face) == 0) continue;
                    int first = strategy.getVertexOffset(row, face);
                    int end = strategy.getRunVertexEnd(row, face, primitive);
                    int elements = SectionRenderDataUnsafe.elementsForVertices(end - first, primitive);
                    if (first < 0 || end < first || elements < 0) throw new IllegalArgumentException("Invalid terrain mesh range");
                    mesh[offset + 4 + face] = first; mesh[offset + 12 + face] = elements;
                    sectionElements = Math.addExact(sectionElements, elements);
                }
                maxElementCount = Math.max(maxElementCount, sectionElements);
            }
            size = TerrainCommandPlan.MAX_COMMANDS;
        }

        void update(byte[] source, int count, int x, int y, int z) {
            if (count < 0 || count > sections.length || count > source.length) throw new IllegalArgumentException("Invalid visible terrain sections");
            System.arraycopy(source, 0, sections, 0, count);
            length = count; cameraX = x; cameraY = y; cameraZ = z;
        }

        private void prepare(CommandList device) {
            int key = faceCulling ? TerrainCommandPlan.cameraKey(cameraX, cameraY, cameraZ,
                    region.getOriginX(), region.getOriginY(), region.getOriginZ()) : 0;
            buffers.prepare(device, sections, length, key, cameraX, cameraY, cameraZ, faceCulling);
        }

        @Override public void upload(CommandList device) {
            if (buffers != null) return;
            if (!TerrainGpuBudget.reserve(this, TerrainCommandBuffers.BYTES)) throw new IllegalStateException("Shared terrain command budget is full");
            try {
                buffers = new TerrainCommandBuffers(device, mesh, verticesPerPrimitive, elementsPerPrimitive);
                LIVE.add(this); gpuBytes += TerrainCommandBuffers.BYTES;
                prepare(device);
            } catch (Throwable failure) {
                try { delete(); } catch (Throwable cleanup) { if(cleanup!=failure)failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        @Override public void execute(CommandList device, GlTessellation tessellation, GlPrimitiveType primitive) {
            boolean drawn = false;
            int previousParameter = GL11.glGetInteger(ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB);
            try {
                prepare(device);
                device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffers.commands());
                GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, buffers.countHandle());
                try (var draw = device.beginTessellating(tessellation)) {
                    if (GL.getCapabilities().OpenGL46) GL46.glMultiDrawElementsIndirectCount(primitive.getId(), GL11.GL_UNSIGNED_INT, 0L, 0L, TerrainCommandPlan.MAX_COMMANDS, 20);
                    else ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(primitive.getId(), GL11.GL_UNSIGNED_INT, 0L, 0L, TerrainCommandPlan.MAX_COMMANDS, 20);
                    drawn = true; GpuTerrainCommands.regionDraws++;
                }
            } catch (Throwable error) {
                fail(error);
                if (!drawn) fallback(device, tessellation, primitive);
            } finally {
                device.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
                GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, previousParameter);
            }
        }

        private void fallback(CommandList device, GlTessellation tessellation, GlPrimitiveType primitive) {
            ChunkRenderList list = new ChunkRenderList(region);
            for (int index = 0; index < length; index++) list.add(sections[index] & 255, 1);
            CameraTransform camera = new CameraTransform(cameraX, cameraY, cameraZ);
            MultiDrawBatch original = BatchAssembler.fillRegion(region, storage, list, camera, pass, faceCulling);
            if (original != null) {
                try { original.upload(device); original.execute(device, tessellation, primitive); }
                finally { original.delete(); }
            }
            GpuTerrainCommands.fallbacks++;
        }

        @Override public void delete() {
            if (closed) return;
            closed = true;
            try { if (buffers != null) { buffers.close(); buffers = null; } }
            finally { if (LIVE.remove(this)) gpuBytes -= TerrainCommandBuffers.BYTES; TerrainGpuBudget.release(this); }
        }
        @Override public void appendDrawCommand(int base, int count, long pointer) { throw new UnsupportedOperationException("Resident terrain commands are generated on the GPU"); }
        @Override public void mergeIntoLastCommand(int count) { throw new UnsupportedOperationException("Resident terrain commands are generated on the GPU"); }
    }
}
