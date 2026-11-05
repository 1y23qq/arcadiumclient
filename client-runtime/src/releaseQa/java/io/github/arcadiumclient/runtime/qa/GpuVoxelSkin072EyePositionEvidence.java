// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072EyePositionEvidence {
    private static final int CAPACITY=GpuVoxelSkin072EyeWords.CAPACITY,BINDING=8;
    private static final String MAIN_SHA="7163f447bb9fcf3bd35f6edf0b9bff02193a8ad1cada7fa7ddd007f9c5817b40";
    private static final String PREFIX="io.github.arcadiumclient.runtime.client.optimizations.";
    private static final float[] matrices=new float[CAPACITY*16];
    private static final FloatBuffer upload=BufferUtils.createFloatBuffer(CAPACITY*16),query=BufferUtils.createFloatBuffer(16);
    private static final Map<String,String> shaderSources=new LinkedHashMap<>();
    private static final Map<Integer,Integer> vertexComponents=new LinkedHashMap<>();
    private static final List<Map<String,Object>> samples=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static int count,current=-1,peakBytes;
    private static long epoch,captured,submitted,timeAssignments,drawCalls,instances,restores;
    private static boolean pinned,hooked,stopped;
    private GpuVoxelSkin072EyePositionEvidence(){}
    public static boolean enabled(){return System.getProperty("arcadium.voxelQuality072.eyePosition")!=null;}
    public static int mode(){return switch(System.getProperty("arcadium.voxelQuality072.eyePosition","")){
        case "control"->0;case "plain"->1;case "precise"->2;default->throw new IllegalStateException("Unknown eye-position correctness mode");};}
    private static void context(){
        if(!enabled()||!Boolean.getBoolean("arcadium.playerPipeline072.nativeVoxelRootControl")||!PlayerPipeline072Qa.enabled()
                ||!System.getProperty("arcadium.playerPipeline072.mode","").equals("visual")
                ||Integer.getInteger("arcadium.playerPipeline072.players",0)!=3
                ||Boolean.getBoolean("arcadium.playerPipeline072.nativeBodyControl")||Boolean.getBoolean("arcadium.voxelQuality072.captureOnly"))
            throw new IllegalStateException("Eye-position diagnostics require the exclusive original-root fake visual fixture");
        var session=Minecraft.getInstance().getSession();
        if(!session.getUsername().equals("ArcadiumTest")||!session.getAccessToken().equals("0"))throw new IllegalStateException("Eye-position diagnostics require the owned offline account");
        mode();hook();
    }
    private static void pin()throws Exception{
        if(pinned)return;
        Class<?> source=Class.forName(PREFIX+"GpuVoxelSkinPipeline",false,GpuVoxelSkin072EyePositionEvidence.class.getClassLoader());
        Path file=Path.of(source.getProtectionDomain().getCodeSource().getLocation().toURI());
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file)){byte[] bytes=new byte[16384];int length;while((length=input.read(bytes))!=-1)digest.update(bytes,0,length);}
        if(!MAIN_SHA.equals(HexFormat.of().formatHex(digest.digest())))throw new IllegalStateException("Eye-position diagnostics require the exact pre-admission7163 main");
        pinned=true;
    }
    public static String shader(String name,String source){
        if(!enabled()||!name.equals("argentum:entity_instancing.vert")&&!name.equals("argentum:entity_instancing.frag"))return source;
        context();
        if(!GL.getCapabilities().OpenGL43||GL11.glGetInteger(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS)<CAPACITY*16)
            throw new IllegalStateException("The eye-position shader needs compatibilityGL4.3 and64vertexmatrices");
        String patched=GpuVoxelSkin072EyeShader.patch(name,source);shaderSources.put(name,GpuVoxelSkin072EyeShader.hash(patched));return patched;
    }
    public static void beginBatch(){if(enabled()){context();if(current!=-1)throw new IllegalStateException("An eye-position capture escaped its native callback");count=0;epoch++;}}
    public record Capture(int previous,int index){}
    public static Capture beginCapture(Object mesh,float scale){
        if(!enabled())return null;
        try{
            context();if(((Number)field(null,GpuVoxelSkin072QualityControls.class,"nativeRootActorDepth")).intValue()==0)return null;pin();
            if(Float.floatToRawIntBits(scale)!=Float.floatToRawIntBits(.0625f)||GL11.glGetInteger(GL11.GL_MATRIX_MODE)!=GL11.GL_MODELVIEW
                    ||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0||count>=CAPACITY)throw new IllegalStateException("Unsupported physical native voxel matrix capture");
            if(!mesh.getClass().getName().equals("org.polyfrost.skinlayers.render.CompiledMesh"))throw new IllegalStateException("Unknown native voxel mesh");
            query.clear();GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,query);int index=count++;
            for(int i=0;i<16;i++){float v=query.get(i);if(!Float.isFinite(v))throw new IllegalStateException("Non-finite native modelview");matrices[index*16+i]=v;}
            if(matrices[index*16+3]!=0||matrices[index*16+7]!=0||matrices[index*16+11]!=0||matrices[index*16+15]!=1)throw new IllegalStateException("The physical modelview is not affine");
            Capture token=new Capture(current,index);current=index;captured++;return token;
        }catch(Throwable failure){throw failed(failure);}
    }
    public static void endCapture(Capture token,boolean accepted){
        if(token==null)return;if(current!=token.index)throw failed(new IllegalStateException("Native eye-position capture order changed"));
        if(accepted)submitted++;current=token.previous;
    }
    public static float effectTime(float original){
        if(!enabled()||current<0)return original;
        if(current>=count)throw failed(new IllegalStateException("Native eye matrix index is outside this batch"));
        timeAssignments++;return GpuVoxelSkin072EyeWords.encode(current,count);
    }
    public record Draw(int program,int modeLocation,int countLocation,int oldMode,int oldCount,int matrixLocation,FloatBuffer oldMatrices,
                       int oldGeneric,int oldIndexed,long oldStart,long oldSize,int instances,long epoch,int matrices){}
    public static Draw beforeDraw(Object draw,IntBuffer payload,int instanceCount){
        if(!enabled())return null;
        try{
            context();pin();if(current!=-1||count==0||count>CAPACITY||instanceCount<1||instanceCount>CAPACITY||payload.remaining()!=instanceCount*19)
                throw new IllegalStateException("Invalid current native eye-position instance batch");
            Object renderer=field(null,Class.forName(PREFIX+"GpuVoxelSkinPipeline"),"backend");boolean owned=false;
            for(Object storage:((Map<?,?>)field(renderer,"owners")).values())if(field(storage,"draw")==draw&&field(storage,"auxiliary")!=null&&!((Boolean)field(storage,"closed"))){owned=true;break;}
            if(!owned)throw new IllegalStateException("Eye-position override reached a non-owned auxiliary geometry");
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),modeLocation=GL20.glGetUniformLocation(program,"uArcVoxelEyeMode"),countLocation=GL20.glGetUniformLocation(program,"uArcVoxelEyeCount");
            if(shaderSources.size()!=2||modeLocation<0||countLocation<0||uniform(program,"uAlphaPass")!=0||uniform(program,"uGlintPass")>=0
                    ||uniform(program,"uItemGlintPass")>=0||uniform(program,"uChargePass")==1||uniform(program,"uChargePass")==2)
                throw new IllegalStateException("Eye-position override requires the known uncharged NORMAL native program");
            validateUniformBudget(program);
            validateGpuIndices(program,payload,instanceCount);
            int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING),indexed=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,BINDING);
            long start=indexed==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,BINDING),size=indexed==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,BINDING);
            int matrixLocation=GL20.glGetUniformLocation(program,"uArcVoxelEyeMatrices[0]");if(matrixLocation<0)throw new IllegalStateException("Native eye matrix array is unavailable");
            FloatBuffer oldMatrices=BufferUtils.createFloatBuffer(count*16);
            for(int i=0;i<count;i++){
                int location=GL20.glGetUniformLocation(program,"uArcVoxelEyeMatrices["+i+"]");if(location<0)throw new IllegalStateException("Unknown native eye uniform array extent");
                query.clear();GL20.glGetUniformfv(program,location,query);oldMatrices.put(query);
            }
            oldMatrices.flip();
            Draw token=new Draw(program,modeLocation,countLocation,GL20.glGetUniformi(program,modeLocation),GL20.glGetUniformi(program,countLocation),matrixLocation,oldMatrices,generic,indexed,start,size,instanceCount,epoch,count);
            try{
                if(token.oldMode!=0||token.oldCount!=0)throw new IllegalStateException("The native eye override was already active outside its owned draw");
                upload.clear();upload.put(matrices,0,count*16).flip();GL20.glUniformMatrix4fv(matrixLocation,false,upload);
                GL20.glUniform1i(countLocation,count);GL20.glUniform1i(modeLocation,mode());peakBytes=Math.max(peakBytes,count*64);
                if(GL20.glGetUniformi(program,modeLocation)!=mode()||GL20.glGetUniformi(program,countLocation)!=count)throw new IllegalStateException("Native eye override uniform assignment failed");
                return token;
            }catch(Throwable failure){restore(token);throw failure;}
        }catch(Throwable failure){throw failed(failure);}
    }
    private static void validateUniformBudget(int program){
        if(vertexComponents.containsKey(program))return;
        int resources=GL43.glGetProgramInterfacei(program,GL43.GL_UNIFORM,GL43.GL_ACTIVE_RESOURCES),components=0;
        if(resources<1||resources>512||vertexComponents.size()>=8)throw new IllegalStateException("Unknown or excessive native uniform interface");
        IntBuffer properties=BufferUtils.createIntBuffer(4).put(new int[]{GL43.GL_TYPE,GL43.GL_ARRAY_SIZE,GL43.GL_REFERENCED_BY_VERTEX_SHADER,GL43.GL_BLOCK_INDEX});properties.flip();
        IntBuffer values=BufferUtils.createIntBuffer(4);
        for(int index=0;index<resources;index++){
            values.clear();GL43.glGetProgramResourceiv(program,GL43.GL_UNIFORM,index,properties,null,values);
            if(values.get(2)==0||values.get(3)!=-1)continue;
            int size=values.get(1);if(size<1||size>4096)throw new IllegalStateException("Unknown native vertex uniform extent");
            int width=switch(values.get(0)){
                case GL11.GL_FLOAT,GL11.GL_INT,GL11.GL_UNSIGNED_INT,GL20.GL_BOOL,GL20.GL_SAMPLER_2D,GL20.GL_SAMPLER_CUBE,GL30.GL_SAMPLER_2D_ARRAY->1;
                case GL20.GL_FLOAT_VEC2,GL20.GL_INT_VEC2,GL20.GL_BOOL_VEC2,GL30.GL_UNSIGNED_INT_VEC2->2;
                case GL20.GL_FLOAT_VEC3,GL20.GL_INT_VEC3,GL20.GL_BOOL_VEC3,GL30.GL_UNSIGNED_INT_VEC3->3;
                case GL20.GL_FLOAT_VEC4,GL20.GL_INT_VEC4,GL20.GL_BOOL_VEC4,GL30.GL_UNSIGNED_INT_VEC4,GL20.GL_FLOAT_MAT2->4;
                case GL21.GL_FLOAT_MAT2x3,GL21.GL_FLOAT_MAT3x2->6;
                case GL21.GL_FLOAT_MAT2x4,GL21.GL_FLOAT_MAT4x2->8;
                case GL20.GL_FLOAT_MAT3->9;
                case GL21.GL_FLOAT_MAT3x4,GL21.GL_FLOAT_MAT4x3->12;
                case GL20.GL_FLOAT_MAT4->16;
                default->throw new IllegalStateException("Unknown native vertex uniform type"+values.get(0));
            };
            components=Math.addExact(components,Math.multiplyExact(size,width));
        }
        if(components<CAPACITY*16||components>GL11.glGetInteger(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS))throw new IllegalStateException("Actual native vertex uniforms exceed the available component budget");
        vertexComponents.put(program,components);
    }
    private static void validateGpuIndices(int program,IntBuffer payload,int instances){
        int attribute=GL20.glGetAttribLocation(program,"aModel0");
        if(attribute<0||GL20.glGetVertexAttribi(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE)!=76||GL20.glGetVertexAttribi(attribute,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)!=1)
            throw new IllegalStateException("Unknown native instance stride for eye indices");
        int nativeBuffer=GL20.glGetVertexAttribi(attribute,GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING),old=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        PointerBuffer pointer=BufferUtils.createPointerBuffer(1);GL20.glGetVertexAttribPointerv(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER,pointer);long offset=pointer.get(0);
        int time=GL20.glGetAttribLocation(program,"aEffectTime");
        if(time<0||GL20.glGetVertexAttribi(time,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE)!=76||GL20.glGetVertexAttribi(time,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)!=1
                ||GL20.glGetVertexAttribi(time,GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING)!=nativeBuffer)throw new IllegalStateException("Unknown native effect-time attribute");
        pointer.clear();GL20.glGetVertexAttribPointerv(time,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER,pointer);if(pointer.get(0)!=offset+60)throw new IllegalStateException("Native effect time is not the verified instanceword15");
        ByteBuffer raw=BufferUtils.createByteBuffer(instances*76);
        try{
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,nativeBuffer);long bytes=GL32.glGetBufferParameteri64(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE);
            if(offset<0||offset>bytes-(long)instances*76)throw new IllegalStateException("Native eye instance range exceeds its GPU buffer");
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT|GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,offset,raw);
            IntBuffer gpu=raw.asIntBuffer();
            for(int i=0;i<instances;i++){
                for(int component=0;component<12;component++){float value=Float.intBitsToFloat(gpu.get(i*19+component));if(!Float.isFinite(value)||Math.abs(value)>1024)throw new IllegalStateException("The actual GPU voxel transform is unresolved or outside the owned fixture");}
                int word=gpu.get(i*19+15);GpuVoxelSkin072EyeWords.decode(word,count);
                if(word!=payload.get(payload.position()+i*19+15))
                    throw new IllegalStateException("The actual post-transform GPU voxel has an invalid or reordered eye index");
            }
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,old);}
    }
    public static void drew(Draw token){
        if(token==null)return;drawCalls++;instances+=token.instances;
        if(samples.size()<16){Map<String,Object> record=new LinkedHashMap<>();record.put("epoch",token.epoch);record.put("actualProgram",token.program);record.put("mode",mode());record.put("matrices",token.matrices);record.put("instances",token.instances);record.put("vertexUniformComponents",vertexComponents.get(token.program));record.put("firstPhysicalModelView",Arrays.copyOf(matrices,16));samples.add(record);}
    }
    public static void restore(Draw token){
        if(token==null)return;
        if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=token.program)GL20.glUseProgram(token.program);
        GL20.glUniformMatrix4fv(token.matrixLocation,false,token.oldMatrices);GL20.glUniform1i(token.modeLocation,token.oldMode);GL20.glUniform1i(token.countLocation,token.oldCount);
        boolean restored=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==token.program&&GL20.glGetUniformi(token.program,token.modeLocation)==token.oldMode
                &&GL20.glGetUniformi(token.program,token.countLocation)==token.oldCount&&GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)==token.oldGeneric
                &&GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,BINDING)==token.oldIndexed;
        if(token.oldIndexed!=0&&token.oldSize!=0)restored&=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,BINDING)==token.oldStart&&GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,BINDING)==token.oldSize;
        for(int i=0;i<token.matrices;i++){
            int location=GL20.glGetUniformLocation(token.program,"uArcVoxelEyeMatrices["+i+"]");query.clear();GL20.glGetUniformfv(token.program,location,query);
            for(int component=0;component<16;component++)restored&=Float.floatToRawIntBits(query.get(component))==Float.floatToRawIntBits(token.oldMatrices.get(i*16+component));
        }
        int error=GL11.glGetError();if(!restored||error!=0)throw failed(new IllegalStateException("Native eye override leaked uniforms/buffer range or produced GLerror"+error));restores++;
    }
    public static void stop(){
        if(enabled())stopped=true;
    }
    private static int uniform(int program,String name){int location=GL20.glGetUniformLocation(program,name);if(location<0)throw new IllegalStateException("Missing normal-pass uniform "+name);return GL20.glGetUniformi(program,location);}
    private static Object field(Object target,String name)throws Exception{return field(target,target.getClass(),name);}
    private static Object field(Object target,Class<?> type,String name)throws Exception{
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static IllegalStateException failed(Throwable failure){if(failures.size()<32)failures.add(failure.toString());return new IllegalStateException("Eye-position correctness diagnostic failed",failure);}
    private static void hook(){
        if(hooked)return;hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try{
                Map<String,Object> report=new LinkedHashMap<>();report.put("correctnessOnly",true);report.put("notPerformanceEvidence",true);report.put("mode",System.getProperty("arcadium.voxelQuality072.eyePosition"));report.put("pinnedPreAdmissionMain",pinned);report.put("shaderSources",shaderSources);
                report.put("capturedMatrices",captured);report.put("submittedCaptures",submitted);report.put("negativeTimeAssignments",timeAssignments);report.put("drawCalls",drawCalls);report.put("actualInstances",instances);report.put("restoredDrawScopes",restores);report.put("currentCaptureIndex",current);report.put("peakUniformBytes",peakBytes);report.put("boundedUniformBytes",CAPACITY*64);report.put("noNewGpuBuffers",true);report.put("gpuReadbacksCorrectnessOnly",true);report.put("observedClientStop",stopped);report.put("vertexUniformComponents",vertexComponents);report.put("samples",samples);report.put("failures",failures);
                report.put("passed",pinned&&shaderSources.size()==2&&submitted>0&&timeAssignments>=submitted&&drawCalls>0&&restores==drawCalls&&current==-1&&stopped&&failures.isEmpty());
                Path output=Path.of(System.getProperty("arcadium.playerPipeline072.output")).resolveSibling("voxel-eye-position.json");Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            }catch(Throwable failure){failure.printStackTrace();}
        },"Arcadium voxel eye-position correctness"));
    }
}
