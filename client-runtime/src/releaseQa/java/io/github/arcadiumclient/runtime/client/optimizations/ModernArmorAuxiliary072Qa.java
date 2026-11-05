// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public final class ModernArmorAuxiliary072Qa {
    private static final Set<Integer> mainBuffers=new HashSet<>(),mainPrograms=new HashSet<>(),mainArrays=new HashSet<>();
    private static final Set<Integer> ownedBuffers=new HashSet<>(),ownedPrograms=new HashSet<>(),ownedArrays=new HashSet<>();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static boolean observeDraws;
    private static int nativeDraws;
    private static boolean coldFault;
    private static int copyAttempts,copiesCompleted,drawsBeforeFault,jobsAtFault;
    private static long failuresBeforeFault;
    private static Object ownerAtFault;
    private static final Set<Integer> partialBuffers=new HashSet<>(),partialArrays=new HashSet<>();
    private ModernArmorAuxiliary072Qa(){}

    public static void closeAndValidate()throws Throwable{
        Map<?,?> states=(Map<?,?>)staticValue(ModernArmorAuxiliary.class,"owners");
        ModernArmorAuxiliaryAccess api=(ModernArmorAuxiliaryAccess)staticValue(ModernArmorAuxiliary.class,"api");
        require(states.size()==1&&api!=null,"Missing actual modern auxiliary owner");
        int copies=0;long bytes=0;List<Object> details=new ArrayList<>();
        for(Object state:states.values()){
            Object main=value(state,"main"),auxiliary=value(state,"auxiliary");
            require(main!=auxiliary&&GpuModelAuxiliaries.isAuxiliaryBackend(auxiliary)&&!GpuModelAuxiliaries.isAuxiliaryBackend(main),"Modern auxiliary registry ownership mismatch");
            require(!(boolean)api.textureArraysSupported.get(auxiliary),"Owned modern renderer did not use its preflighted2D capability");
            Map<?,?> sourcePrograms=(Map<?,?>)api.programs.get(main),targetPrograms=(Map<?,?>)api.programs.get(auxiliary);
            require(sourcePrograms!=targetPrograms&&!sourcePrograms.isEmpty()&&!targetPrograms.isEmpty(),"Main/auxiliary program ownership missing");
            for(Object program:sourcePrograms.values())mainPrograms.add(((GlProgram<?>)program).handle());
            for(Object program:targetPrograms.values())ownedPrograms.add(((GlProgram<?>)program).handle());
            for(Object box:(Object[])value(main,"boxGeometries"))if(box!=null){Object buffers=bufferField(box);if(buffers!=null)collectMain(api,buffers);}
            for(Object geometry:((Map<?,?>)value(state,"geometries")).values()){
                require((boolean)value(geometry,"ready"),"A cloned armor part was never preflighted");
                Object source=value(geometry,"sourceBuffers"),target=value(geometry,"buffers");require(source!=target&&target!=null,"Clone shares main mutable buffer object");
                GlMutableBuffer src=(GlMutableBuffer)api.vertexBuffer.get(source),dst=(GlMutableBuffer)api.vertexBuffer.get(target);
                GlMutableBuffer sourceInstances=(GlMutableBuffer)api.instanceBuffer.get(source),targetInstances=(GlMutableBuffer)api.instanceBuffer.get(target);
                long extent=(long)value(geometry,"bytes");require(src!=dst&&sourceInstances!=targetInstances&&src.getSize()==extent&&dst.getSize()==extent,"Cloned native vertex extent/ownership differs");
                require(equalBuffers(src.handle(),dst.handle(),extent),"GPU-to-GPU original armor vertex bytes differ");
                require(api.vertices.get(source)==null&&api.vertices.get(target)==null,"Clone did not complete the actual native GPU initialization");
                collectMain(api,source);ownedBuffers.add(dst.handle());ownedBuffers.add(targetInstances.handle());ownedArrays.add(array(target));
                copies++;bytes+=extent;details.add(Map.of("sourceVertex",src.handle(),"cloneVertex",dst.handle(),"sourceInstances",sourceInstances.handle(),"ownedInstances",targetInstances.handle(),"bytes",extent));
            }
        }
        require(copies==14&&bytes<=4L*1024*1024,"Missing all14 exact original armor clones");
        require(disjoint(mainBuffers,ownedBuffers)&&disjoint(mainPrograms,ownedPrograms)&&disjoint(mainArrays,ownedArrays),"Modern auxiliary aliases main GL ownership");
        report.put("gpuToGpuCopies",copies);report.put("identicalVertexBytes",bytes);report.put("geometryIds",details);
        report.put("mainPrograms",mainPrograms);report.put("ownedPrograms",ownedPrograms);report.put("mainVertexArrays",mainArrays);report.put("ownedVertexArrays",ownedArrays);
        ModernArmorAuxiliary.close();
        require(((Map<?,?>)staticValue(ModernArmorAuxiliary.class,"owners")).isEmpty(),"Modern owner remained after close");
        require(GpuModelAuxiliaries.size()==0,"Common voxel/modern auxiliary registry was not empty after shutdown");
        for(int id:mainBuffers)require(GL15.glIsBuffer(id),"Main buffer was deleted by modern close");
        for(int id:mainPrograms)require(GL20.glIsProgram(id),"Main program was deleted by modern close");
        for(int id:mainArrays)require(GL30.glIsVertexArray(id),"Main VAO was deleted by modern close");
        for(int id:ownedBuffers)require(!GL15.glIsBuffer(id),"Owned modern buffer leaked after close");
        for(int id:ownedPrograms)require(!GL20.glIsProgram(id),"Owned modern program leaked after close");
        for(int id:ownedArrays)require(!GL30.glIsVertexArray(id),"Owned modern VAO leaked after close");
        report.put("mainResourcesSurvived",true);report.put("ownedResourcesReleased",true);report.put("globalAuxiliaryRegistryAfterClose",GpuModelAuxiliaries.size());
        observeDraws=true;
    }
    public static void drawCompleted(){
        if(!observeDraws)return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(!mainPrograms.contains(program))return;
        int position=GL20.glGetAttribLocation(program,"aPosition");if(position<0)return;
        int buffer=GL20.glGetVertexAttribi(position,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);if(mainBuffers.contains(buffer))nativeDraws++;
    }
    public static Map<String,Object> finish(){
        observeDraws=false;require(nativeDraws>0,"No actual surviving Main geometry/program draw after auxiliary close");
        int error=GL11.glGetError();report.put("actualMainDrawsAfterClose",nativeDraws);report.put("finalGlError",error);require(error==GL11.GL_NO_ERROR,"GL error after Main survival proof");return report;
    }
    public static void armColdFailure(){
        coldFault=true;drawsBeforeFault=nativeDraws;failuresBeforeFault=ModernArmorAuxiliary.failures;observeDraws=true;
    }
    public static void beforeColdCopy()throws Throwable{
        if(!coldFault)return;
        if(++copyAttempts!=2)return;
        ModernArmorAuxiliaryAccess api=(ModernArmorAuxiliaryAccess)staticValue(ModernArmorAuxiliary.class,"api");
        for(Object state:((Map<?,?>)staticValue(ModernArmorAuxiliary.class,"owners")).values())for(Object geometry:((Map<?,?>)value(state,"geometries")).values()){
            Object buffers=value(geometry,"buffers");if(buffers==null)continue;
            partialBuffers.add(((GlMutableBuffer)api.vertexBuffer.get(buffers)).handle());partialBuffers.add(((GlMutableBuffer)api.instanceBuffer.get(buffers)).handle());partialArrays.add(array(buffers));
        }
        jobsAtFault=GpuPlayerPackets.count();ownerAtFault=GpuModelTransforms.batchOwner();
        throw new IllegalStateException("Expected isolated QA second-copy failure");
    }
    public static void coldCopyCompleted(){if(coldFault)copiesCompleted++;}
    public static Map<String,Object> finishColdFailure()throws Throwable{
        coldFault=false;observeDraws=false;
        require(copyAttempts==2&&copiesCompleted==1&&partialBuffers.size()==4&&partialArrays.size()==2,"Partial allocation failure was not exercised at its exact second copy");
        require(ModernArmorAuxiliary.failures==failuresBeforeFault+1,"Expected cold failure was not caught exactly once");
        require(GpuModelAuxiliaries.size()==0&&((Map<?,?>)staticValue(ModernArmorAuxiliary.class,"owners")).isEmpty(),"Failed cold auxiliary retained an owner");
        for(int id:partialBuffers)require(!GL15.glIsBuffer(id),"Partial owned buffer leaked");for(int id:partialArrays)require(!GL30.glIsVertexArray(id),"Partial owned VAO leaked");
        for(int id:mainBuffers)require(GL15.glIsBuffer(id),"Partial failure deleted a Main buffer");for(int id:mainPrograms)require(GL20.glIsProgram(id),"Partial failure deleted a Main program");
        require(GpuPlayerPackets.count()==jobsAtFault&&GpuModelTransforms.batchOwner()==ownerAtFault,"Cold auxiliary failure changed Main jobs or ownership");
        require(nativeDraws>drawsBeforeFault,"Main did not actually draw after cold preparation failed");
        int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"GL error after partial allocation cleanup");
        return Map.of("injectedSecondCopyFailure",true,"copyAttempts",copyAttempts,"copiesCompleted",copiesCompleted,"releasedPartialBuffers",partialBuffers,"releasedPartialArrays",partialArrays,
                "globalRegistry",GpuModelAuxiliaries.size(),"mainJobCountPreserved",jobsAtFault,"actualMainDrawsAfterFailure",nativeDraws-drawsBeforeFault,"finalGlError",error);
    }
    private static void collectMain(ModernArmorAuxiliaryAccess api,Object buffers)throws Throwable{
        Object vertex=api.vertexBuffer.get(buffers),instances=api.instanceBuffer.get(buffers);if(vertex!=null)mainBuffers.add(((GlMutableBuffer)vertex).handle());if(instances!=null)mainBuffers.add(((GlMutableBuffer)instances).handle());
        Object tess=value(buffers,"tessellation");if(tess!=null)mainArrays.add(array(buffers));
    }
    private static int array(Object buffers)throws Throwable{Object array=value(value(buffers,"tessellation"),"array");return ((Number)array.getClass().getMethod("handle").invoke(array)).intValue();}
    private static Object bufferField(Object geometry)throws Throwable{for(Field field:geometry.getClass().getDeclaredFields())if(field.getType().getName().equals("dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer")){field.setAccessible(true);return field.get(geometry);}return null;}
    private static boolean equalBuffers(int source,int target,long bytes){
        require(bytes>0&&bytes<=64*1024,"Single armor source exceeds the bounded native probe");int old=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        ByteBuffer a=BufferUtils.createByteBuffer((int)bytes),b=BufferUtils.createByteBuffer((int)bytes);
        try{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,source);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,a);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,target);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,b);}
        finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,old);}return a.equals(b);
    }
    private static Object staticValue(Class<?> type,String name)throws Throwable{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
    private static Object value(Object object,String name)throws Throwable{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static boolean disjoint(Set<Integer>a,Set<Integer>b){for(int id:a)if(b.contains(id))return false;return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
