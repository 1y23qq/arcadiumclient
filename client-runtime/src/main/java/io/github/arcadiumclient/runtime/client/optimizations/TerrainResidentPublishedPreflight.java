// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.*;
import org.embeddedt.embeddium.impl.render.chunk.lists.*;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

final class TerrainResidentPublishedPreflight implements AutoCloseable {
    static final boolean BOOT=Boolean.getBoolean(TerrainResidentInventoryHooks.PROPERTY)&&GpuTerrainCommands.residentBootRequested();
    private final RenderSectionManager manager;
    private final TerrainRenderPass pass;
    private final int passIndex;
    private final TerrainResidentNativeAccess api;
    private final Matrix4f matrix=new Matrix4f();
    private final float[] clip=new float[16];
    private TerrainResidentGpuBuffers buffers;
    private TerrainResidentResources pending;
    private int capacity;
    private Object admittedProgram;
    private long orderEpoch=-1;
    private Prepared cachedPrepared;private float currentDistance;
    private List<ChunkRenderList> order=List.of();
    long orderRebuilds,steadyOrderReuses,readyFrames,replayRequests;
    record Prepared(TerrainResidentInventory.Frame frame,List<ChunkRenderList> drawOrder,TerrainResidentGpuBuffers buffers) {
        boolean valid(ChunkRenderListIterable current){return frame.valid(current);}
        int slot(Object storage,ChunkRenderListIterable current){return frame.slot(storage,current);}
    }
    TerrainResidentPublishedPreflight(RenderSectionManager manager,TerrainRenderPass pass,int passIndex)throws ReflectiveOperationException{
        if(!BOOT||passIndex<0||passIndex>1)throw new IllegalArgumentException("Published path is a startup-only private alternative");this.manager=manager;this.pass=pass;this.passIndex=passIndex;api=new TerrainResidentNativeAccess();
    }
    private static boolean supported(){if(!TerrainResidentGpuBuffers.trustedContext())return false;var caps=GL.getCapabilities();return caps.OpenGL43&&(caps.OpenGL46||caps.GL_ARB_indirect_parameters);}
    boolean admit(DefaultChunkRenderer renderer,ChunkRenderListIterable original,boolean nativeHasPass,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render){
        admittedProgram=null;if(nativeHasPass)return true;if(!supported())return false;
        try{if(!api.normalPass(manager)||!api.residentPass(renderer,pass)||!TerrainResidentNativeAccess.cameras(matrices,face,render))return false;
            var frame=TerrainResidentInventoryHooks.frame(manager,passIndex,original);if(frame==null||frame.activeRegions==0)return false;
            var admission=api.cachedAdmission(renderer,pass);if(admission==null||!admission.fact().settled(System.nanoTime(),frame.newestLoad))return false;admittedProgram=admission.program();return true;
        }catch(Throwable unknown){return false;}
    }
    Prepared prepare(DefaultChunkRenderer renderer,CommandList device,ChunkRenderListIterable original,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render,boolean cull)throws Throwable{
        if(!supported()||!api.normalPass(manager)||!api.residentPass(renderer,pass)||!TerrainResidentNativeAccess.cameras(matrices,face,render))return null;
        var fact=api.program(renderer,pass);if(fact==null||!api.admittedProgram(renderer,admittedProgram))return null;
        var frame=TerrainResidentInventoryHooks.frame(manager,passIndex,original);if(frame==null||frame.activeRegions==0||!fact.settled(System.nanoTime(),frame.newestLoad))return null;
        if(pending!=null)return null;
        if(buffers==null||capacity<frame.highWater){
            closeBuffers();int next=Math.min(1024,Integer.highestOneBit(Math.max(1,frame.highWater-1))<<1);if(frame.highWater==1)next=1;
            if(!TerrainGpuBudget.reserve(this,(long)next*TerrainResidentGpuBuffers.REGION_BYTES))return null;capacity=next;
            try(var input=TerrainResidentPublishedPreflight.class.getResourceAsStream("/assets/arcadium/shaders/terrain_resident_commands.comp")){
                if(input==null)throw new IllegalStateException("Missing unchanged native command kernel");buffers=new TerrainResidentGpuBuffers(device,capacity,new String(input.readAllBytes(),StandardCharsets.UTF_8));
            }catch(Throwable failure){if(failure instanceof TerrainResidentResources.AllocationFailure retained)pending=retained.pending;if(pending==null){capacity=0;TerrainGpuBudget.release(this);}throw failure;}
            TerrainResidentInventoryHooks.replay(manager,passIndex);replayRequests++;frame=TerrainResidentInventoryHooks.frame(manager,passIndex,original);if(frame==null)return null;
        }
        api.ensureIndices(renderer,device,pass,frame.maximumElements);
        matrix.set(matrices.projection()).mul(matrices.modelView()).get(clip);
        currentDistance=api.searchDistance(manager,matrices.projection());
        var result=buffers.preparePublished(frame,original,clip,render.intX,render.intY,render.intZ,render.fracX,render.fracY,render.fracZ,face.intX,face.intY,face.intZ,face.fracX,face.fracY,face.fracZ,currentDistance,true,cull);
        if(!result.ready())return null;
        if(!frame.valid(original))return null;
        if(orderEpoch!=frame.layoutVersion){order=TerrainResidentInventoryHooks.renderLists(manager,frame,original);orderEpoch=frame.layoutVersion;orderRebuilds++;}else steadyOrderReuses++;
        TerrainResidentInventoryHooks.acknowledge(manager,frame);var clean=TerrainResidentInventoryHooks.frame(manager,passIndex,original);if(clean==null)return null;readyFrames++;if(cachedPrepared==null||cachedPrepared.frame()!=clean||cachedPrepared.buffers()!=buffers||cachedPrepared.drawOrder()!=order)cachedPrepared=new Prepared(clean,order,buffers);return cachedPrepared;
    }
    void copyClip(float[] target){if(target.length!=16)throw new IllegalArgumentException("Clip size");System.arraycopy(clip,0,target,0,16);}
    float distance(){return currentDistance;}
    void requireOwner(){if(buffers!=null)buffers.requireOwner();if(pending!=null)pending.requireCurrent();}
    TerrainResidentGpuBuffers currentBuffers(){return buffers;}
    private void closeBuffers(){
        cachedPrepared=null;
        try{if(buffers!=null){buffers.close();if(buffers.resourcesReleased())buffers=null;}if(pending!=null){pending.close();if(pending.empty())pending=null;}}
        finally{if(buffers==null&&pending==null){capacity=0;TerrainGpuBudget.release(this);}}
    }
    long retainedBytes(){return buffers==null&&pending==null?0:(long)capacity*TerrainResidentGpuBuffers.REGION_BYTES;}
    @Override public void close(){closeBuffers();order=List.of();orderEpoch=-1;}
}
