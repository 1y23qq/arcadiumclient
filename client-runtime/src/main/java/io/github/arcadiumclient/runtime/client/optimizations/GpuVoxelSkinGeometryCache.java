// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;

public final class GpuVoxelSkinGeometryCache {
    public static final int DEFAULT_WORD_BUDGET = 8 * 1024 * 1024, DEFAULT_MESH_BUDGET = 1024;
    public static final class Entry {
        private final Object mesh;
        private final int scale, nx, ny, nz;
        private int first;
        private final int vertices;
        private long used;
        private boolean disposed;
        private Entry(Object mesh, float scale, GpuVoxelSkinMath.Offset offset, int first, int vertices, long epoch) {
            this.mesh = mesh; this.scale = Float.floatToRawIntBits(scale);
            nx = Float.floatToRawIntBits(offset.normalX()); ny = Float.floatToRawIntBits(offset.normalY()); nz = Float.floatToRawIntBits(offset.normalZ());
            this.first = first; this.vertices = vertices; used = epoch;
        }
        public int firstVertex() { return first; }
        public int vertexCount() { return vertices; }
        public boolean disposed() { return disposed; }
    }
    private final IdentityHashMap<Object, List<Entry>> meshes = new IdentityHashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final int wordBudget, meshBudget;
    private int[] words = new int[4096];
    private int size, deadWords;
    private long epoch, generation;
    private boolean reclaim;
    public long hits, misses, budgetFallbacks, invalidations, compactions;

    public GpuVoxelSkinGeometryCache() { this(DEFAULT_WORD_BUDGET, DEFAULT_MESH_BUDGET); }
    public GpuVoxelSkinGeometryCache(int wordBudget, int meshBudget) {
        if (wordBudget < 1 || meshBudget < 1) throw new IllegalArgumentException("Invalid voxel cache budget");
        this.wordBudget = wordBudget; this.meshBudget = meshBudget;
        if (words.length > wordBudget) words = new int[wordBudget];
    }

    public void beginBatch() {
        epoch++;
        if (reclaim || deadWords > size / 3) compact();
        reclaim = false;
    }

    public Entry mesh(Object mesh, float[] quads, float scale, GpuVoxelSkinMath.Offset offset) {
        Objects.requireNonNull(mesh);
        List<Entry> variants = meshes.get(mesh);
        if (variants != null) for (Entry entry : variants) {
            if (entry.scale == Float.floatToRawIntBits(scale) && entry.nx == Float.floatToRawIntBits(offset.normalX())
                    && entry.ny == Float.floatToRawIntBits(offset.normalY()) && entry.nz == Float.floatToRawIntBits(offset.normalZ())) {
                entry.used = epoch; hits++; return entry;
            }
        }
        long required = (long) quads.length / GpuVoxelSkinMath.QUAD_FLOATS * 4 * GpuVoxelSkinMath.VERTEX_WORDS;
        if (entries.size() == meshBudget || required > wordBudget - size) { reclaim = true; budgetFallbacks++; return null; }
        int[] converted = GpuVoxelSkinMath.vertices(quads, scale, offset);
        if (size + converted.length > words.length) words = Arrays.copyOf(words, Math.min(wordBudget, Math.max(size + converted.length, words.length * 2)));
        Entry entry = new Entry(mesh, scale, offset, size / GpuVoxelSkinMath.VERTEX_WORDS, converted.length / GpuVoxelSkinMath.VERTEX_WORDS, epoch);
        System.arraycopy(converted, 0, words, size, converted.length); size += converted.length;
        entries.add(entry); meshes.computeIfAbsent(mesh, key -> new ArrayList<>(1)).add(entry); misses++;
        return entry;
    }

    public void dispose(Object mesh) {
        List<Entry> removed = meshes.remove(mesh);
        if (removed == null) return;
        for (Entry entry : removed) if (!entry.disposed) { entry.disposed = true; deadWords += entry.vertices * GpuVoxelSkinMath.VERTEX_WORDS; invalidations++; }
    }

    private void compact() {
        int target = 0; boolean changed = false;
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.disposed || epoch - entry.used > 2) {
                entry.disposed = true;
                List<Entry> variants = meshes.get(entry.mesh);
                if (variants != null) { variants.remove(entry); if (variants.isEmpty()) meshes.remove(entry.mesh); }
                iterator.remove(); changed = true; continue;
            }
            int length = entry.vertices * GpuVoxelSkinMath.VERTEX_WORDS;
            if (target != entry.first * GpuVoxelSkinMath.VERTEX_WORDS) {
                System.arraycopy(words, entry.first * GpuVoxelSkinMath.VERTEX_WORDS, words, target, length);
                entry.first = target / GpuVoxelSkinMath.VERTEX_WORDS; changed = true;
            }
            target += length;
        }
        size = target; deadWords = 0;
        if (changed) { generation++; compactions++; }
    }

    public int sizeWords() { return size; }
    public long generation() { return generation; }
    public int[] words() { return words; }
    public int meshCount() { return entries.size(); }
    public void reset() {
        entries.clear(); meshes.clear(); size = deadWords = 0; words = new int[Math.min(4096, wordBudget)];
        generation++; reclaim = false;
    }
}
