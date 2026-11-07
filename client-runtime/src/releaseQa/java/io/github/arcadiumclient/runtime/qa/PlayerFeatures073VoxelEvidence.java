// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class PlayerFeatures073VoxelEvidence {
    private static String mode="off",failure;
    private static long readyBypasses,draws;
    private static final List<Map<String,Object>> submissions=new ArrayList<>(),uploads=new ArrayList<>(),commands=new ArrayList<>();
    private static final FloatBuffer scratch=BufferUtils.createFloatBuffer(16);
    private PlayerFeatures073VoxelEvidence(){}
    public static void begin(String next){if(!mode.equals("off"))throw new IllegalStateException("Nested voxel diagnosis");mode=next;failure=null;readyBypasses=draws=0;submissions.clear();uploads.clear();commands.clear();}
    public static Map<String,Object> end(){Map<String,Object> result=new LinkedHashMap<>();result.put("mode",mode);result.put("readyBypasses",readyBypasses);result.put("actualMdiCalls",draws);result.put("mdiArguments",new ArrayList<>(commands));result.put("submissions",new ArrayList<>(submissions));result.put("cpuUploadInputs",new ArrayList<>(uploads));result.put("failure",failure);result.put("gpuBufferReadbacks",false);mode="off";return result;}
    public static boolean affine(){if(mode.equals("voxel-affine")){readyBypasses++;return true;}return false;}
    public static int submit(Object storage,Object geometry,Object texture,Object pass,Matrix4f submitted,int light,Vector4fc color,float time,Vector4fc overlay,Object capture){
        if(mode.equals("off"))return -1;if(submissions.size()==32){failed(new IllegalStateException("Diagnostic submission budget exceeded"));return -1;}
        try{
            Map<String,Object> record=new LinkedHashMap<>();record.put("matrixArgument",matrix(submitted));record.put("marker",marker(submitted));record.put("physicalModelViewAtSubmitEntry",physical(GL11.GL_MODELVIEW_MATRIX));record.put("projectionAtSubmitEntry",physical(GL11.GL_PROJECTION_MATRIX));record.put("texture",texture.toString());record.put("pass",((Enum<?>)pass).name());record.put("light",light);record.put("color",vector(color));record.put("time",time);record.put("overlay",vector(overlay));
            if(capture!=null)record.put("captureMatrixAtSubmitEntry",matrix((Matrix4fc)field(capture,"matrices")));
            record.put("firstVertex",geometry.getClass().getMethod("firstVertex").invoke(geometry));record.put("vertexCount",geometry.getClass().getMethod("vertexCount").invoke(geometry));Object mesh=field(geometry,"mesh");Class<?> bridge=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinBridge");Method part=bridge.getDeclaredMethod("part",Object.class);part.setAccessible(true);record.put("observedMeshPart",part.invoke(null,mesh));record.put("scope","Submission entry, not an actual fixed-function native draw oracle");submissions.add(record);return submissions.size()-1;
        }catch(Throwable error){failed(error);}
        return -1;
    }
    public static void submitted(int token,boolean accepted){if(token>=0&&token<submissions.size())submissions.get(token).put("accepted",accepted);}
    public static void upload(Object draw,IntBuffer payload,int count){
        if(mode.equals("off"))return;if(uploads.size()==8){failed(new IllegalStateException("Diagnostic upload budget exceeded"));return;}
        try{
            Map<String,Object> record=new LinkedHashMap<>();record.put("count",count);record.put("instanceWords",copyPayload(payload,count));record.put("position",payload.position());record.put("limit",payload.limit());record.put("currentProgram",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            Object cache=field(draw,"cache");int size=(int)cache.getClass().getMethod("sizeWords").invoke(cache);if(size<0||size>16384)throw new IllegalStateException("Diagnostic voxel atlas word budget exceeded");int[] words=(int[])cache.getClass().getMethod("words").invoke(cache);record.put("vertexWords",Arrays.copyOf(words,size));record.put("vertexWordStride",9);record.put("atlasGeneration",cache.getClass().getMethod("generation").invoke(cache));record.put("payloadStage","CPU input before original native upload/pose expansion");uploads.add(record);
        }catch(Throwable error){failed(error);}
    }
    public static void mdi(int primitive,long offset,int count,int stride){if(!mode.equals("off")){draws++;if(commands.size()<8)commands.add(Map.of("primitive",primitive,"offset",offset,"count",count,"stride",stride));else failed(new IllegalStateException("Diagnostic command budget exceeded"));}}
    static int[] copyPayload(IntBuffer payload,int count){if(count<0||count>32||payload.remaining()!=Math.multiplyExact(count,19))throw new IllegalArgumentException("Unknown bounded native instance payload");IntBuffer read=payload.duplicate();int[] words=new int[read.remaining()];read.get(words);return words;}
    private static boolean marker(Matrix4fc matrix){return Float.floatToRawIntBits(matrix.m01())==Float.floatToRawIntBits(-9876543f)&&Float.floatToRawIntBits(matrix.m02())==Float.floatToRawIntBits(-8765432f);}
    private static Map<String,Object> matrix(Matrix4fc matrix){float[] values=new float[16];matrix.get(values);int[] bits=new int[16];for(int i=0;i<16;i++){if(!Float.isFinite(values[i]))throw new IllegalStateException("Nonfinite diagnostic matrix");bits[i]=Float.floatToRawIntBits(values[i]);}return Map.of("values",values,"rawBits",bits);}
    private static Map<String,Object> physical(int type){scratch.clear();GL11.glGetFloatv(type,scratch);float[] values=new float[16];scratch.get(values);return matrix(new Matrix4f().set(values));}
    private static float[] vector(Vector4fc value){return new float[]{value.x(),value.y(),value.z(),value.w()};}
    private static Object field(Object object,String name)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private static void failed(Throwable error){if(failure==null)failure=error.toString();}
}
