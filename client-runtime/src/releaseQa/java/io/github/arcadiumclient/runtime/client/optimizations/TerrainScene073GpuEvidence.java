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
    private static final Set<Integer> ownedBuffers=new LinkedHashSet<>();
    private static final Set<Integer> ownedPrograms=new LinkedHashSet<>();
    private static final Set<TerrainResidentGpuBuffers> ownedPages=Collections.newSetFromMap(new IdentityHashMap<>());
    private static int ownedProgram;
    private static final Map<String,Map<String,Object>> configuredPasses=new LinkedHashMap<>();
    private static String operationKey;
    static String currentOperation()throws Exception{
        Class<?> oracle=Class.forName("io.github.arcadiumclient.runtime.qa.TerrainFullFrameOracle073");
        if(!(boolean)oracle.getMethod("controlling").invoke(null))return null;
        int stage=(int)value(oracle,null,"stage");if(stage<0)return null;
        Class<?> qa=Class.forName("io.github.arcadiumclient.runtime.qa.TerrainScene073Qa");return qa.getMethod("oracleViewIndex").invoke(null)+":"+stage+":"+qa.getMethod("oracleTick").invoke(null);
    }
    private static Map<String,Object> passRecord(String pass)throws Exception{
        String key=currentOperation();if(key==null)return null;if(!key.equals(operationKey)){operationKey=key;configuredPasses.clear();}
        if(configuredPasses.size()>=8&&!configuredPasses.containsKey(pass))throw new IllegalStateException("Native pass observer bound exceeded");
        return configuredPasses.computeIfAbsent(pass,ignored->{var row=new LinkedHashMap<String,Object>();row.put("configuredCalls",0);row.put("returnCalls",0);return row;});
    }
    public static Map<String,Object> configuredPass(Object renderer,String pass,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render)throws Throwable{
        var state=configured(renderer,matrices,face,render);var row=passRecord(pass);if(row!=null){row.put("configuredCalls",((Number)row.get("configuredCalls")).intValue()+1);row.put("state",state);row.put("physicalState",physicalState());}return state;
    }
    public static void returnedPass(String pass)throws Exception{var row=passRecord(pass);if(row!=null)row.put("returnCalls",((Number)row.get("returnCalls")).intValue()+1);if(TerrainEvent073Evidence.enabled())TerrainEvent073Evidence.returned(pass);}
    private static Map<String,Object> physicalState(){
        var row=new LinkedHashMap<String,Object>();row.put("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));row.put("blend",GL11.glIsEnabled(GL11.GL_BLEND));row.put("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));row.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));row.put("depthFunc",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));row.put("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));row.put("frontFace",GL11.glGetInteger(GL11.GL_FRONT_FACE));row.put("blendSrcRgb",GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));row.put("blendDstRgb",GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));return row;
    }
    public static Map<String,Object> passProof(boolean gpu)throws Exception{
        String key=currentOperation();if(key==null||!key.equals(operationKey))throw new IllegalStateException("No same-operation native pass state");var records=new LinkedHashMap<String,Object>();
        for(var item:configuredPasses.entrySet()){var row=new LinkedHashMap<>(item.getValue());if(((Number)row.get("returnCalls")).intValue()!=1)throw new IllegalStateException("Native pass did not return exactly once: "+item.getKey());records.put(item.getKey(),row);}
        if(gpu)for(String pass:List.of("solid","cutout_mipped")){
            var row=(Map<?,?>)records.get(pass);if(row==null||((Number)row.get("configuredCalls")).intValue()!=1)throw new IllegalStateException("Supported resident pass lost its actual configured callback: "+pass);
            var state=(Map<?,?>)row.get("state");var fact=(Map<?,?>)state.get("compiledFact");if(fact==null||!Boolean.TRUE.equals(state.get("physicalProgramMatches"))||Boolean.TRUE.equals(fact.get("fragmentDiscard"))!=pass.equals("cutout_mipped"))throw new IllegalStateException("Actual supported fragment-discard/program provenance differs");
        }
        var result=new LinkedHashMap<String,Object>();result.put("operation",key);result.put("nativePasses",records);result.put("actualConfiguredAndReturnCallbacks",true);result.put("readOnlyCorrectnessObserver",true);if(gpu){var counts=new LinkedHashMap<String,Object>();for(String pass:List.of("solid","cutout_mipped")){var observed=TerrainEvent073Evidence.enabled()?TerrainEvent073Evidence.pass(pass):null;if(TerrainEvent073Evidence.enabled()&&(observed==null||!key.equals(observed.get("operation"))))throw new IllegalStateException("Missing same-operation event pass snapshot: "+pass);var count=new LinkedHashMap<>(observed==null?currentCounts(pass):(Map<String,Object>)observed.get("counts"));count.put("actualIndirectRecipes",observed==null?TerrainOracleGeometry073.capture(pass):observed.get("actualGeometry"));if(observed!=null)count.put("actualEventSnapshot",observed);counts.put(pass,count);}result.put("residentPassCounts",counts);}return result;
    }

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
        if(scope!=null){if(nativeApi==null)nativeApi=new TerrainResidentNativeAccess();result.put("nativeNormalList",nativeApi.normalPass((RenderSectionManager)value(scope.getClass(),scope,"manager")));result.put("pass",((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)value(scope.getClass(),scope,"pass")).name());var pass=(org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)value(scope.getClass(),scope,"pass");var original=(org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable)value(scope.getClass(),scope,"original");result.put("nativeHasPass",original.hasPass(pass));result.put("admittedExistingProgram",value(scope.getClass(),scope,"admittedProgram")!=null);}
        result.put("physicalProgram",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));result.put("activeProgramClass",active==null?"null":active.getClass().getName());
        if(active instanceof GlProgram<?> program){
            result.put("activeProgram",program.handle());result.put("physicalProgramMatches",program.handle()==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            var fact=TerrainResidentShaderFacts.current(program);
            if(fact!=null)result.put("compiledFact",Map.of("fog",fact.fog(),"fragmentDiscard",fact.fragmentDiscard(),"fadeMilliseconds",fact.fadeMilliseconds(),"requiredAgeNanos",fact.requiredAgeNanos(),"regionSize",fact.regionSize(),"definesSha256",fact.definesSha256(),"sourcesSha256",fact.sourcesSha256()));
        }
        result.put("retainedPrograms",TerrainResidentShaderFacts.retainedPrograms());result.put("sourceCaptures",TerrainResidentShaderFacts.captures);result.put("sourceRejected",TerrainResidentShaderFacts.rejected);
        return result;
    }
    private static Map<String,Object> camera(CameraTransform c){return Map.of("position",List.of(c.x,c.y,c.z),"integer",List.of(c.intX,c.intY,c.intZ),"fraction",List.of(c.fracX,c.fracY,c.fracZ));}
    private static Object passScene(String passName)throws Exception{
        Field field=TerrainResidentScene.class.getDeclaredField("book");field.setAccessible(true);
        var book=(TerrainResidentPassBook<?>)field.get(null);if(book==null)throw new IllegalStateException("No resident terrain book");
        Object found=null;
        for(Object scene:book.values())if(((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)value(scene.getClass(),scene,"pass")).name().equals(passName)){
            if(found!=null)throw new IllegalStateException("Duplicated resident pass name");found=scene;
        }
        if(found==null)throw new IllegalStateException("Resident pass unavailable: "+passName);return found;
    }
    public static Map<String,Object> currentCounts()throws Exception{return currentCounts("solid");}
    public static Map<String,Object> currentCounts(String passName)throws Exception{
        Object scene=passScene(passName);
        TerrainResidentGpuBuffers buffers=TerrainEvent073Evidence.enabled()?TerrainEvent073Evidence.context(passName).buffers():(TerrainResidentGpuBuffers)value(scene.getClass(),scene,"buffers");
        if(buffers==null)throw new IllegalStateException("Resident buffers were not allocated");
        int regions=TerrainEvent073Evidence.enabled()?TerrainEvent073Evidence.context(passName).frame().highWater:((Map<?,?>)value(scene.getClass(),scene,"byStorage")).size();if(regions<1||regions>1024)throw new IllegalStateException("Invalid active resident region inventory");
        IntBuffer counts=BufferUtils.createIntBuffer(regions);int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);long total=0;
        try{
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffers.countHandle());GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,counts);
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        var rows=new ArrayList<Integer>();int positive=0;
        for(int i=0;i<regions;i++){int count=counts.get(i);if(count<0||count>1792)throw new IllegalStateException("Invalid resident draw count");rows.add(count);total+=count;if(count>0)positive++;}
        if(ownedPages.size()>=64&&!ownedPages.contains(buffers))throw new IllegalStateException("Owned pass allocation observer bound exceeded");ownedPages.add(buffers);var currentHandles=new LinkedHashSet<Integer>();
        for(String name:List.of("meshes","commands","counts")){
            var object=(org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer)value(TerrainResidentGpuBuffers.class,buffers,name);ownedBuffers.add(object.handle());currentHandles.add(object.handle());
        }
        ownedBuffers.add(buffers.orderHandle());currentHandles.add(buffers.orderHandle());ownedProgram=(int)value(TerrainResidentGpuBuffers.class,buffers,"program");
        ownedPrograms.add(ownedProgram);if(!GL20.glIsProgram(ownedProgram))throw new IllegalStateException("Resident compute program is not live");
        for(int handle:currentHandles)if(!GL15.glIsBuffer(handle))throw new IllegalStateException("Resident buffer is not live");
        var result=new LinkedHashMap<String,Object>();result.put("pass",passName);result.putAll(Map.of("correctnessOnlyReadback",true,"regions",regions,"positiveRegions",positive,"gpuCommandCount",total,"counts",rows,
            "residentBytes",buffers.residentBytes(),"budgetBytes",TerrainGpuBudget.bytes(),"budgetOwners",TerrainGpuBudget.owners(),"metadataUploads",buffers.meshUploads,"computeDispatches",buffers.dispatches));return result;
    }
    public static Map<String,Object> released(){
        var pages=new ArrayList<Map<String,Object>>();boolean deleted=!ownedPages.isEmpty();
        for(TerrainResidentGpuBuffers page:ownedPages){boolean complete=page.resourcesReleased();deleted&=complete;pages.add(Map.of("pageIdentity",System.identityHashCode(page),"allOwnedNativeDeleteCallsReturned",complete));}
        var numericBuffers=new ArrayList<Map<String,Object>>();for(int name:ownedBuffers)numericBuffers.add(Map.of("name",name,"currentlyLive",GL15.glIsBuffer(name)));
        var numericPrograms=new ArrayList<Map<String,Object>>();for(int name:ownedPrograms)numericPrograms.add(Map.of("name",name,"currentlyLive",GL20.glIsProgram(name)));
        return Map.of("ownedObjectsDeleted",deleted,"programDeleted",deleted,"allocationPages",pages,"numericBufferNameObservations",numericBuffers,"numericProgramNameObservations",numericPrograms,"usesAllocationIdentityNotReusedNumericNames",true,"budgetBytes",TerrainGpuBudget.bytes(),"budgetOwners",TerrainGpuBudget.owners(),"residentBytes",GpuTerrainCommands.residentBytes());
    }

}
