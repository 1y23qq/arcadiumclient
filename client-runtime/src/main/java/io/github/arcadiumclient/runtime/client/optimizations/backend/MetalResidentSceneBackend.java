// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import static java.lang.foreign.ValueLayout.*;

public final class MetalResidentSceneBackend implements ResidentSceneBackend {
    private final Thread owner = Thread.currentThread();
    private static final ConcurrentHashMap<Path, SymbolLookup> LIBRARIES = new ConcurrentHashMap<>();
    private final Layout layout;
    private MethodHandle error, destroy, pipeline, mesh, material, removeMesh, removeMaterial,
            write, submit, await, read, query, reset;
    private MemorySegment context;
    private SymbolLookup symbols;
    private Runnable releasePresentation;
    private int width, height;

    public static MetalResidentSceneBackend open(Path library, Layout layout, String source, String vertex, String fragment) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            throw new UnsupportedOperationException("The Metal backend requires macOS");
        }
        return new MetalResidentSceneBackend(library, layout, source, vertex, fragment);
    }

    MetalResidentSceneBackend(Path library, Layout layout, String source, String vertex, String fragment) {
        this.layout = layout;
        try {
            var lookup = LIBRARIES.computeIfAbsent(library.toRealPath(), path -> SymbolLookup.libraryLookup(path, Arena.global()));
            symbols=lookup;
            var linker = Linker.nativeLinker();
            var abi = function(linker, lookup, "arc_scene_abi", FunctionDescriptor.of(JAVA_INT));
            if ((int) abi.invokeExact() != 1) throw new IllegalStateException("Incompatible resident scene ABI");
            error = function(linker, lookup, "arc_scene_error", FunctionDescriptor.of(ADDRESS, ADDRESS));
            destroy = function(linker, lookup, "arc_scene_destroy", FunctionDescriptor.ofVoid(ADDRESS));
            pipeline = function(linker, lookup, "arc_scene_pipeline", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            mesh = function(linker, lookup, "arc_scene_mesh", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
            material = function(linker, lookup, "arc_scene_material", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG));
            removeMesh = function(linker, lookup, "arc_scene_remove_mesh", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
            removeMaterial = function(linker, lookup, "arc_scene_remove_material", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
            write = function(linker, lookup, "arc_scene_write", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG));
            submit = function(linker, lookup, "arc_scene_submit", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT));
            await = function(linker, lookup, "arc_scene_wait", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            read = function(linker, lookup, "arc_scene_read_rgba", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
            query = function(linker, lookup, "arc_scene_query", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
            reset = function(linker, lookup, "arc_scene_reset", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            var create = function(linker, lookup, "arc_scene_create", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
            context = (MemorySegment) create.invokeExact(layout.actorCapacity(), layout.actorStride(), layout.vertexStride(), layout.uniformBytes());
            if (context.address() == 0) throw new IllegalStateException(nativeError());
            try (Arena strings = Arena.ofConfined()) {
                check((int) pipeline.invokeExact(context, strings.allocateFrom(source), strings.allocateFrom(vertex), strings.allocateFrom(fragment)));
            }
        } catch (Throwable failure) {
            if (context != null && context.address() != 0) {
                try { destroy.invokeExact(context); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                context = MemorySegment.NULL;
            }
            throw propagate(failure);
        }
    }

    private static MethodHandle function(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(lookup.find(name).orElseThrow(() -> new IllegalStateException("Missing native symbol: " + name)), descriptor);
    }

    @Override public String name() { return "Metal resident scene (offscreen prototype)"; }
    @Override public Layout layout() { return layout; }

    void presentationAvailable() { ready();if(releasePresentation!=null)throw new IllegalStateException("The scene already owns a presentation surface"); }
    void presentationReady() { ready(); }
    SymbolLookup presentationSymbols() { ready();return symbols; }
    MemorySegment presentationContext() { ready();return context; }
    void ownPresentation(Runnable cleanup) { presentationAvailable();releasePresentation=java.util.Objects.requireNonNull(cleanup); }

    private void ready() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("The resident backend belongs to its render thread");
        if (context == null || context.address() == 0) throw new IllegalStateException("The resident backend is closed");
    }

    private String nativeError() {
        try {
            MemorySegment argument = context == null ? MemorySegment.NULL : context;
            MemorySegment message = (MemorySegment) error.invokeExact(argument);
            return message.address() == 0 ? "Native Metal error" : message.reinterpret(1024).getString(0);
        } catch (Throwable failure) { return "Native Metal error: " + failure.getClass().getSimpleName(); }
    }

    private void check(int result) {
        if (result == 1) throw new IllegalStateException("The previous resident frame is still in flight");
        if (result != 0) throw new IllegalStateException(nativeError());
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native resident scene call failed", failure);
    }

    @Override public void putMesh(int id, ByteBuffer vertices) {
        ready(); SceneBufferChecks.resourceId(id); SceneBufferChecks.direct(vertices);
        if (vertices.remaining() == 0 || vertices.remaining() % layout.vertexStride() != 0) throw new IllegalArgumentException("Invalid mesh vertex stride");
        try { check((int) mesh.invokeExact(context, id, MemorySegment.ofBuffer(vertices), (long) vertices.remaining())); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    @Override public void putMaterial(int id, int width, int height, ByteBuffer rgba) {
        ready(); SceneBufferChecks.resourceId(id); SceneBufferChecks.direct(rgba);
        if (rgba.remaining() != SceneBufferChecks.rgbaBytes(width, height)) throw new IllegalArgumentException("Invalid RGBA material length");
        try { check((int) material.invokeExact(context, id, width, height, MemorySegment.ofBuffer(rgba), (long) rgba.remaining())); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    @Override public void removeMesh(int id) {
        ready(); SceneBufferChecks.resourceId(id);
        try { check((int) removeMesh.invokeExact(context, id)); } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public void removeMaterial(int id) {
        ready(); SceneBufferChecks.resourceId(id);
        try { check((int) removeMaterial.invokeExact(context, id)); } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public void writeActors(long offset, ByteBuffer bytes) {
        ready(); SceneBufferChecks.direct(bytes); SceneBufferChecks.range(offset, bytes.remaining(), layout.actorBytes());
        if (!bytes.hasRemaining()) return;
        try { check((int) write.invokeExact(context, offset, MemorySegment.ofBuffer(bytes), (long) bytes.remaining())); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    @Override public boolean submit(int width, int height, ByteBuffer uniforms, ByteBuffer commands, int commandCount) {
        ready(); SceneBufferChecks.rgbaBytes(width, height); SceneBufferChecks.direct(uniforms);
        if (uniforms.remaining() != layout.uniformBytes()) throw new IllegalArgumentException("Invalid frame uniform length");
        SceneBufferChecks.commands(commands, commandCount, layout.actorCapacity());
        try {
            int result = (int) submit.invokeExact(context, width, height, MemorySegment.ofBuffer(uniforms), (long) uniforms.remaining(), MemorySegment.ofBuffer(commands), commandCount);
            if (result == 1) return false;
            check(result); this.width = width; this.height = height; return true;
        } catch (Throwable failure) { throw propagate(failure); }
    }

    @Override public void awaitFrame() {
        ready();
        try { check((int) await.invokeExact(context)); } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public void readRgba(ByteBuffer destination) {
        ready(); SceneBufferChecks.direct(destination);
        if (destination.isReadOnly()) throw new IllegalArgumentException("Readback requires writable storage");
        if (width == 0 || destination.remaining() != SceneBufferChecks.rgbaBytes(width, height)) throw new IllegalArgumentException("Readback must match the submitted frame dimensions");
        try { check((int) read.invokeExact(context, MemorySegment.ofBuffer(destination), (long) destination.remaining())); }
        catch (Throwable failure) { throw propagate(failure); }
    }
    private long counter(int key) {
        try { return (long) query.invokeExact(context, key); } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public Statistics statistics() {
        ready(); return new Statistics(counter(0), counter(1), counter(2), counter(3), counter(4), counter(5), counter(6));
    }
    @Override public void reset() {
        ready();
        try { check((int) reset.invokeExact(context)); width = height = 0; } catch (Throwable failure) { throw propagate(failure); }
    }
    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("The resident backend belongs to its render thread");
        if (context == null || context.address() == 0) return;
        if(releasePresentation!=null){releasePresentation.run();releasePresentation=null;}
        try { destroy.invokeExact(context); }
        catch (Throwable failure) { throw propagate(failure); }
        finally { context = MemorySegment.NULL; }
    }
}
