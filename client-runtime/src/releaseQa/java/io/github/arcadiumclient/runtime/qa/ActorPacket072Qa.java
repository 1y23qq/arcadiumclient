// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.optimizations.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ActorPacket072Qa {
    private static final IdentityHashMap<Object, Integer> indices=new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Long> handles=new IdentityHashMap<>();
    private static final Set<Long> epochs=new HashSet<>();
    private static final ArrayList<Object> roster=new ArrayList<>();
    private static long epoch=Long.MIN_VALUE,orders,matrices;
    private static float maximumError;
    private static Map<String,Object> pending;
    private static int completed;
    private static Throwable failure;
    private ActorPacket072Qa(){}
    public static boolean enabled(){return Boolean.getBoolean("arcadium.actorPacket072.proof");}
    private static void owned(){
        Minecraft mc=Minecraft.getInstance();
        require(PlayerPipeline072Qa.enabled()&&System.getProperty("arcadium.playerPipeline072.mode","visual").equals("visual"),"Use the visual actor fixture");
        require(mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0"),"Use only the owned fake session");
        require(GpuResidentPlayerState.enabled,"Enable resident player state for the actor proof");
    }
    private static Object registry()throws Exception{Field field=GpuPlayerPackets.class.getDeclaredField("actors");field.setAccessible(true);return field.get(null);}
    private static Number value(Object registry,String method,Object owner)throws Exception{
        Method call=registry.getClass().getDeclaredMethod(method,Object.class);call.setAccessible(true);return (Number)call.invoke(registry,owner);
    }
    public static void beforeWorld(){
        if(!enabled()||!PlayerPipeline072Qa.sceneReady())return;owned();
        try{
            Minecraft mc=Minecraft.getInstance();roster.clear();roster.addAll(mc.world.players);
            if((orders++&1)==0)Collections.reverse(roster);
            GpuPlayerPackets.prepareActors(mc.world,roster,true);
            Object registry=registry();long current=GpuPlayerPackets.actorEpoch();boolean changed=current!=epoch;
            if(changed){indices.clear();epochs.add(current);require(epochs.size()<64,"Unexpected repeated actor invalidations");}
            for(Object owner:roster){
                int index=value(registry,"index",owner).intValue();long handle=value(registry,"handle",owner).longValue();
                require(index>=0&&handle!=0,"Actual world actor is not reserved");
                Integer previous=indices.put(owner,index);Long oldHandle=handles.put(owner,handle);
                if(!changed&&previous!=null)require(previous==index,"Render-order changes moved an unchanged actor slot");
                if(oldHandle!=null)require(changed?oldHandle!=handle:oldHandle==handle,"World/resource actor handle generation is stale");
            }
            epoch=current;
        }catch(Exception error){failure=new IllegalStateException("Actor scene order/generation proof failed",error);}
    }
    public static void check(){if(enabled()&&failure!=null)throw new IllegalStateException("Actor packet proof failed",failure);}
    public static Map<String,Object> requestSample(){
        owned();check();require(pending==null,"The previous actor GPU proof was not completed");
        pending=new LinkedHashMap<>();pending.put("pending",true);return pending;
    }
    public static void afterPlayer(Object owner,long compactDelta){
        if(!enabled()||pending==null||compactDelta<=0)return;
        try{Map<String,Object> proof=sample(owner);pending.clear();pending.putAll(proof);pending=null;completed++;}
        catch(Throwable error){failure=error;}
    }
    private static Map<String,Object> sample(Object owner){
        owned();
        int oldCount=GpuPlayerPackets.count(),oldRows=GpuPlayerPackets.explicitPoseRows();
        int duplicate=-1,pose=-1,variant=-1,buffer=0,guard=0;
        long[] original=bindings();
        try{
            require(owner.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor"),"Use an actual captured fixture actor");
            int stable=value(registry(),"index",owner).intValue();
            Field data=GpuPlayerPackets.class.getDeclaredField("players");data.setAccessible(true);FloatBuffer players=(FloatBuffer)data.get(null);
            int template=(int)players.get(stable*32+27);Matrix4f before=GpuPlayerPackets.resolve(stable*16,new Matrix4f());
            duplicate=GpuPlayerPackets.addActor(owner,new Matrix4f().translation(21,22,23),0,0,0,0,0,0,0,0,false,false,false,template);
            require(duplicate>=GpuPlayerPackets.residentActorCount()&&duplicate!=stable,"A repeated actual actor overwrote its resident slot");
            require(before.equals(GpuPlayerPackets.resolve(stable*16,new Matrix4f()),0),"Repeated actor capture changed the first pose");
            pose=GpuPlayerPackets.addPose(new Matrix4f().translation(4,5,6).rotateY(.4f),template);
            GpuPlayerPackets.writePose(pose,2,1,2,3,.1f,.2f,.3f);variant=GpuPlayerPackets.addVariant(pose,template);
            players=(FloatBuffer)data.get(null);int row=(int)players.get(pose*32+29);require(row==oldRows&&row<pose,"Explicit native poses still contain analytic actor holes");
            int[] jobs={stable,duplicate,pose,variant},parts={0,0,2,2};IntBuffer payload=BufferUtils.createIntBuffer(4*19);Matrix4f[] expected=new Matrix4f[4];
            for(int record=0;record<4;record++){
                int at=record*19;payload.put(at,Float.floatToRawIntBits(-(jobs[record]*16+parts[record]+1)));
                payload.put(at+1,Float.floatToRawIntBits(-9876543f));payload.put(at+2,Float.floatToRawIntBits(-8765432f));
                for(int word=12;word<19;word++)payload.put(at+word,record*100+word);
                expected[record]=GpuPlayerPackets.resolve(jobs[record]*16+parts[record],new Matrix4f());
            }
            require(GL11.glGetError()==0,"GL was not clean before actor packet proof");
            guard=GL15.glGenBuffers();int alignment=Math.max(16,GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT));
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,guard);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)alignment+64,GL15.GL_STREAM_DRAW);
            for(int binding=0;binding<7;binding++)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,binding,guard,alignment,64);
            buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,payload,GL15.GL_STREAM_DRAW);
            long[] ranged=bindings();GpuModelTransforms.expand(buffer,payload);require(Arrays.equals(ranged,bindings()),"Actor compute changed ranged bindings/program state");
            IntBuffer actual=BufferUtils.createIntBuffer(payload.remaining());GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,actual);
            for(int record=0;record<4;record++){
                for(int column=0;column<4;column++)for(int r=0;r<3;r++){
                    float value=Float.intBitsToFloat(actual.get(record*19+column*3+r));float error=Math.abs(value-expected[record].get(column,r));
                    require(Float.isFinite(value)&&error<=.00001f,"Actual GPU matrix differs from current actor/explicit pose");maximumError=Math.max(maximumError,error);
                }
                for(int word=12;word<19;word++)require(actual.get(record*19+word)==payload.get(record*19+word),"Actor compute changed native material metadata");
                matrices++;
            }
            require(GL11.glGetError()==0,"GL error in actor packet proof");
            return Map.of("gpuMatrices",4,"explicitPoseRow",row,"explicitPoseJob",pose,"reservedActors",GpuPlayerPackets.residentActorCount(),
                          "duplicateUsesEphemeral",true,"rangedBindingsPreserved",true,"maximumMatrixError",maximumError);
        }catch(Exception failure){throw new IllegalStateException("Native actor packet proof failed",failure);}
        finally{
            if(variant>=0)GpuPlayerPackets.rollback(variant);if(pose>=0)GpuPlayerPackets.rollback(pose);if(duplicate>=0)GpuPlayerPackets.rollback(duplicate);
            restore(original);if(buffer!=0)GL15.glDeleteBuffers(buffer);if(guard!=0)GL15.glDeleteBuffers(guard);
            require(GpuPlayerPackets.count()==oldCount&&GpuPlayerPackets.explicitPoseRows()==oldRows,"Diagnostic actor jobs leaked into the scene");
        }
    }
    public static Map<String,Object> verify(){
        owned();check();require(completed==4&&pending==null&&matrices>=16&&orders>10&&epochs.size()>=2&&GpuPlayerPackets.actorAssignments>0,"Actor order/reload/GPU proof did not activate");
        return Map.of("passed",true,"gpuMatrices",matrices,"orderFrames",orders,"epochs",epochs.size(),"actorAssignments",GpuPlayerPackets.actorAssignments,
                      "maximumMatrixError",maximumError,"notPerformanceEvidence",true);
    }
    private static long[] bindings(){
        long[] result=new long[24];result[0]=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);result[1]=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);result[2]=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        for(int binding=0;binding<7;binding++){int at=3+binding*3;result[at]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,binding);
            if(result[at]!=0){result[at+1]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,binding);result[at+2]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,binding);}}
        return result;
    }
    private static void restore(long[] state){
        GL20.glUseProgram((int)state[0]);for(int binding=0;binding<7;binding++){int at=3+binding*3;
            if(state[at]!=0&&state[at+2]!=0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,binding,(int)state[at],state[at+1],state[at+2]);
            else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,binding,(int)state[at]);}
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,(int)state[1]);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,(int)state[2]);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
