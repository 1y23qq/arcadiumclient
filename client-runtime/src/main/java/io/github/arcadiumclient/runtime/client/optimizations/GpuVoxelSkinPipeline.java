// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;

public final class GpuVoxelSkinPipeline {
    public static volatile boolean enabled;
    public static long nativeParts, analyticParts, drawCalls, meshHits, vertexUploads, verticesDrawn, fallbacks,
            stateFallbacks, geometryFallbacks, budgetFallbacks, errors;
    public static long meshesBuilt, queuedVertices, submittedInstances, cpuRecoveries;
    public static long auxiliaryBatches,preShadowFlushes,fallbackFlushes,auxiliaryPreparations;
    public static long auxiliaryTextureBytes;
    public static long admissionFallbacks,binaryAlphaFallbacks,fractionalAlphaFallbacks,unknownImageFallbacks,
            dimensionFallbacks,samplerFallbacks,emissiveFallbacks,admissionSnapshots,admissionInvalidations,admissionHits,admissionAlphaBytes;
    public static int admissionSources,admissionMeshes,admissionClassifications;
    public static volatile String admissionFailure="";
    public static int auxiliaryBackends;
    public static int analyticPartMask, nativePartMask;
    private static volatile Backend backend;
    private static final GpuVoxelSkinAuxiliaries auxiliaries=new GpuVoxelSkinAuxiliaries(2);
    private static boolean failed;
    private static volatile String state = "Waiting for voxel skin layers";
    private GpuVoxelSkinPipeline() {}

    interface Backend {
        int queue(ModelPart[] parts, int mask, Identifier texture, int sourceJob, int sourceTemplate, int light, float age);
        boolean capture(Object mesh, float scale);
        void observe(Object model);
        void dispose(Object mesh);
        void beginBatch();
        void prepare(Object main);
        boolean usesMain(Object main);
        void flush(Object main,Object commands,boolean beforeShadows);
        void draw(Object geometry, Object commands, Object instances);
        void delete(Object geometry, Object commands);
        void reset();
        String status();
    }
    static void attach(Backend value) { backend = value; state = "Native skin atlas adapter available"; }
    static void fail(Throwable failure) {
        if (!failed) LogManager.getLogger("Arcadium/GPU Voxel Skins").warn("GPU voxel skins disabled; using original skin layers", failure);
        errors++; failed = true; state = "Disabled after an error; using original skin layers";
    }
    private static boolean ready() {
        if (!enabled || failed || !Skin3DConfig.getEnabled()) return false;
        if (backend == null) {
            try { Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinRenderer").getMethod("initialize").invoke(null); }
            catch (Throwable failure) { fail(failure); return false; }
        }
        return backend != null;
    }
    public static void beginBatch() { if (ready()) backend.beginBatch(); }
    public static boolean isAuxiliaryBackend(Object value){return auxiliaries.contains(value);}
    public static boolean usesMainBackend(Object value){return backend!=null&&backend.usesMain(value);}
    static boolean registerAuxiliary(Object main,Object auxiliary){boolean result=auxiliaries.register(main,auxiliary);auxiliaryBackends=auxiliaries.size();return result;}
    static void unregisterAuxiliary(Object auxiliary){auxiliaries.unregister(auxiliary);auxiliaryBackends=auxiliaries.size();}
    public static void prepareMainBatch(Object main,boolean active){if(active&&!isAuxiliaryBackend(main)&&ready())backend.prepare(main);}
    public static void beforeShadows(Object commands){if(backend!=null)backend.flush(null,commands,true);}
    public static void beforeMainFlush(Object main,Object commands){if(backend!=null&&!isAuxiliaryBackend(main))backend.flush(main,commands,false);}
    public static void observeModel(Object model) { if (ready()) backend.observe(model); }
    public static boolean captureMesh(Object mesh, float scale) { return ready() && backend.capture(mesh, scale); }
    public static int queueAnalytic(ModelPart[] parts, int mask, Identifier texture, int sourceJob, int sourceTemplate, int light, float age) {
        return ready() ? backend.queue(parts, mask, texture, sourceJob, sourceTemplate, light, age) : 0;
    }
    public static void disposeMesh(Object mesh) { if (backend != null) backend.dispose(mesh); }
    public static void drawNative(Object geometry, Object commands, Object instances) { if (backend != null) backend.draw(geometry, commands, instances); }
    public static void deleteNative(Object geometry, Object commands) { if (backend != null) backend.delete(geometry, commands); }
    public static void reset() {
        if (backend != null) backend.reset();
        failed = false; state = "Waiting for voxel skin layers";
    }
    public static String status() {
        if (!enabled) return "GPU voxel skins: Off";
        return "GPU voxel skins: " + (failed || backend == null ? state : backend.status()) + " | Native/analytic parts: "
                + nativeParts + "/" + analyticParts + " | Draws: " + drawCalls + " | Fallback: " + fallbacks;
    }
}
