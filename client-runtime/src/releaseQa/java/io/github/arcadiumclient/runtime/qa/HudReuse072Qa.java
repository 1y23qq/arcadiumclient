// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class HudReuse072Qa {
    private static final int SIZE = 256, MAX_BYTES = 2 * 1024 * 1024;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> cases = new ArrayList<>();
    private static final IdentityHashMap<VertexBuffer, Owned> buffers = new IdentityHashMap<>();
    private static final IdentityHashMap<VertexBuffer, Integer> deleting = new IdentityHashMap<>();
    private static final ByteBuffer referencePixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
    private static final ByteBuffer actualPixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
    private static final ByteBuffer uploadedBytes = BufferUtils.createByteBuffer(MAX_BYTES);
    private static boolean running, done;
    private static int frames, comparedImages, verifiedUploads, actualDraws, verifiedDeletes, maxEntries, maxCapacity, maximumNativeVertices, minimumForeground = Integer.MAX_VALUE;
    private static long nativeVertices;
    private static String asynchronousFailure;
    private static final class Owned { final int id; final boolean candidate; boolean deleted; Owned(int id, boolean candidate) { this.id = id; this.candidate = candidate; } }
    private HudReuse072Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.hudReuse072.output") != null; }

    public static void tick() {
        if (!enabled() || done || ++frames < 65) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;
        done = true; Throwable failure = null; Object previous = null;
        try {
            require(mc.world == null, "Use the isolated title fixture, without a world or server");
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use only the owned offline session");
            var property = BetaOptimizationsConfig.INSTANCE.getTree().getProp("retainedHudBatches");
            require(property != null, "Retained HUD option is absent"); previous = property.get(); property.setAs(true);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native GL context was not clean at fixture entry");
            running = true;
            try (Target target = new Target()) { qualify(target); }
            qualifyResourceReload(mc);
            RetainedHudBuffers.closeAll();
            for (Owned owned : buffers.values()) require(owned.deleted, "An owned native VBO survived final cleanup");
            require(asynchronousFailure == null, "Native delete observation failed");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native qualification teardown produced a GL error");
        } catch (Throwable error) { failure = error; }
        finally {
            try { RetainedHudBuffers.closeAll(); } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            running = false;
            if (previous != null) BetaOptimizationsConfig.INSTANCE.getTree().getProp("retainedHudBatches").setAs(previous);
            finish(failure); mc.stop();
        }
    }

    private static void qualify(Target target) throws Exception {
        VertexFormat a = new VertexFormat(DefaultVertexFormat.POSITION_COLOR), b = new VertexFormat(DefaultVertexFormat.POSITION_COLOR);
        require(a != b && a.getVertexSize() == 16 && a.getColorOffset() == 12, "Unexpected pinned position/color layout");
        RetainedHudBuffers cache = new RetainedHudBuffers();
        long allocations = RetainedHudBuffers.snapshotAllocations, created = RetainedHudBuffers.buffersCreated, reused = RetainedHudBuffers.bufferReuses;
        for (int i = 0; i < 24; i++) compare(target, cache, a, 4, i + 1, i == 0 || i == 8);
        require(RetainedHudBuffers.snapshotAllocations - allocations == 8 && RetainedHudBuffers.buffersCreated - created == 8
                && RetainedHudBuffers.bufferReuses - reused == 16, "Same-size native cache did not reuse its eight slots");
        cases.add(Map.of("case", "same-size", "frames", 24, "allocations", 8, "reuses", 16));
        for (int i = 0; i < 64; i++) compare(target, cache, a, (i % 4) + 1, i + 100, i == 0 || i == 63);
        require(RetainedHudBuffers.snapshotAllocations - allocations == 8 && RetainedHudBuffers.buffersCreated - created == 8
                && RetainedHudBuffers.bufferReuses - reused == 80, "Variable native payload sizes allocated after warmup");
        cases.add(Map.of("case", "variable-shrink-grow", "frames", 64, "extraAllocations", 0, "extraReuses", 64));
        long hits = RetainedHudBuffers.hits, uploads = RetainedHudBuffers.uploads;
        compare(target, cache, a, 4, 163, true);
        require(RetainedHudBuffers.hits == hits + 1 && RetainedHudBuffers.uploads == uploads, "Exact used-prefix hit reuploaded data");
        created = RetainedHudBuffers.buffersCreated; int deletes = verifiedDeletes;
        compare(target, cache, b, 4, 300, true);
        require(RetainedHudBuffers.buffersCreated == created + 1 && verifiedDeletes >= deletes + 2, "Format identity miss did not really evict/create storage");
        close(cache); cases.add(Map.of("case", "hit-and-format-miss", "passed", true));

        cache = new RetainedHudBuffers(); created = RetainedHudBuffers.buffersCreated; allocations = RetainedHudBuffers.snapshotAllocations;
        int largeQuads = (MAX_BYTES / 4) / (4 * a.getVertexSize());
        for (int i = 0; i < 8; i++) compare(target, cache, a, largeQuads, 1000 + i, i == 0);
        require(RetainedHudBuffers.buffersCreated - created == 4, "Real byte-budgeted cache exceeded four large slots");
        for (int i = 0; i < 12; i++) compare(target, cache, a, (i % 4) + 1, 2000 + i, i == 0);
        require((int) field(cache, "bytes") == MAX_BYTES && ((Collection<?>) field(cache, "entries")).size() == 4, "Retained large capacity was counted as only used bytes");
        compare(target, cache, a, largeQuads, 3000, true);
        require(RetainedHudBuffers.buffersCreated - created == 4 && RetainedHudBuffers.snapshotAllocations - allocations == 4, "Large-small-large native reuse allocated another buffer");
        cases.add(Map.of("case", "retained-capacity-budget", "maximumCapacityBytes", MAX_BYTES, "nativeLargeVerticesPerDraw", largeQuads * 4,
                "allocations", 4, "currentSmallLimitRetainsFullBudget", true));
        close(cache);

        cache = new RetainedHudBuffers();
        for (int i = 0; i < 8; i++) compare(target, cache, a, 1, 4000 + i, false);
        created = RetainedHudBuffers.buffersCreated; compare(target, cache, a, 4, 5000, true);
        require(RetainedHudBuffers.buffersCreated == created + 1, "An undersized native slot was reused");
        RetainedHudBuffers.closeAll(); require(((Collection<?>) field(cache, "entries")).isEmpty() && (int) field(cache, "bytes") == 0, "Resource close retained snapshots");
        verifyAllDeleted();
        compare(target, cache, a, 2, 6000, true); close(cache);
        cases.add(Map.of("case", "growth-and-resource-close-rebuild", "passed", true));
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native HUD qualification produced a GL error");
    }

    private static void qualifyResourceReload(Minecraft mc) throws Exception {
        RetainedHudBuffers cache = new RetainedHudBuffers(); VertexFormat format = new VertexFormat(DefaultVertexFormat.POSITION_COLOR);
        try (Target target = new Target()) { compare(target, cache, format, 4, 7000, true); }
        require(!((Collection<?>) field(cache, "entries")).isEmpty(), "Resource test did not retain a live native cache");
        mc.reloadResources();
        require(((Collection<?>) field(cache, "entries")).isEmpty() && (int) field(cache, "bytes") == 0, "Minecraft resource reload did not close all retained HUD caches");
        verifyAllDeleted();
        try (Target target = new Target()) { compare(target, cache, format, 2, 7001, true); }
        close(cache); cases.add(Map.of("case", "actual-minecraft-resource-reload", "nativeCachesClosed", true, "newNativeUploadsAndDrawsAfterReload", true));
    }

    private static void compare(Target target, RetainedHudBuffers cache, VertexFormat format, int quads, int seed, boolean save) throws Exception {
        BufferBuilder source = build(format, quads, seed), candidate = build(format, quads, seed);
        ByteBuffer expected = source.getBuffer().duplicate();
        require(source.getFormat() == format && candidate.getFormat() == format, "BufferBuilder changed format identity");
        int expectedVertices = source.getVertexCount();
        VertexBuffer reference = new VertexBuffer(format); own(reference, false);
        try { reference.upload(expected.duplicate()); target.clear(); draw(reference, format, expected, expectedVertices); target.read(referencePixels); }
        finally { reference.delete(); }
        require(buffers.get(reference).deleted, "Reference VBO deletion was not observed natively");
        IntBuffer colors = referencePixels.asIntBuffer(); int background = colors.get(0), foreground = 0;
        for (int i = 0; i < colors.remaining(); i++) if (colors.get(i) != background) foreground++;
        require(foreground > 0, "Native reference VBO did not draw visible pixels"); minimumForeground = Math.min(minimumForeground, foreground);
        target.clear(); int[] calls = {0};
        require(cache.render(candidate, (gpu, actual) -> {
            require(actual == format, "Cached format identity changed"); own(gpu, true); draw(gpu, actual, expected, expectedVertices); calls[0]++;
        }), "Native retained cache rejected supported quads");
        require(calls[0] == 1 && candidate.getVertexCount() == 0, "Native draw count or builder consumption changed");
        target.read(actualPixels);
        if (referencePixels.mismatch(actualPixels) != -1) {
            int different = 0, maximum = 0;
            for (int i = 0; i < referencePixels.limit(); i += 4) {
                boolean pixel = false;
                for (int channel = 0; channel < 4; channel++) { int error = Math.abs((referencePixels.get(i + channel) & 255) - (actualPixels.get(i + channel) & 255)); pixel |= error != 0; maximum = Math.max(maximum, error); }
                if (pixel) different++;
            }
            report.put("mismatch", Map.of("comparison", comparedImages + 1, "differentFullImagePixels", different, "maximumChannelDifference", maximum));
            save(referencePixels, "hud-failure-reference.png"); save(actualPixels, "hud-failure-retained.png");
            throw new IllegalStateException("Full offscreen image differs from native reference");
        }
        comparedImages++; bounded(cache);
        if (save) { save(referencePixels, "hud-" + comparedImages + "-reference.png"); save(actualPixels, "hud-" + comparedImages + "-retained.png"); }
        require(asynchronousFailure == null, "A real VBO deletion failed qualification");
    }

    private static BufferBuilder build(VertexFormat format, int quads, int seed) {
        BufferBuilder builder = new BufferBuilder(Math.max(256, quads * 4 * format.getIntSize())); builder.begin(GL11.GL_QUADS, format);
        for (int i = 0; i < quads; i++) {
            double x = i < 4 ? 12 + ((seed * 11 + i * 41) & 127) : SIZE * 2 + i % 16, y = i < 4 ? 10 + ((seed * 7 + i * 31) & 127) : SIZE * 2;
            int r = (seed * 17 + i * 23) & 255, g = (seed * 13 + i * 19) & 255, b = (seed * 7 + i * 37) & 255, a = 64 + ((seed + i) % 192);
            vertex(builder, x, y, r, g, b, a); vertex(builder, x + 31, y, r, g, b, a);
            vertex(builder, x + 31, y + 27, r, g, b, a); vertex(builder, x, y + 27, r, g, b, a);
        }
        builder.end(); return builder;
    }
    private static void vertex(BufferBuilder builder, double x, double y, int r, int g, int b, int a) { builder.vertex(x, y, 0).color(r, g, b, a).nextVertex(); }
    private static void own(VertexBuffer buffer, boolean candidate) {
        if (buffers.containsKey(buffer)) return;
        int id = ((HudReuse072BufferAccess) buffer).arcadium$hudBufferId(); require(id > 0, "Native VBO name was not created");
        buffers.put(buffer, new Owned(id, candidate));
    }
    private static void draw(VertexBuffer buffer, VertexFormat format, ByteBuffer expected, int vertices) {
        require(((HudReuse072BufferAccess) buffer).arcadium$hudVertexCount() == vertices, "Actual native vertex count is stale after size change");
        buffer.bind();
        try {
            require(GL15.glIsBuffer(buffers.get(buffer).id), "Actual VBO is not live");
            require(GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) == expected.remaining(), "Actual uploaded VBO byte count differs");
            uploadedBytes.clear().limit(expected.remaining()); GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, uploadedBytes);
            require(uploadedBytes.mismatch(expected) == -1, "Actual GPU vertex bytes differ from the native input"); verifiedUploads++;
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY); GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, format.getVertexSize(), 0L); GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, format.getVertexSize(), (long) format.getColorOffset());
            buffer.draw(GL11.GL_QUADS); actualDraws++; nativeVertices += vertices; maximumNativeVertices = Math.max(maximumNativeVertices, vertices);
        } finally { GL11.glDisableClientState(GL11.GL_COLOR_ARRAY); GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY); buffer.unbind(); }
    }
    public static void deleting(VertexBuffer buffer) {
        if (!running || !buffers.containsKey(buffer)) return;
        int id = ((HudReuse072BufferAccess) buffer).arcadium$hudBufferId(); if (id > 0) deleting.put(buffer, id);
    }
    public static void deleted(VertexBuffer buffer) {
        if (!running) return; Integer id = deleting.remove(buffer); if (id == null) return;
        if (((HudReuse072BufferAccess) buffer).arcadium$hudBufferId() >= 0 || GL15.glIsBuffer(id)) asynchronousFailure = "Native deleted VBO is still live";
        else { buffers.get(buffer).deleted = true; verifiedDeletes++; }
    }
    private static Object field(RetainedHudBuffers cache, String name) throws Exception { Field field = RetainedHudBuffers.class.getDeclaredField(name); field.setAccessible(true); return field.get(cache); }
    private static void bounded(RetainedHudBuffers cache) throws Exception {
        Collection<?> entries = (Collection<?>) field(cache, "entries"); int capacity = 0;
        for (Object entry : entries) { Method method = entry.getClass().getDeclaredMethod("snapshot"); method.setAccessible(true); capacity += ((ByteBuffer) method.invoke(entry)).capacity(); }
        require(entries.size() <= 8 && capacity <= MAX_BYTES && capacity == (int) field(cache, "bytes"), "Real retained snapshot capacity exceeded its bounds");
        maxEntries = Math.max(maxEntries, entries.size()); maxCapacity = Math.max(maxCapacity, capacity);
    }
    private static void close(RetainedHudBuffers cache) throws Exception { cache.close(); cache.close(); require((int) field(cache, "bytes") == 0, "Closed cache retained capacity"); verifyAllDeleted(); }
    private static void verifyAllDeleted() { for (Owned owned : buffers.values()) require(owned.deleted, "Cache close did not delete an owned native buffer"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static Path output() { return Path.of(System.getProperty("arcadium.hudReuse072.output")).toAbsolutePath(); }
    private static void save(ByteBuffer pixels, String file) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) { int at = ((SIZE - y - 1) * SIZE + x) * 4;
            image.setRGB(x, y, (pixels.get(at + 3) & 255) << 24 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255); }
        Files.createDirectories(output().getParent()); ImageIO.write(image, "png", output().resolveSibling(file).toFile());
    }
    private static void finish(Throwable error) {
        report.put("schema", "arcadium.hud-native-reuse.v1"); report.put("passed", error == null); report.put("correctnessOnly", true); report.put("syntheticOffline", true);
        report.put("offscreenResolution", List.of(SIZE, SIZE)); report.put("fullImagesCompared", comparedImages); report.put("maximumPixelDifference", error == null ? 0 : null);
        report.put("actualGpuVertexDataReadbacks", verifiedUploads); report.put("actualNativeDrawCalls", actualDraws); report.put("nativeDeletionsVerifiedWithGlIsBuffer", verifiedDeletes);
        report.put("actualNativeVerticesDrawn", nativeVertices); report.put("maximumNativeVertexCount", maximumNativeVertices);
        report.put("minimumReferenceForegroundPixels", minimumForeground == Integer.MAX_VALUE ? 0 : minimumForeground);
        report.put("candidateVbosObserved", buffers.values().stream().filter(value -> value.candidate).count());
        report.put("referenceVbosObserved", buffers.values().stream().filter(value -> !value.candidate).count());
        report.put("maximumCacheEntries", maxEntries); report.put("maximumRetainedSnapshotCapacityBytes", maxCapacity); report.put("cases", cases);
        report.put("scope", "Actual Minecraft BufferBuilder/VertexBuffer and retained cache; full controlled RGBA8 offscreen images and full uploaded vertex-byte comparison. Large batches include offscreen padding quads, all uploaded/read back and vertex-count checked.");
        report.put("noPerformanceMeasurement", true); if (error != null) report.put("failure", error.toString());
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
    }

    private static final class Target implements AutoCloseable {
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int target = GL30.glGenFramebuffers(), texture = GL11.glGenTextures(), ownVao = GL30.glGenVertexArrays();
        Target() {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(0, SIZE, SIZE, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
            GL20.glUseProgram(0); GL30.glBindVertexArray(ownVao); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Native qualification target is incomplete");
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glViewport(0, 0, SIZE, SIZE);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_DITHER); GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D); GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glColorMask(true, true, true, true); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        }
        void clear() { GL11.glClearColor(.03f, .05f, .07f, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); }
        void read(ByteBuffer pixels) { pixels.clear(); GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels); }
        @Override public void close() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array); GL20.glUseProgram(program);
            GL30.glDeleteVertexArrays(ownVao); GL30.glDeleteFramebuffers(target); GL11.glDeleteTextures(texture);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(activeTexture);
        }
    }
}
