// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;

public final class TerrainResidentFrustumPlan {
    public static final int SECTIONS=256,FACES=7,WORDS=20,COMMAND_WORDS=5,MAX_COMMANDS=SECTIONS*FACES;
    private TerrainResidentFrustumPlan(){}
    public static boolean withinDistance(int x,int y,int z,int cameraX,int cameraY,int cameraZ,float fractionX,float fractionY,float fractionZ,float distance){
        int dx=x-cameraX,dy=y-cameraY,dz=z-cameraZ;
        float nearestX=nearest(dx,dx+16)-fractionX,nearestY=nearest(dy,dy+16)-fractionY,nearestZ=nearest(dz,dz+16)-fractionZ;
        return nearestX*nearestX+nearestZ*nearestZ<distance*distance&&Math.abs(nearestY)<distance;
    }
    private static int nearest(int a,int b){return a>0?a:b<0?b:0;}
    public static boolean visible(float[] clip,int x,int y,int z,int cameraX,int cameraY,int cameraZ,float fractionX,float fractionY,float fractionZ){
        if(clip.length!=16)throw new IllegalArgumentException("Expected a camera-relative clip matrix");
        long dx=x-(long)cameraX,dy=y-(long)cameraY,dz=z-(long)cameraZ;
        if(Math.abs(dx)>(1<<20)||Math.abs(dy)>(1<<20)||Math.abs(dz)>(1<<20))return true;
        if(!Float.isFinite(fractionX+fractionY+fractionZ)||Math.abs(fractionX)>1||Math.abs(fractionY)>1||Math.abs(fractionZ)>1)return true;
        float[] center={(float)dx-fractionX+8,(float)dy-fractionY+8,(float)dz-fractionZ+8,1};
        float[] transformed=new float[4],radius=new float[4];
        for(int row=0;row<4;row++){
            for(int column=0;column<4;column++){
                float coefficient=clip[column*4+row];if(!Float.isFinite(coefficient))return true;
                transformed[row]+=coefficient*center[column];if(column<3)radius[row]+=Math.abs(coefficient)*9.25f;
            }
        }
        for(int axis=0;axis<3;axis++){
            float margin=radius[axis]+radius[3]+.01f;
            if(transformed[3]+transformed[axis]<-margin||transformed[3]-transformed[axis]<-margin)return false;
        }
        return true;
    }
    public static int[] reference(int[] meshes,float[] clip,int cameraX,int cameraY,int cameraZ,float fractionX,float fractionY,float fractionZ,
                                  int faceCameraX,int faceCameraY,int faceCameraZ,boolean cullFaces){
        if(meshes.length!=SECTIONS*WORDS)throw new IllegalArgumentException("Invalid resident terrain metadata");
        int[] output=new int[MAX_COMMANDS*COMMAND_WORDS];int commands=0;
        for(int section=0;section<SECTIONS;section++){
            int row=section*WORDS;if(!visible(clip,meshes[row],meshes[row+1],meshes[row+2],cameraX,cameraY,cameraZ,fractionX,fractionY,fractionZ))continue;
            int mask=cullFaces?faces(faceCameraX,faceCameraY,faceCameraZ,meshes[row],meshes[row+1],meshes[row+2]):127,previousEnd=Integer.MIN_VALUE;
            for(int face=0;face<FACES;face++){
                int elements=meshes[row+12+face],first=meshes[row+4+face];if(elements==0||(mask&1<<face)==0)continue;
                if(elements<0||elements%6!=0||first<0)throw new IllegalArgumentException("Invalid resident quad range");
                if(previousEnd==first)output[(commands-1)*COMMAND_WORDS]+=elements;
                else{int target=commands++*COMMAND_WORDS;output[target]=elements;output[target+1]=1;output[target+3]=first;}
                previousEnd=first+elements/6*4;
            }
        }
        return Arrays.copyOf(output,commands*COMMAND_WORDS);
    }
    private static int faces(int x,int y,int z,int bx,int by,int bz){
        int mask=64;if(x>bx-3)mask|=1;if(y>by-3)mask|=2;if(z>bz-3)mask|=4;if(x<bx+19)mask|=8;if(y<by+19)mask|=16;if(z<bz+19)mask|=32;return mask;
    }
}
