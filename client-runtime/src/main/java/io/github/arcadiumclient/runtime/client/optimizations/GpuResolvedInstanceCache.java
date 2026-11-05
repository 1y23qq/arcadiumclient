// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

public final class GpuResolvedInstanceCache {
    public static volatile boolean enabled;
    public static long hits, misses, bytesAvoided, expansionsAvoided, sortsAvoided, unknownFallbacks, capacityFallbacks, repairs, invalidations;
    private static final int STRIDE = 19, MAGIC = Float.floatToRawIntBits(-9876543f), SORT_MAGIC = Float.floatToRawIntBits(-7654321f);
    private static final ResolvedInstanceRecords records = new ResolvedInstanceRecords();
    private static final Upload[] uploads = new Upload[16];
    private static long epoch = Long.MIN_VALUE;
    private static Object backend;
    private static int depth, driver, blocked;
    private static boolean wasEnabled;
    private static String state = "Waiting for a repeated native payload";

    private static final class Upload {
        final ResolvedInstanceRecords.Stamp stamp = new ResolvedInstanceRecords.Stamp(), check = new ResolvedInstanceRecords.Stamp();
        Object owner;
        CommandList commands;
        IntBuffer payload;
        GlMutableBuffer buffer;
        ResolvedInstanceRecords.Entry entry;
        long serial, address, bytes;
        int previousDriver;
        boolean skipped, confirmed, driverSeen, sorted;
        void clear() { owner = null; commands = null; payload = null; buffer = null; entry = null; stamp.backend = check.backend = null; }
    }
    static { for (int index = 0; index < uploads.length; index++) uploads[index] = new Upload(); }
    private GpuResolvedInstanceCache() {}

    private static void stamp(ResolvedInstanceRecords.Stamp value) {
        value.set(GpuModelTransforms.batchOwner(), GpuModelTransforms.batchEpoch(), GpuModelTransforms.modelRevision(),
                GpuPlayerPackets.playerRevision(), GpuPlayerPackets.poseRevision(), GpuPlayerPackets.templateRevision());
    }

    public static int beginUpload(Object owner, CommandList commands, IntBuffer payload) {
        boolean active = enabled && GpuResidentPlayerState.enabled;
        if (!active) {
            if (wasEnabled) reset();
            wasEnabled = false; return blockNested();
        }
        wasEnabled = true;
        Object current = GpuModelTransforms.batchOwner(); long currentEpoch = GpuModelTransforms.batchEpoch();
        if (backend != current || epoch != currentEpoch) { records.clear(); backend = current; epoch = currentEpoch; }
        if (current == null || !GpuModelTransforms.batchThread() || !GpuModelTransforms.outputsReusable() || payload.remaining() % STRIDE != 0 ||
                !GpuModelTransforms.hasJobs(payload) || !deferredSort(payload)) { records.invalidate(owner); return blockNested(); }
        if (!ResolvedUploadPolicy.trusted(commands)) { records.invalidate(owner); unknownFallbacks++; state = ResolvedUploadPolicy.reason(); return blockNested(); }
        if (depth == uploads.length) { records.invalidate(owner); capacityFallbacks++; blocked++; return -1; }
        Upload upload = uploads[depth++]; upload.clear(); upload.owner = owner; upload.commands = commands; upload.payload = payload;
        upload.skipped = upload.confirmed = upload.driverSeen = upload.sorted = false;
        return depth;
    }

    private static int blockNested() { if (depth == 0) return 0; blocked++; return -1; }
    private static boolean deferredSort(IntBuffer words) {
        for (int at = words.position(); at < words.limit(); at += STRIDE)
            if (words.get(at + 1) == MAGIC && words.get(at + 2) == SORT_MAGIC) return true;
        return false;
    }

    public static int beginDriver(Object owner, CommandList commands, GlMutableBuffer buffer, long address, long bytes, GlBufferUsage usage) {
        if (depth == 0 || blocked != 0) return 0;
        Upload upload = uploads[depth - 1];
        if (upload.owner != owner || upload.commands != commands || buffer == null || usage != GlBufferUsage.STREAM_DRAW ||
                bytes != (long)upload.payload.remaining() * Integer.BYTES || address != MemoryUtil.memAddress(upload.payload)) { records.invalidate(owner); return 0; }
        if (upload.driverSeen) { records.invalidate(owner); return 0; }
        upload.driverSeen = true; upload.buffer = buffer; upload.address = address; upload.bytes = bytes;
        upload.previousDriver = driver; driver = depth; return depth;
    }

