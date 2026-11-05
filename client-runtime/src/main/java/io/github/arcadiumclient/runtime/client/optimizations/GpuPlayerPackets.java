// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;
import java.util.HashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuPlayerPackets {
    public static final int PLAYER_WORDS=32,PART_WORDS=16,PARTS=12,MAX_PLAYERS=8192,POSE_WORDS=PARTS*8;
    private static FloatBuffer players=BufferUtils.createFloatBuffer(256*PLAYER_WORDS);
    private static FloatBuffer poses=BufferUtils.createFloatBuffer(256*POSE_WORDS);
    private static FloatBuffer playerView=players.duplicate(),poseView=poses.duplicate();
    private static float[] templates=new float[PARTS*PART_WORDS*4];
    private static final HashMap<TemplateKey,Integer> templateIds=new HashMap<>();
    private static int playerCount,poseCount,templateWords,uploadedPlayers,uploadedTemplates,playerBuffer,templateBuffer,sineBuffer,poseBuffer;
    private static long poseRevision,uploadedPoseRevision=-1,stateRevision,playerRevision,uploadedPlayerRevision=-1;
    private static final Matrix4f root=new Matrix4f();
    private static final float[] scratch=new float[6];
    private GpuPlayerPackets(){}
    private static final class TemplateKey {
        final float[] values;final int hash;
        TemplateKey(float[] values){this.values=values.clone();hash=Arrays.hashCode(this.values);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof TemplateKey key&&Arrays.equals(values,key.values);}
    }
    public static int count(){return playerCount;}
    public static void beginBatch(){playerCount=poseCount=uploadedPlayers=0;uploadedPoseRevision=uploadedPlayerRevision=-1;GpuResidentPlayerState.beginBatch();}
    public static int registerTemplate(float[] parts){
        if(parts.length!=PARTS*PART_WORDS)
            throw new IllegalArgumentException("Player template budget exceeded or invalid template");
        TemplateKey key=new TemplateKey(parts);Integer existing=templateIds.get(key);if(existing!=null)return existing;
        if(templateWords+parts.length>256*PARTS*PART_WORDS)throw new IllegalArgumentException("Player template budget exceeded");
        int offset=templateWords;if(templateWords+parts.length>templates.length)templates=Arrays.copyOf(templates,Math.max(templateWords+parts.length,templates.length*2));
        System.arraycopy(parts,0,templates,offset,parts.length);templateWords+=parts.length;templateIds.put(key,offset);return offset;
    }
    public static int add(Matrix4fc base,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,
                          boolean sneak,boolean riding,boolean bow,int template){
        if(playerCount==MAX_PLAYERS)return -1;
        int offset=playerCount*PLAYER_WORDS;
        if(offset+PLAYER_WORDS>players.capacity()){
            var next=BufferUtils.createFloatBuffer(players.capacity()*2);next.put(players.duplicate().position(0).limit(offset));players=next;playerView=players.duplicate();
        }
        players.clear();base.get(offset,players);
        players.put(offset+16,walk).put(offset+17,speed).put(offset+18,age).put(offset+19,yaw).put(offset+20,pitch).put(offset+21,swing);
        players.put(offset+22,sneak?1:0).put(offset+23,bow?1:0).put(offset+24,left).put(offset+25,right).put(offset+26,riding?1:0).put(offset+27,template).put(offset+28,PlayerPoseOptions.flags());
        players.put(offset+29,-1);
        stateRevision++;playerRevision++;
        return playerCount++;
    }
    public static int addPose(Matrix4fc base,int template){
        int job=add(base,0,0,0,0,0,0,0,0,false,false,false,template);
        if(job<0)return job;
        int words=(job+1)*POSE_WORDS;
        if(words>poses.capacity()){
            FloatBuffer next=BufferUtils.createFloatBuffer(Math.max(words,poses.capacity()*2));
            next.put(poses.duplicate().position(0).limit(poseCount*POSE_WORDS));poses=next;poseView=poses.duplicate();
        }
        players.put(job*PLAYER_WORDS+29,job);poseCount=job+1;poseRevision++;stateRevision++;playerRevision++;
        return job;
    }
    public static void copyTemplate(int offset,float[] destination){
        if(offset<0||offset% (PARTS*PART_WORDS)!=0||offset+PARTS*PART_WORDS>templateWords||destination.length!=PARTS*PART_WORDS)
            throw new IllegalArgumentException("Invalid player template copy");
        System.arraycopy(templates,offset,destination,0,destination.length);
    }
    public static int addVariant(int source,int template){
        if(source<0||source>=playerCount||template<0||template%(PARTS*PART_WORDS)!=0||template+PARTS*PART_WORDS>templateWords)
            throw new IllegalArgumentException("Invalid player pose variant");
        if(playerCount==MAX_PLAYERS)return -1;
        int target=playerCount*PLAYER_WORDS;
        if(target+PLAYER_WORDS>players.capacity()){
            FloatBuffer next=BufferUtils.createFloatBuffer(players.capacity()*2);
            next.put(players.duplicate().position(0).limit(target));players=next;playerView=players.duplicate();
        }
        int origin=source*PLAYER_WORDS;
        for(int word=0;word<PLAYER_WORDS;word++)players.put(target+word,players.get(origin+word));
        players.put(target+27,template);stateRevision++;playerRevision++;
        return playerCount++;
    }
    public static void writePose(int job,int part,float tx,float ty,float tz,float rx,float ry,float rz){
        if(job<0||job>=playerCount||part<0||part>=PARTS||players.get(job*PLAYER_WORDS+29)<0)
            throw new IllegalArgumentException("Invalid native player pose");
        int offset=(int)players.get(job*PLAYER_WORDS+29)*POSE_WORDS+part*8;
        poses.put(offset,tx).put(offset+1,ty).put(offset+2,tz).put(offset+3,rx).put(offset+4,ry).put(offset+5,rz);
        poseRevision++;stateRevision++;
    }
    public static void rollback(int index){if(index==playerCount-1){playerCount--;if(poseCount>playerCount)poseCount=playerCount;poseRevision++;stateRevision++;playerRevision++;}}
    public static void resetTemplates(){templateIds.clear();templateWords=0;uploadedTemplates=-1;beginBatch();}
    public static Matrix4f resolve(int encoded,Matrix4f destination){
        int player=encoded/16,part=encoded%16,base=player*PLAYER_WORDS;
        if(player<0||player>=playerCount||part>=PARTS)throw new IllegalArgumentException("Invalid compact player instance");
        int t=(int)players.get(base+27)+part*PART_WORDS;
        int pose=(int)players.get(base+29);
        if(pose>=0){
            int p=pose*POSE_WORDS+part*8;
            root.set(base,players);
            return destination.set(root).translate(poses.get(p),poses.get(p+1),poses.get(p+2))
                    .rotateZ(poses.get(p+5)).rotateY(poses.get(p+4)).rotateX(poses.get(p+3))
                    .translate(templates[t+8],templates[t+9],templates[t+10]).scale(templates[t+12],templates[t+13],templates[t+14]);
        }
        PlayerPoseMath.part(players.get(base+16),players.get(base+17),players.get(base+18),players.get(base+19),players.get(base+20),players.get(base+21),
                (int)players.get(base+24),(int)players.get(base+25),players.get(base+22)!=0,players.get(base+26)!=0,players.get(base+23)!=0,
                PlayerPoseMath.BONES[part],templates[t],templates[t+1],templates[t+2],(int)players.get(base+28),scratch);
        root.set(base,players);if(players.get(base+22)!=0&&((int)players.get(base+28)&2)==0)root.translate(0,.2f,0);
        return destination.set(root).translate(scratch[0]*.0625f+templates[t+4],scratch[1]*.0625f+templates[t+5],scratch[2]*.0625f+templates[t+6])
                .rotateZ(scratch[5]).rotateY(scratch[4]).rotateX(scratch[3])
                .translate(templates[t+8],templates[t+9],templates[t+10]).scale(templates[t+12],templates[t+13],templates[t+14]);
    }
    public static void bind(){
        if(playerBuffer==0){
            playerBuffer=GL15.glGenBuffers();templateBuffer=GL15.glGenBuffers();sineBuffer=GL15.glGenBuffers();poseBuffer=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,sineBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,PlayerPoseMath.sineTable(),GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
        }
        boolean resident=GpuResidentPlayerState.enabled&&GpuResidentPlayerState.bind(playerView.position(0).limit(playerCount*PLAYER_WORDS),poseView.position(0).limit(poseCount*POSE_WORDS),stateRevision);
        if(!resident&&playerCount>0&&uploadedPlayerRevision!=playerRevision){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,playerView.position(0).limit(playerCount*PLAYER_WORDS),GL15.GL_STREAM_DRAW);uploadedPlayers=playerCount;uploadedPlayerRevision=playerRevision;
        }
        if(uploadedTemplates!=templateWords){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,Arrays.copyOf(templates,templateWords),GL15.GL_STATIC_DRAW);uploadedTemplates=templateWords;
        }
        if(!resident&&uploadedPoseRevision!=poseRevision&&poseCount>0){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,poseView.position(0).limit(poseCount*POSE_WORDS),GL15.GL_STREAM_DRAW);uploadedPoseRevision=poseRevision;
        }
        if(!resident){GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,playerBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,6,poseBuffer);}
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,4,templateBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,5,sineBuffer);
    }
    public static void close(){
        GpuResidentPlayerState.reset();
        if(playerBuffer!=0)GL15.glDeleteBuffers(playerBuffer);if(templateBuffer!=0)GL15.glDeleteBuffers(templateBuffer);if(sineBuffer!=0)GL15.glDeleteBuffers(sineBuffer);if(poseBuffer!=0)GL15.glDeleteBuffers(poseBuffer);
        playerBuffer=templateBuffer=sineBuffer=poseBuffer=uploadedPlayers=uploadedTemplates=playerCount=poseCount=templateWords=0;uploadedPoseRevision=uploadedPlayerRevision=-1;
        templateIds.clear();
    }
}
