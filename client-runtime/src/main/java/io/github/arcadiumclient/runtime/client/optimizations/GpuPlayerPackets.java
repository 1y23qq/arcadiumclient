// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuPlayerPackets {
    public static final int PLAYER_WORDS=32,PART_WORDS=16,PARTS=12,MAX_PLAYERS=2048;
    private static FloatBuffer players=BufferUtils.createFloatBuffer(256*PLAYER_WORDS);
    private static float[] templates=new float[PARTS*PART_WORDS*4];
    private static int playerCount,templateWords,uploadedPlayers,uploadedTemplates,playerBuffer,templateBuffer,sineBuffer;
    private static final Matrix4f root=new Matrix4f();
    private static final float[] scratch=new float[6];
    private GpuPlayerPackets(){}
    public static int count(){return playerCount;}
    public static void beginBatch(){playerCount=uploadedPlayers=0;}
    public static int registerTemplate(float[] parts){
        int offset=templateWords;if(templateWords+parts.length>templates.length)templates=Arrays.copyOf(templates,Math.max(templateWords+parts.length,templates.length*2));
        System.arraycopy(parts,0,templates,offset,parts.length);templateWords+=parts.length;return offset;
    }
    public static int add(Matrix4fc base,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,
                          boolean sneak,boolean riding,boolean bow,int template){
        if(playerCount==MAX_PLAYERS)return -1;
        int offset=playerCount*PLAYER_WORDS;
        if(offset+PLAYER_WORDS>players.capacity()){
            var next=BufferUtils.createFloatBuffer(players.capacity()*2);next.put(players.duplicate().position(0).limit(offset));players=next;
        }
        players.clear();base.get(offset,players);
        players.put(offset+16,walk).put(offset+17,speed).put(offset+18,age).put(offset+19,yaw).put(offset+20,pitch).put(offset+21,swing);
        players.put(offset+22,sneak?1:0).put(offset+23,bow?1:0).put(offset+24,left).put(offset+25,right).put(offset+26,riding?1:0).put(offset+27,template).put(offset+28,PlayerPoseOptions.flags());
        return playerCount++;
    }
    public static void rollback(int index){if(index==playerCount-1)playerCount--;}
    public static Matrix4f resolve(int encoded,Matrix4f destination){
        int player=encoded/16,part=encoded%16,base=player*PLAYER_WORDS;
        if(player<0||player>=playerCount||part>=PARTS)throw new IllegalArgumentException("Invalid compact player instance");
        int t=(int)players.get(base+27)+part*PART_WORDS;
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
            playerBuffer=GL15.glGenBuffers();templateBuffer=GL15.glGenBuffers();sineBuffer=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,sineBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,PlayerPoseMath.sineTable(),GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STATIC_DRAW);
        }
        if(uploadedPlayers!=playerCount){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,players.duplicate().position(0).limit(playerCount*PLAYER_WORDS),GL15.GL_STREAM_DRAW);uploadedPlayers=playerCount;
        }
        if(uploadedTemplates!=templateWords){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,Arrays.copyOf(templates,templateWords),GL15.GL_STATIC_DRAW);uploadedTemplates=templateWords;
        }
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,playerBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,4,templateBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,5,sineBuffer);
    }
    public static void close(){
        if(playerBuffer!=0)GL15.glDeleteBuffers(playerBuffer);if(templateBuffer!=0)GL15.glDeleteBuffers(templateBuffer);if(sineBuffer!=0)GL15.glDeleteBuffers(sineBuffer);
        playerBuffer=templateBuffer=sineBuffer=uploadedPlayers=uploadedTemplates=playerCount=0;
    }
}