    public static void endDriver(int token) {
        if (token > 0 && token <= depth && driver == token) driver = uploads[token - 1].previousDriver;
    }
    public static void endDriver(Object owner) {
        if (driver > 0 && driver <= depth && uploads[driver - 1].owner == owner) endDriver(driver);
    }
    public static boolean activeUpload(Object owner) { return depth > 0 && blocked == 0 && uploads[depth - 1].owner == owner; }

    public static boolean skipDriver(Object commands, GlMutableBuffer buffer, Object service, int target, long size, long address, int usage) {
        if (driver == 0 || driver > depth) return false;
        Upload upload = uploads[driver - 1];
        if (blocked != 0 || !enabled || !GpuResidentPlayerState.enabled || upload.commands != commands || upload.buffer != buffer || upload.bytes != size || upload.address != address ||
                target != GL15.GL_ARRAY_BUFFER || usage != GL15.GL_STREAM_DRAW || !ResolvedUploadPolicy.service(service)) {
            records.invalidate(upload.owner); unknownFallbacks++; state = "Unrecognized driver upload; using streaming"; return false;
        }
        stamp(upload.stamp);
        var cached = records.find(upload.owner, buffer, buffer.handle(), upload.stamp, upload.payload);
        if (cached != null) {
            upload.entry = cached; upload.serial = cached.serial; upload.skipped = true;
            return true;
        }
        misses++;
        upload.entry = records.stage(upload.owner, buffer, buffer.handle(), upload.stamp, upload.payload);
        if (upload.entry == null) capacityFallbacks++; else upload.serial = upload.entry.serial;
        for (int at = upload.payload.position(); at < upload.payload.limit(); at += STRIDE) upload.sorted |= upload.payload.get(at + 2) == SORT_MAGIC;
        return false;
    }

    public static boolean reused(int token, GlMutableBuffer buffer) {
        if (token <= 0 || token > depth) return false;
        Upload upload = uploads[token - 1];
        if (!upload.skipped) return false;
        stamp(upload.check);
        if (enabled && GpuResidentPlayerState.enabled && buffer == upload.buffer && upload.entry.serial == upload.serial &&
                records.find(upload.owner, buffer, buffer.handle(), upload.check, upload.payload) == upload.entry) {
            upload.confirmed = true; hits++; bytesAvoided += upload.bytes; expansionsAvoided++;
            for (int at = upload.payload.position(); at < upload.payload.limit(); at += STRIDE) if (upload.payload.get(at + 2) == SORT_MAGIC) { sortsAvoided++; break; }
            state = "Repeated native output reused within the same batch"; return true;
        }
        repairs++; records.invalidate(upload.owner); state = "Changed upload state; using normal streaming"; return false;
    }

    public static boolean needsRepair(int token) { return token > 0 && token <= depth && uploads[token - 1].skipped && !uploads[token - 1].confirmed; }

    public static void complete(int token, GlMutableBuffer buffer) {
        if (token <= 0 || token > depth) return;
        Upload upload = uploads[token - 1];
        if (upload.skipped || upload.entry == null || buffer == null) return;
        stamp(upload.check);
        if (!records.commit(upload.entry, upload.serial, buffer, buffer.handle(), upload.check, upload.payload)) records.invalidate(upload.owner);
    }

    public static void abort(int token) {
        if (token > 0 && token <= depth) { Upload upload = uploads[token - 1]; records.invalidate(upload.owner); upload.entry = null; }
    }

    public static void endUpload(int token) {
        if (token < 0) { if (blocked > 0) blocked--; return; }
        if (token == 0) return;
        if (token != depth) { reset(); state = "Nested upload mismatch; using normal streaming"; return; }
        if (driver == token) endDriver(token);
        uploads[--depth].clear();
    }

    public static void invalidate(Object owner) { records.invalidate(owner); invalidations++; }
    public static void reset() {
        records.clear(); backend = null; epoch = Long.MIN_VALUE; driver = blocked = 0;
        for (int index = 0; index < depth; index++) uploads[index].clear();
        depth = 0; invalidations++;
    }
    public static long residentBytes() { return records.residentBytes(); }
    public static String status() {
        return enabled ? "Resolved player output: " + state + " | Reused uploads: " + hits + " | Saved bytes: " + bytesAvoided +
                " | CPU bytes: " + residentBytes() + "/" + ResolvedInstanceRecords.CPU_BYTES : "Resolved player output: Off";
    }
}
