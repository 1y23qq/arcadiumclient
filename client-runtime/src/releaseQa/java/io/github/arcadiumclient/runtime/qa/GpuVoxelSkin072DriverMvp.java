// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072DriverMvp {
    private static final int LIMIT=64;
    private static final String SOURCE="#version 130\nout vec4 tfMvp0;out vec4 tfMvp1;out vec4 tfMvp2;out vec4 tfMvp3;\n"
            +"void main(){tfMvp0=gl_ModelViewProjectionMatrix[0];tfMvp1=gl_ModelViewProjectionMatrix[1];"
            +"tfMvp2=gl_ModelViewProjectionMatrix[2];tfMvp3=gl_ModelViewProjectionMatrix[3];gl_Position=vec4(0.0,0.0,0.0,1.0);}\n";
    private static final Map<Key,float[]> cache=new LinkedHashMap<>();
    private static final List<Map<String,Object>> samples=new ArrayList<>();
    private static GLCapabilities owner;
    private static int program,vao,feedback,buffer;
    private static long hits,captures,restores;
    private static boolean deleted;
    private GpuVoxelSkin072DriverMvp(){}
    private static final class Key {
        final int[] bits=new int[32];final int hash;
        Key(float[] eye,float[] projection){
            if(eye.length!=16||projection.length!=16)throw new IllegalArgumentException("Invalid physical matrix size");
            for(int i=0;i<16;i++){if(!Float.isFinite(eye[i])||!Float.isFinite(projection[i]))throw new IllegalArgumentException("Non-finite physical matrix");bits[i]=Float.floatToRawIntBits(eye[i]);bits[16+i]=Float.floatToRawIntBits(projection[i]);}
            hash=Arrays.hashCode(bits);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Key key&&Arrays.equals(bits,key.bits);}
    }
    private record State(int program,int vao,int array,int feedback,int genericFeedback,int copyRead,int indexedFeedback,
                         long feedbackStart,long feedbackSize,boolean discard,int mode,int activeTexture,int readFbo,int drawFbo){}
    public static float[] capture(float[] eye,float[] projection){
        GLCapabilities current=GL.getCapabilities();
        if(!current.OpenGL43||(GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT)==0)
            throw new IllegalStateException("Driver MVP capture requires the actual compatibilityGL4.3 context");
        if(GL11.glGetInteger(GL11.GL_RENDER_MODE)!=GL11.GL_RENDER)throw new IllegalStateException("The driver MVP probe requires ordinary rendering mode");
        if(owner==null)owner=current;else if(owner!=current)throw new IllegalStateException("Driver MVP capture crossed OpenGL contexts");
        rejectFeedback();Key key=new Key(eye,projection);float[] known=cache.get(key);if(known!=null){hits++;return known;}
        rejectQueries();
        if(cache.size()==LIMIT)throw new IllegalStateException("The static visual scene exceeded64distinct physical matrix keys");
        State before=state();boolean begun=false;float[] values=new float[16];
        try{
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("GL was not clean before driver MVP capture");
            ready();GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL40.glBindTransformFeedback(GL40.GL_TRANSFORM_FEEDBACK,feedback);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,buffer);GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,64L,GL15.GL_STREAM_READ);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,buffer);GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBeginTransformFeedback(GL11.GL_POINTS);begun=true;GL11.glDrawArrays(GL11.GL_POINTS,0,1);GL30.glEndTransformFeedback();begun=false;
            GL42.glMemoryBarrier(GL42.GL_TRANSFORM_FEEDBACK_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            FloatBuffer result=BufferUtils.createFloatBuffer(16);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0L,result);result.get(values);
            for(float value:values)if(!Float.isFinite(value))throw new IllegalStateException("Driver MVP readout returned non-finite values");
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Driver MVP capture produced a GL error");
        }finally{
            if(begun)GL30.glEndTransformFeedback();
            GL40.glBindTransformFeedback(GL40.GL_TRANSFORM_FEEDBACK,before.feedback);GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,before.genericFeedback);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,before.copyRead);GL30.glBindVertexArray(before.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,before.array);
            GL20.glUseProgram(before.program);if(before.discard)GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);else GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            State after=state();if(!before.equals(after)||GL11.glGetBoolean(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE)||GL11.glGetError()!=GL11.GL_NO_ERROR)
                throw new IllegalStateException("Driver MVP capture did not restore program/VAO/TFO/indexed/generic/raster state");
            restores++;
        }
        cache.put(key,values);captures++;samples.add(Map.of("physicalModelView",eye.clone(),"physicalProjection",projection.clone(),"actualDriverMvp",values.clone(),"allTrackedStateRestored",true));return values;
    }
    private static State state(){
        int indexed=GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,0);
        return new State(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL40.GL_TRANSFORM_FEEDBACK_BINDING),GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING),GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER),indexed,
                indexed==0?0:GL32.glGetInteger64i(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_START,0),indexed==0?0:GL32.glGetInteger64i(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_SIZE,0),
                GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),GL11.glGetInteger(GL11.GL_MATRIX_MODE),GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
    }
    private static void rejectFeedback(){
        if(GL11.glGetBoolean(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE)||GL11.glGetBoolean(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_PAUSED))throw new IllegalStateException("An existing transform-feedback operation is active");
    }
    private static void rejectQueries(){
        int streams=GL11.glGetInteger(GL40.GL_MAX_VERTEX_STREAMS);if(streams<1||streams>32)throw new IllegalStateException("Unknown vertex stream limit");
        for(int stream=0;stream<streams;stream++)for(int target:new int[]{GL30.GL_PRIMITIVES_GENERATED,GL30.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN})
            if(GL40.glGetQueryIndexedi(target,stream,GL15.GL_CURRENT_QUERY)!=0)throw new IllegalStateException("A vertex/transform-feedback query is active");
        for(int target:new int[]{GL15.GL_SAMPLES_PASSED,GL33.GL_ANY_SAMPLES_PASSED,GL43.GL_ANY_SAMPLES_PASSED_CONSERVATIVE,GL33.GL_TIME_ELAPSED})
            if(GL15.glGetQueryi(target,GL15.GL_CURRENT_QUERY)!=0)throw new IllegalStateException("A raster/timing query is active");
        if(GL.getCapabilities().OpenGL46||GL.getCapabilities().GL_ARB_pipeline_statistics_query)for(int target:new int[]{0x82EE,0x82EF,0x82F0,0x82F1,0x82F2,0x82F3,0x82F4,0x82F5,0x82F6,0x82F7,0x887F})
            if(GL15.glGetQueryi(target,GL15.GL_CURRENT_QUERY)!=0)throw new IllegalStateException("A pipeline statistics query is active");
        if(GL.getCapabilities().OpenGL46||GL.getCapabilities().GL_ARB_transform_feedback_overflow_query){
            if(GL15.glGetQueryi(0x82EC,GL15.GL_CURRENT_QUERY)!=0)throw new IllegalStateException("A transform-feedback overflow query is active");
            for(int stream=0;stream<streams;stream++)if(GL40.glGetQueryIndexedi(0x82ED,stream,GL15.GL_CURRENT_QUERY)!=0)throw new IllegalStateException("A transform-feedback stream overflow query is active");
        }
    }
    private static void ready(){
        if(program!=0)return;
        if(GL11.glGetInteger(GL30.GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS)<16||GL11.glGetInteger(GL40.GL_MAX_TRANSFORM_FEEDBACK_BUFFERS)<1)
            throw new IllegalStateException("Insufficient transform-feedback limits");
        int shader=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),candidate=GL20.glCreateProgram(),createdVao=0,createdFeedback=0,createdBuffer=0;
        try{
            GL20.glShaderSource(shader,SOURCE);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate,shader);GL30.glTransformFeedbackVaryings(candidate,new String[]{"tfMvp0","tfMvp1","tfMvp2","tfMvp3"},GL30.GL_INTERLEAVED_ATTRIBS);GL20.glLinkProgram(candidate);
            if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            createdVao=GL30.glGenVertexArrays();createdFeedback=GL40.glGenTransformFeedbacks();createdBuffer=GL15.glGenBuffers();
            if(createdVao==0||createdFeedback==0||createdBuffer==0)throw new IllegalStateException("Cannot allocate bounded driver MVP probe objects");
            program=candidate;vao=createdVao;feedback=createdFeedback;buffer=createdBuffer;
        }catch(Throwable failure){if(createdVao!=0)GL30.glDeleteVertexArrays(createdVao);if(createdFeedback!=0)GL40.glDeleteTransformFeedbacks(createdFeedback);if(createdBuffer!=0)GL15.glDeleteBuffers(createdBuffer);GL20.glDeleteProgram(candidate);throw failure;}
        finally{GL20.glDeleteShader(shader);}
    }
    public static void stop(){
        if(owner==null){deleted=true;return;}
        if(GL.getCapabilities()!=owner)throw new IllegalStateException("Driver MVP cleanup crossed OpenGL contexts");
        if(GL11.glGetBoolean(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE))throw new IllegalStateException("Transform feedback remained active at driver MVP cleanup");
        if(program!=0&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program||vao!=0&&GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==vao
                ||feedback!=0&&GL11.glGetInteger(GL40.GL_TRANSFORM_FEEDBACK_BINDING)==feedback)throw new IllegalStateException("A driver MVP probe object remained bound at cleanup");
        int oldProgram=program,oldVao=vao,oldFeedback=feedback,oldBuffer=buffer;
        if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(feedback!=0)GL40.glDeleteTransformFeedbacks(feedback);if(buffer!=0)GL15.glDeleteBuffers(buffer);
        deleted=(oldProgram==0||!GL20.glIsProgram(oldProgram))&&(oldVao==0||!GL30.glIsVertexArray(oldVao))&&(oldFeedback==0||!GL40.glIsTransformFeedback(oldFeedback))&&(oldBuffer==0||!GL15.glIsBuffer(oldBuffer));
        program=vao=feedback=buffer=0;if(!deleted||GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Driver MVP GPU objects were not deleted cleanly");
    }
    public static Map<String,Object> report(){
        Map<String,Object> result=new LinkedHashMap<>();result.put("correctnessOnly",true);result.put("notPerformanceEvidence",true);result.put("distinctKeys",cache.size());result.put("maximumKeys",LIMIT);result.put("gpuReadbacks",captures);result.put("cacheHits",hits);result.put("restoredCaptureScopes",restores);result.put("allOwnGlObjectsDeleted",deleted);result.put("samples",samples);
        result.put("shaderSourceSha256",GpuVoxelSkin072EyeShader.hash(SOURCE));result.put("passed",captures>0&&captures==restores&&deleted&&cache.size()<=LIMIT);return result;
    }
}
