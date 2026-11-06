// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

final class TerrainResidentNativeAccess {
    private final VarHandle regions,normalLists,activeProgram,currentPass,currentFormat,storageStrategy,managedActive;
    private final MethodHandle currentLists,searchDistance,prepareTessellation,sharedIndices;
    TerrainResidentNativeAccess()throws ReflectiveOperationException{
        regions=field(RenderSectionManager.class,"regions");normalLists=field(RenderSectionManager.class,"renderListManager");
        currentLists=method(RenderSectionManager.class,"getCurrentRenderListManager");searchDistance=method(RenderSectionManager.class,"getSearchDistance",Matrix4fc.class);
        activeProgram=field(ShaderChunkRenderer.class,"activeProgram");currentPass=field(DefaultChunkRenderer.class,"currentRenderPass");currentFormat=field(DefaultChunkRenderer.class,"currentVertexFormat");
        storageStrategy=field(SectionRenderDataStorage.class,"storageStrategy");managedActive=field(GLRenderDevice.class,"isActive");
        prepareTessellation=method(DefaultChunkRenderer.class,"prepareTessellation",CommandList.class,RenderRegion.class,TerrainRenderPass.class);
        sharedIndices=method(DefaultChunkRenderer.class,"getSharedIndexBuffer",ChunkPrimitiveType.class,CommandList.class);
    }
    RenderRegionManager regions(RenderSectionManager manager){return (RenderRegionManager)regions.get(manager);}
    boolean normalPass(RenderSectionManager manager)throws Throwable{return currentLists.invoke(manager)==normalLists.get(manager);}
    boolean managed(){return (boolean)managedActive.get(RenderDevice.INSTANCE);}
    float searchDistance(RenderSectionManager manager,Matrix4fc projection)throws Throwable{
        float value=(float)searchDistance.invoke(manager,projection);if(!Float.isFinite(value)||value<=0)throw new IllegalArgumentException("Unknown native terrain search distance");return value;
    }
    boolean storage(SectionRenderDataStorage storage){return storage!=null&&storage.getPrimitiveType()==QuadPrimitiveType.TRIANGULATED&&storageStrategy.get(storage)==SectionRenderDataUnsafe.Strategy.COMPACT;}
    TerrainResidentShaderFacts.Fact program(DefaultChunkRenderer renderer,TerrainRenderPass pass){
        if(!managed()||currentPass.get(renderer)!=pass||pass.isSorted()||pass.isReverseOrder()||!pass.name().equals("solid")
                ||pass.primitiveType()!=QuadPrimitiveType.TRIANGULATED||pass.vertexType().getClass()!=CompactChunkVertex.class)return null;
        GlVertexFormat expected=pass.vertexType().getVertexFormat();if(currentFormat.get(renderer)!=expected)return null;
        Object active=activeProgram.get(renderer);
        if(active==null||active.getClass()!=GlProgram.class)return null;
        GlProgram<?> program=(GlProgram<?>)active;
        Object api=program.getInterface();
        if(program.handle()==0||program.handle()!=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)||api==null||api.getClass()!=DefaultChunkShaderInterface.class)return null;
        var shader=(DefaultChunkShaderInterface)api;if(shader.getPrimitiveType().getId()!=GL11.GL_TRIANGLES)return null;
        return TerrainResidentShaderFacts.current(program);
    }
    void ensureIndices(DefaultChunkRenderer renderer,CommandList device,TerrainRenderPass pass,int maximumElements)throws Throwable{
        if(maximumElements<0)throw new IllegalArgumentException("Invalid native shared index requirement");
        ((SharedQuadIndexBuffer)sharedIndices.invoke(renderer,pass.primitiveType(),device)).ensureCapacity(device,maximumElements);
    }
    GlTessellation tessellation(DefaultChunkRenderer renderer,CommandList device,RenderRegion region,TerrainRenderPass pass)throws Throwable{
        if(currentPass.get(renderer)!=pass||currentFormat.get(renderer)==null)throw new IllegalStateException("Native terrain pass is not configured");
        GlTessellation value=(GlTessellation)prepareTessellation.invoke(renderer,device,region,pass);
        if(value==null||value.getClass()!=org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation.class)throw new IllegalArgumentException("Unknown native terrain tessellation binding");return value;
    }
    static boolean cameras(ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render){
        if(!camera(face)||!camera(render))return false;
        for(int column=0;column<4;column++)for(int row=0;row<4;row++)if(!Float.isFinite(matrices.projection().get(column,row))||!Float.isFinite(matrices.modelView().get(column,row)))return false;
        Matrix4fc projection=matrices.projection();return projection.m33()==0&&projection.m23()==-1&&projection.m00()>0&&projection.m11()>0;
    }
    private static boolean camera(CameraTransform value){
        return value!=null&&TerrainResidentCameraContract.axis(value.x,value.intX,value.fracX)
            &&TerrainResidentCameraContract.axis(value.y,value.intY,value.fracY)&&TerrainResidentCameraContract.axis(value.z,value.intZ,value.fracZ);
    }
    private static VarHandle field(Class<?> owner,String name)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).unreflectVarHandle(owner.getDeclaredField(name));}
    private static MethodHandle method(Class<?> owner,String name,Class<?>...args)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).unreflect(owner.getDeclaredMethod(name,args));}
}
