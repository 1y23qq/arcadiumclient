// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class ActorSceneGpu {
    private static final int MAX=8192,SLOTS=3;
    private static final ActorSceneContext context=new ActorSceneContext();
    private static final Slot[] slots=new Slot[SLOTS];
    private static final int[] oldBindings=new int[3];
    private static final long[] oldStarts=new long[3],oldSizes=new long[3];
    private static final int[] indices={0,1,3};
    private static FloatBuffer staging=BufferUtils.createFloatBuffer(256*ActorSceneData.FRAME_WORDS);
    private static final FloatBuffer tickUpload=BufferUtils.createFloatBuffer(ActorSceneData.TICK_WORDS);
    private static final boolean[] selected=new boolean[MAX];
    private static final ActorSceneData.Tick[] frameTicks=new ActorSceneData.Tick[MAX];
    private static int program,uniform,output,cursor;
    private static long resolvedBatch=Long.MIN_VALUE,resolvedRevision=Long.MIN_VALUE,resolvedEpoch=Long.MIN_VALUE,resolvedContextEpoch=Long.MIN_VALUE;
    private static int resolvedRows;
    static long dispatches,reuses,tickUploads,tickUploadWords,frameUploadWords,streamRows,busyFallbacks,resolvedActorRows;
    private static boolean failed,validOutput;
    static long failures,contextRefusals;
    private static void fail(Throwable error){if(!failed)org.apache.logging.log4j.LogManager.getLogger("Arcadium/Actor Scene").warn("Private actor scene uses exact streaming fallback",error);failed=true;failures++;}
    private ActorSceneGpu(){}
    static boolean supported(){var current=GL.getCapabilities();boolean accepted=context.accept(current,current.OpenGL43);if(!context.owns(current))contextRefusals++;return !failed&&accepted;}
    static boolean ownsCurrent(){if(!context.attached())return true;try{return context.owns(GL.getCapabilities());}catch(IllegalStateException absent){return false;}}
    static void requireCurrent(){if(!ownsCurrent()){contextRefusals++;throw new IllegalStateException("Actor scene buffers belong to a different GL context; retain native CPU recovery");}}
    static long contextEpoch(){return context.epoch();}
    static boolean bind(ActorSceneData scene,FloatBuffer legacy,int rows,long batch,long revision){
        if(!supported()||rows<1||rows>MAX)return false;
        if(validOutput&&output!=0&&scene.epoch()==resolvedEpoch&&context.epoch()==resolvedContextEpoch&&batch==resolvedBatch&&revision==resolvedRevision&&rows==resolvedRows){int previous=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,output);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previous);reuses++;return true;}
        int count=0;
        Arrays.fill(selected,0,rows,false);Arrays.fill(frameTicks,null);
        for(int job=0;job<rows;job++)if(scene.gpuReady(job)){selected[job]=true;count++;}
        if(count==0)return false;
        ActorSceneData.Batch snapshot=scene.seal();if(snapshot.batch()!=batch)return false;
        for(int job=0;job<rows;job++)if(selected[job]){
            int slot=snapshot.slot(job);var tick=snapshot.tick(job);var known=frameTicks[slot];if(known!=null&&known!=tick)return false;frameTicks[slot]=tick;
        }
        int chosen=-1;
        try{for(int n=0;n<SLOTS;n++){int i=(cursor+n)%SLOTS;if(slots[i]==null||slots[i].free()){chosen=i;break;}}}catch(Throwable error){fail(error);return false;}
        if(chosen<0){busyFallbacks++;return false;}
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int n=0;n<indices.length;n++){int binding=oldBindings[n]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,indices[n]);oldStarts[n]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,indices[n]);oldSizes[n]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,indices[n]);}
        boolean accepted=false,dispatched=false,fenced=false;Slot slot=null;
        try{
            validOutput=false;ready();slot=slots[chosen];if(slot==null){slot=new Slot();slots[chosen]=slot;}slot.allocate();
            if(staging.capacity()<count*ActorSceneData.FRAME_WORDS)staging=BufferUtils.createFloatBuffer(Math.min(MAX*ActorSceneData.FRAME_WORDS,Integer.highestOneBit(count*ActorSceneData.FRAME_WORDS-1)<<1));
            staging.clear();for(int job=0;job<rows;job++)if(selected[job])snapshot.input(job,staging);staging.flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,slot.frames);GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,staging);frameUploadWords+=staging.remaining();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,slot.ticks);
            for(int index=0;index<frameTicks.length;index++){
                var tick=frameTicks[index];if(tick==null||slot.revisions[index]==tick.revision)continue;
                tickUpload.clear();tick.copy(tickUpload);tickUpload.flip();GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,(long)index*ActorSceneData.TICK_WORDS*4,tickUpload);
                slot.revisions[index]=tick.revision;tickUploads++;tickUploadWords+=ActorSceneData.TICK_WORDS;
            }
            if(output==0)output=GL15.glGenBuffers();if(output==0)throw new IllegalStateException("Actor scene output allocation failed");GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,output);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)rows*32*4,GL15.GL_STREAM_DRAW);
            for(int start=0;start<rows;){
                if(selected[start]){start++;continue;}int end=start+1;while(end<rows&&!selected[end])end++;
                FloatBuffer source=legacy.duplicate().position(start*32).limit(end*32);GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,(long)start*32*4,source);streamRows+=end-start;start=end;
            }
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,slot.ticks);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,slot.frames);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,output);
            GL20.glUseProgram(program);GL30.glUniform1ui(uniform,count);GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            dispatched=true;GL43.glDispatchCompute((count+63)/64,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            slot.fenceInputs();fenced=true;
            resolvedContextEpoch=context.epoch();resolvedEpoch=scene.epoch();resolvedBatch=batch;resolvedRevision=revision;resolvedRows=rows;cursor=(chosen+1)%SLOTS;dispatches++;resolvedActorRows+=count;validOutput=accepted=true;return true;
        }catch(Throwable failure){fail(failure);return false;}
        finally{
            if(dispatched&&!fenced&&slot!=null)try{slot.fenceInputs();}catch(Throwable failure){fail(failure);}
            for(int n=0;n<indices.length;n++)if(indices[n]!=3||!accepted){if(oldBindings[n]==0||oldSizes[n]==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,indices[n],oldBindings[n]);else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,indices[n],oldBindings[n],oldStarts[n],oldSizes[n]);}
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);GL20.glUseProgram(previousProgram);Arrays.fill(frameTicks,null);
        }
    }
    private static void ready()throws java.io.IOException{
        if(program!=0)return;int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),candidate=GL20.glCreateProgram();
        try(var stream=ActorSceneGpu.class.getResourceAsStream("/assets/arcadium/shaders/actor_scene_resolve.comp")){
            if(stream==null)throw new IllegalStateException("Actor scene resolver resource missing");GL20.glShaderSource(shader,new String(stream.readAllBytes(),StandardCharsets.UTF_8));GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate,shader);GL20.glLinkProgram(candidate);if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));uniform=GL20.glGetUniformLocation(candidate,"sceneRows");if(uniform<0)throw new IllegalStateException("Actor scene row uniform missing");program=candidate;
        }catch(Throwable failure){GL20.glDeleteProgram(candidate);throw failure;}finally{GL20.glDeleteShader(shader);}
    }
    static long bytes(){long total=output==0?0:(long)resolvedRows*32*4;for(Slot slot:slots)if(slot!=null&&slot.ticks!=0)total+=(long)MAX*(ActorSceneData.TICK_WORDS+ActorSceneData.FRAME_WORDS)*4;return total;}
    static void close(){
        if(!ownsCurrent()){contextRefusals++;return;}Object owner=context.attached()?GL.getCapabilities():null;
        Throwable failure=null;
        for(int i=0;i<SLOTS;i++){try{if(slots[i]!=null)slots[i].close();}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}finally{slots[i]=null;}}
        try{if(output!=0)GL15.glDeleteBuffers(output);}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        try{if(program!=0)GL20.glDeleteProgram(program);}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        output=program=cursor=resolvedRows=0;resolvedContextEpoch=resolvedEpoch=resolvedBatch=resolvedRevision=Long.MIN_VALUE;failed=validOutput=false;Arrays.fill(frameTicks,null);if(failure==null)context.release(owner);else fail(failure);
    }
    private static final class Slot {
        int ticks,frames;long fence;
        final long[] revisions=new long[MAX];
        void allocate(){
            if(ticks!=0&&frames!=0)return;
            try{ticks=GL15.glGenBuffers();if(ticks==0)throw new IllegalStateException("Actor tick allocation failed");GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,ticks);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)MAX*ActorSceneData.TICK_WORDS*4,GL15.GL_DYNAMIC_DRAW);frames=GL15.glGenBuffers();if(frames==0)throw new IllegalStateException("Actor frame allocation failed");GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,frames);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)MAX*ActorSceneData.FRAME_WORDS*4,GL15.GL_DYNAMIC_DRAW);}
            catch(Throwable failure){close();throw failure;}
        }
        void fenceInputs(){long next=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(next==0)throw new IllegalStateException("Actor scene input fence creation failed");if(fence!=0)GL32.glDeleteSync(fence);fence=next;}
        boolean free(){if(fence==0)return true;int result=GL32.glClientWaitSync(fence,0,0);if(result==GL32.GL_TIMEOUT_EXPIRED)return false;if(result!=GL32.GL_ALREADY_SIGNALED&&result!=GL32.GL_CONDITION_SATISFIED)throw new IllegalStateException("Actor scene input fence query failed");GL32.glDeleteSync(fence);fence=0;return true;}
        void close(){try{if(fence!=0)GL32.glDeleteSync(fence);}finally{try{if(ticks!=0)GL15.glDeleteBuffers(ticks);}finally{if(frames!=0)GL15.glDeleteBuffers(frames);ticks=frames=0;fence=0;Arrays.fill(revisions,0);}}}
    }
}
