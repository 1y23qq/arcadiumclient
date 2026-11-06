// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.*;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class TerrainScene073GpuEvidence {
    private static final List<Integer> ownedBuffers=new ArrayList<>();
    private static int ownedProgram;
    private static TerrainResidentNativeAccess nativeApi;
    private TerrainScene073GpuEvidence(){}
    private static Object value(Class<?> owner,Object object,String name)throws Exception{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    public static Map<String,Object> configured(Object renderer,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render)throws Throwable{
        Object active=value(ShaderChunkRenderer.class,renderer,"activeProgram");
        var result=new LinkedHashMap<String,Object>();
        result.put("faceCamera",camera(face));result.put("renderCamera",camera(render));result.put("camerasEqual",face.equals(render));
        result.put("projection",matrices.projection().get(new float[16]));result.put("modelView",matrices.modelView().get(new float[16]));
        result.put("cameraContractAccepted",TerrainResidentNativeAccess.cameras(matrices,face,render));
        Object scope=((ThreadLocal<?>)value(TerrainResidentScene.class,null,"current")).get();
        if(scope!=null){if(nativeApi==null)nativeApi=new TerrainResidentNativeAccess();result.put("nativeNormalList",nativeApi.normalPass((RenderSectionManager)value(scope.getClass(),scope,"manager")));}
        result.put("physicalProgram",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));result.put("activeProgramClass",active==null?"null":active.getClass().getName());
        if(active instanceof GlProgram<?> program){
            result.put("activeProgram",program.handle());result.put("physicalProgramMatches",program.handle()==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            var fact=TerrainResidentShaderFacts.current(program);
            if(fact!=null)result.put("compiledFact",Map.of("fog",fact.fog(),"fadeMilliseconds",fact.fadeMilliseconds(),"requiredAgeNanos",fact.requiredAgeNanos(),"regionSize",fact.regionSize(),"definesSha256",fact.definesSha256(),"sourcesSha256",fact.sourcesSha256()));
        }
        result.put("retainedPrograms",TerrainResidentShaderFacts.retainedPrograms());result.put("sourceCaptures",TerrainResidentShaderFacts.captures);result.put("sourceRejected",TerrainResidentShaderFacts.rejected);
        return result;
    }
    private static Map<String,Object> camera(CameraTransform c){return Map.of("position",List.of(c.x,c.y,c.z),"integer",List.of(c.intX,c.intY,c.intZ),"fraction",List.of(c.fracX,c.fracY,c.fracZ));}
    public static Map<String,Object> currentCounts()throws Exception{
        Object scene=value(TerrainResidentScene.class,null,"scene");if(scene==null)throw new IllegalStateException("Resident scene was not attached");
        TerrainResidentGpuBuffers buffers=(TerrainResidentGpuBuffers)value(scene.getClass(),scene,"buffers");
        if(buffers==null)throw new IllegalStateException("Resident buffers were not allocated");
        int regions=((Map<?,?>)value(scene.getClass(),scene,"byStorage")).size();if(regions<1||regions>1024)throw new IllegalStateException("Invalid active resident region inventory");
        IntBuffer counts=BufferUtils.createIntBuffer(regions);int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);long total=0;
        try{
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffers.countHandle());GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,counts);
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        var rows=new ArrayList<Integer>();int positive=0;
        for(int i=0;i<regions;i++){int count=counts.get(i);if(count<0||count>1792)throw new IllegalStateException("Invalid resident draw count");rows.add(count);total+=count;if(count>0)positive++;}
        ownedBuffers.clear();for(String name:List.of("meshes","commands","counts")){
            var object=(org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer)value(TerrainResidentGpuBuffers.class,buffers,name);ownedBuffers.add(object.handle());
        }
        ownedBuffers.add(buffers.orderHandle());ownedProgram=(int)value(TerrainResidentGpuBuffers.class,buffers,"program");
        if(!GL20.glIsProgram(ownedProgram))throw new IllegalStateException("Resident compute program is not live");
        for(int handle:ownedBuffers)if(!GL15.glIsBuffer(handle))throw new IllegalStateException("Resident buffer is not live");
        return Map.of("correctnessOnlyReadback",true,"regions",regions,"positiveRegions",positive,"gpuCommandCount",total,"counts",rows,
            "residentBytes",buffers.residentBytes(),"budgetBytes",TerrainGpuBudget.bytes(),"budgetOwners",TerrainGpuBudget.owners(),"metadataUploads",buffers.meshUploads,"computeDispatches",buffers.dispatches);
    }
    public static Map<String,Object> released(){
        var buffers=new ArrayList<Map<String,Object>>();boolean deleted=true;
        for(int handle:ownedBuffers){boolean exists=GL15.glIsBuffer(handle);deleted&=!exists;buffers.add(Map.of("name",handle,"deleted",!exists));}
        boolean programDeleted=ownedProgram!=0&&!GL20.glIsProgram(ownedProgram);deleted&=programDeleted;
        return Map.of("ownedObjectsDeleted",deleted,"buffers",buffers,"program",ownedProgram,"programDeleted",programDeleted,
            "budgetBytes",TerrainGpuBudget.bytes(),"budgetOwners",TerrainGpuBudget.owners(),"residentBytes",GpuTerrainCommands.residentBytes());
    }
}
