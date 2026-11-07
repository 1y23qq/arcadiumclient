// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import net.minecraft.client.render.platform.GlStateManager;

public final class GpuVoxelSkinPhysicalProjection {
    public static final boolean ENABLED=Boolean.getBoolean("arcadium.voxelSkin073.physicalMvp");
    public static final int MODE=Integer.getInteger("arcadium.voxelSkin073.physicalMvpMode",1);
    static final int ROW_LIMIT=8192;
    public static long modeQueries,modelViewQueries,projectionQueries,projectionReuses,capturedRows,declines,tableUploads,tableUploadBytes,drawScopes;
    public static long mdiAttempts,replayAttempts,fallbackPayloadUploads;
    private static final GpuVoxelSkinLeafScope scope=new GpuVoxelSkinLeafScope(ENABLED);
    private static int compileDepth;
    private static Thread compileThread;
    private static Boolean trustedHooks;
    public static String fallbackReason="Private physical MVP lane is off";
    private GpuVoxelSkinPhysicalProjection(){}

    public static int enterMesh(Object mesh,float scale,int list) {
        return scope.enter(mesh,scale,list);
    }
    public static void leaveMesh(int token) {
        scope.leave(token);
    }
    static Object mesh(int list){return scope.mesh(list);}
    static float scale(){return scope.scale();}
    static void beginFrame(){scope.beginFrame();}
    public static boolean compiling(){return ENABLED&&compileDepth>0&&compileThread==Thread.currentThread();}
    static void beginCompile(){if(compileDepth!=0&&compileThread!=Thread.currentThread())throw new IllegalStateException("Concurrent voxel program compile");compileThread=Thread.currentThread();compileDepth++;}
    static void endCompile(){if(--compileDepth==0)compileThread=null;}
    static boolean contextSupported(boolean gl43,int vertexBlocks,int bindings,int attributes,String renderer,String version) {
        return gl43&&vertexBlocks>=1&&bindings>=1&&attributes>=15&&(MODE==0||MODE==1)
                &&renderer!=null&&renderer.startsWith("NVIDIA GeForce RTX 5070 Ti")&&"4.6.0 NVIDIA 591.86".equals(version)&&hooksSupported();
    }
    private static boolean hooksSupported(){
        if(trustedHooks!=null)return trustedHooks;
        try{for(var method:GlStateManager.class.getDeclaredMethods())for(var annotation:method.getDeclaredAnnotations()){
            if(!annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged"))continue;
            String source=(String)annotation.annotationType().getMethod("mixin").invoke(annotation);
            if(!ResolvedUploadPolicy.knownMixin(source)){fallbackReason="Unknown matrix hook: "+source;return trustedHooks=false;}
        }fallbackReason="Physical MVP context and matrix hooks admitted";return trustedHooks=true;}
        catch(ReflectiveOperationException|LinkageError unavailable){fallbackReason="Matrix hook provenance unavailable";return trustedHooks=false;}
    }
    static void resetScopes(){scope.clear();}
    static void account(GpuVoxelSkinProjectionRows.Metrics before,GpuVoxelSkinProjectionRows.Metrics after) {
        modeQueries+=after.modeQueries()-before.modeQueries();modelViewQueries+=after.modelViewQueries()-before.modelViewQueries();
        projectionQueries+=after.projectionQueries()-before.projectionQueries();projectionReuses+=after.projectionReuse()-before.projectionReuse();
        capturedRows+=after.committedRows()-before.committedRows();declines+=after.declined()-before.declined();
    }
}
