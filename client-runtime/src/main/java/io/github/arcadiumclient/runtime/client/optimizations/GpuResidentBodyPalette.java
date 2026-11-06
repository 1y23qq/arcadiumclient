// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class GpuResidentBodyPalette {
    static final boolean ENABLED=Boolean.getBoolean("arcadium.residentBodies072.sharedPalette");
    static final long MAX_BONE_BYTES=8192L*12*12*4;
    private static int program,countUniform,rowUniform;
    final GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();
    private final Object owner;
    private final int[] previousBindings=new int[7];
    private final long[] previousStarts=new long[7],previousSizes=new long[7];
    private int[] uploaded=new int[16];
    private IntBuffer staging=BufferUtils.createIntBuffer(16);
    private int bones,input,capacity,uploadedCount;
    private long epoch,players,poses,templates;
    private boolean flushing,prepared;
    GpuResidentBodyPalette(Object owner){this.owner=owner;begin();}
    void begin(){jobs.begin();flushing=prepared=false;epoch=Long.MIN_VALUE;}
    boolean collecting(){return jobs.collecting();}
    boolean canAdd(int job,int mask){return jobs.canAdd(job,mask)&&(jobs.count()==0||epoch==GpuModelTransforms.batchEpoch());}
    void add(int job,int mask){if(!canAdd(job,mask))throw new IllegalStateException("Resident palette capture crossed a native batch");if(jobs.count()==0)epoch=GpuModelTransforms.batchEpoch();jobs.add(job,mask);}
    void beforeFlush(){jobs.freeze();flushing=true;}
    void afterFlush(){flushing=false;}
    int rows(){return jobs.rows();}
    int prepare(IntBuffer records){
        if(!flushing||jobs.count()==0||epoch!=GpuModelTransforms.batchEpoch()||GpuModelTransforms.batchOwner()!=owner||!GpuModelTransforms.batchThread()||!jobs.covers(records))return fallback();
        if(prepared){if(!revisionsMatch())return fallback();GpuResidentBodies.sharedPoseReuses++;return bones;}
        if(jobs.rows()<1||jobs.rows()>GpuPlayerPackets.count()||jobs.rows()>GpuResidentBodyJobs.MAX_JOBS)return fallback();
        int currentProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int i=0;i<7;i++){int binding=previousBindings[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);previousStarts[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);previousSizes[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        try {
            ready();int required=ModernArmorAuxiliaryBudget.capacity(Math.max(1,capacity),jobs.rows());
            if(required<1||required>GpuResidentBodyJobs.MAX_JOBS)throw new IllegalStateException("Resident palette row budget exceeded");
            if(bones==0)bones=GL15.glGenBuffers();
            if(capacity<required){GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,bones);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)required*12*12*4,GL15.GL_DYNAMIC_DRAW);capacity=required;}
            uploadJobs();players=GpuPlayerPackets.playerRevision();poses=GpuPlayerPackets.poseRevision();templates=GpuPlayerPackets.templateRevision();
            GpuPlayerPackets.bind();GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,bones);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,input);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            GL20.glUseProgram(program);GL30.glUniform1ui(countUniform,jobs.count());GL30.glUniform1ui(rowUniform,jobs.rows());GL43.glDispatchCompute((jobs.count()*12+63)/64,1,1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            if(GpuResidentPlayerState.enabled)GpuResidentPlayerState.submitted();
            if(!revisionsMatch())throw new IllegalStateException("Resident pose packets changed during palette preparation");
            prepared=true;GpuResidentBodies.poseDispatches++;GpuResidentBodies.sharedPoseDispatches++;return bones;
        } finally {
            GL20.glUseProgram(currentProgram);
            for(int i=0;i<7;i++){if(previousBindings[i]!=0&&previousSizes[i]>0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,previousBindings[i],previousStarts[i],previousSizes[i]);else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,previousBindings[i]);}
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);
        }
    }
    private boolean revisionsMatch(){return players==GpuPlayerPackets.playerRevision()&&poses==GpuPlayerPackets.poseRevision()&&templates==GpuPlayerPackets.templateRevision();}
    private int fallback(){GpuResidentBodies.sharedPoseFallbacks++;return 0;}
    private void uploadJobs(){
        boolean changed=uploadedCount!=jobs.count();
        if(!changed)for(int i=0;i<uploadedCount;i++)if(uploaded[i]!=jobs.token(i)){changed=true;break;}
        if(!changed&&input!=0){GpuResidentBodies.sharedInputReuses++;return;}
        int required=ModernArmorAuxiliaryBudget.capacity(uploaded.length,jobs.count());
        if(required<1||required>GpuResidentBodyJobs.MAX_JOBS)throw new IllegalStateException("Resident palette input budget exceeded");
        if(uploaded.length<required){uploaded=Arrays.copyOf(uploaded,required);staging=BufferUtils.createIntBuffer(required);}
        staging.clear();jobs.copy(staging);staging.flip();if(input==0)input=GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,input);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,staging,GL15.GL_STREAM_DRAW);
        for(int i=0;i<jobs.count();i++)uploaded[i]=jobs.token(i);uploadedCount=jobs.count();GpuResidentBodies.sharedInputWrites++;
    }
    private static void ready(){
        if(program!=0)return;
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),candidate=GL20.glCreateProgram();boolean accepted=false;
        try {
            String source=GpuResidentBodyDraw.read("resident_body_palette.comp");int line=source.indexOf('\n');source=source.substring(0,line+1)+GpuResidentBodyDraw.read("player_pose.glsl")+source.substring(line+1);
            GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate,shader);GL20.glLinkProgram(candidate);if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            countUniform=GL20.glGetUniformLocation(candidate,"jobCount");rowUniform=GL20.glGetUniformLocation(candidate,"rowCount");if(countUniform<0||rowUniform<0)throw new IllegalStateException("Resident palette uniforms are absent");
            program=candidate;accepted=true;
        }finally{GL20.glDeleteShader(shader);if(!accepted)GL20.glDeleteProgram(candidate);}
    }
    void close(){if(bones!=0)GL15.glDeleteBuffers(bones);if(input!=0)GL15.glDeleteBuffers(input);bones=input=capacity=uploadedCount=0;begin();}
    static void closeProgram(){if(program!=0)GL20.glDeleteProgram(program);program=0;}
}
