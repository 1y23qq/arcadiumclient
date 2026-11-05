// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class GpuResidentBodyDraw {
    private static final String[] ATTRIBUTES={"aPosition","aTexCoord","aNormal","aModel0","aModel1","aModel2","aModel3","aLightCoord","aColor","aVertexColor","aEffectTime","aOverlay","aBoxTexture","aBoxSize"};
    private static int poseProgram,actorUniform;
    private final FloatBuffer vertices;
    private final HashMap<Integer,Integer> arrays=new HashMap<>();
    private final int[] previousBindings=new int[7];
    private final long[] previousStarts=new long[7],previousSizes=new long[7];
    private final IntBuffer fallback=BufferUtils.createIntBuffer(128*19);
    private final int[] firstVertices=new int[128];
    private final Matrix4f matrix=new Matrix4f();
    private int vertexBuffer,instanceBuffer,bones,capacity=1;
    GpuResidentBodyDraw(FloatBuffer vertices){if(!vertices.isDirect()||vertices.remaining()!=3456)throw new IllegalArgumentException("Use the verified direct body mesh");this.vertices=vertices;}

    static boolean ready() {
        if(poseProgram!=0)return true;if(!GpuResidentBodyShaderState.supported())return false;
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),candidate=GL20.glCreateProgram();boolean accepted=false;
        try {
            String source=read("resident_body_pose.comp");int line=source.indexOf('\n');source=source.substring(0,line+1)+read("player_pose.glsl")+source.substring(line+1);
            GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate,shader);GL20.glLinkProgram(candidate);if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            actorUniform=GL20.glGetUniformLocation(candidate,"actorCount");if(actorUniform<0)throw new IllegalStateException("Resident bone count uniform is absent");poseProgram=candidate;accepted=true;return true;
        } finally {GL20.glDeleteShader(shader);if(!accepted)GL20.glDeleteProgram(candidate);}
    }
    private static String read(String name){try(var stream=GpuResidentBodyDraw.class.getResourceAsStream("/assets/arcadium/shaders/"+name)){if(stream==null)throw new IllegalStateException("Missing resident body shader");return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}

    void prepare(int program) {
        if(arrays.containsKey(program))return;if(arrays.size()==4)throw new IllegalStateException("Resident body program budget reached");
        int[] locations=new int[ATTRIBUTES.length];for(int i=0;i<locations.length;i++){locations[i]=GL20.glGetAttribLocation(program,ATTRIBUTES[i]);if(locations[i]<0)throw new IllegalStateException("Native body attribute is absent: "+ATTRIBUTES[i]);}
        int oldArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),array=0;boolean accepted=false;
        try {
            if(vertexBuffer==0){vertexBuffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vertexBuffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices.duplicate(),GL15.GL_STATIC_DRAW);}
            if(instanceBuffer==0){instanceBuffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,76,GL15.GL_STREAM_DRAW);}
            array=GL30.glGenVertexArrays();GL30.glBindVertexArray(array);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vertexBuffer);
            pointer(locations[0],3,GL11.GL_FLOAT,false,48,0,0);pointer(locations[1],2,GL11.GL_FLOAT,false,48,12,0);pointer(locations[2],3,GL11.GL_FLOAT,false,48,20,0);pointer(locations[9],4,GL11.GL_FLOAT,false,48,32,0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);for(int i=0;i<4;i++)pointer(locations[3+i],3,GL11.GL_FLOAT,false,76,i*12L,1);
            pointer(locations[7],3,GL11.GL_UNSIGNED_BYTE,false,76,52,1);pointer(locations[8],4,GL11.GL_UNSIGNED_BYTE,true,76,56,1);pointer(locations[10],1,GL11.GL_FLOAT,false,76,48,1);pointer(locations[11],4,GL11.GL_UNSIGNED_BYTE,true,76,60,1);pointer(locations[12],4,GL11.GL_UNSIGNED_SHORT,false,76,64,1);pointer(locations[13],3,GL11.GL_UNSIGNED_BYTE,false,76,72,1);
            arrays.put(program,array);accepted=true;
        } finally {GL30.glBindVertexArray(oldArray);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);if(!accepted&&array!=0)GL30.glDeleteVertexArrays(array);}
    }
    private static void pointer(int location,int count,int type,boolean normalized,int stride,long offset,int divisor){GL20.glEnableVertexAttribArray(location);GL20.glVertexAttribPointer(location,count,type,normalized,stride,offset);GL33.glVertexAttribDivisor(location,divisor);}

    void draw(IntBuffer payload,int count,int uvBuffer,int profiles,int reservedCapacity) {
        if(count<1||count>8192||!payload.isDirect()||payload.order()!=ByteOrder.nativeOrder()||payload.remaining()!=count*19||reservedCapacity<count||reservedCapacity>8192)
            throw new IllegalStateException("Invalid resident body draw range");
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);prepare(program);
        int oldArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            capacity=Math.max(capacity,reservedCapacity);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,(long)capacity*76,GL15.GL_STREAM_DRAW);GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,0,payload);
            expand(program,count);int token=GpuResidentBodyShaderState.beginOwned(bones,uvBuffer,count,profiles);
            try {GL30.glBindVertexArray(arrays.get(program));GL31.glDrawArraysInstanced(GL11.GL_QUADS,0,288,count);GpuResidentBodies.drawCalls++;GpuResidentBodies.actorsDrawn+=count;}
            finally {GpuResidentBodyShaderState.end(token);}
        } finally {GL30.glBindVertexArray(oldArray);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);}
    }
    private void expand(int originalProgram,int count) {
        int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int i=0;i<7;i++){int binding=previousBindings[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);previousStarts[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);previousSizes[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        try {
            if(!ready())throw new IllegalStateException("Resident bone compute is unavailable");
            if(bones==0)bones=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,bones);
            long required=(long)capacity*12*12*4;if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)<required)GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,required,GL15.GL_DYNAMIC_DRAW);
            GpuPlayerPackets.bind();GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,bones);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,instanceBuffer);
            GL20.glUseProgram(poseProgram);GL30.glUniform1ui(actorUniform,count);GL43.glDispatchCompute((count*12+63)/64,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            if(GpuResidentPlayerState.enabled)GpuResidentPlayerState.submitted();GpuResidentBodies.poseDispatches++;
        } finally {
            GL20.glUseProgram(originalProgram);
            for(int i=0;i<7;i++){if(previousBindings[i]!=0&&previousSizes[i]>0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,previousBindings[i],previousStarts[i],previousSizes[i]);else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,previousBindings[i]);}
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);
        }
    }
    void replay(IntBuffer actors,int[] uv) {
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);prepare(program);
        int oldArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),used=0;
        try {
            GL30.glBindVertexArray(arrays.get(program));
            for(int actor=actors.position();actor<actors.limit();actor+=19){int token=actors.get(actor+16),job=token&65535,mask=token>>>16;
                for(int part=0;part<12;part++){if((mask&(1<<part))==0)continue;
                    GpuPlayerPackets.resolve(job*16+part,matrix);int at=used*19;for(int column=0;column<4;column++)for(int row=0;row<3;row++)fallback.put(at+column*3+row,Float.floatToRawIntBits(matrix.get(column,row)));
                    for(int word=12;word<16;word++)fallback.put(at+word,actors.get(actor+word));for(int word=0;word<3;word++)fallback.put(at+16+word,uv[part*3+word]);firstVertices[used]=part*24;used++;
                    if(used==Math.min(capacity,128)){replayChunk(used);used=0;}
                }
            }
            if(used!=0)replayChunk(used);
        } finally {fallback.clear();GL30.glBindVertexArray(oldArray);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);}
    }
    private void replayChunk(int count){fallback.position(0).limit(count*19);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,(long)capacity*76,GL15.GL_STREAM_DRAW);GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,0,fallback);for(int i=0;i<count;i++)GL42.glDrawArraysInstancedBaseInstance(GL11.GL_QUADS,firstVertices[i],24,1,i);GpuResidentBodies.replayedParts+=count;fallback.clear();}
    void close(){for(int array:arrays.values())GL30.glDeleteVertexArrays(array);arrays.clear();if(vertexBuffer!=0)GL15.glDeleteBuffers(vertexBuffer);if(instanceBuffer!=0)GL15.glDeleteBuffers(instanceBuffer);if(bones!=0)GL15.glDeleteBuffers(bones);vertexBuffer=instanceBuffer=bones=0;}
    static void closeProgram(){if(poseProgram!=0)GL20.glDeleteProgram(poseProgram);poseProgram=0;}
}
