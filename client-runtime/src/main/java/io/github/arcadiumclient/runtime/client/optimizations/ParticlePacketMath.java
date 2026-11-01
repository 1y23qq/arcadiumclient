// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class ParticlePacketMath {
    private ParticlePacketMath(){}
    public static int[] quads(int[] words,int offset){
        int[] out=new int[28];
        float x=f(words,offset),y=f(words,offset+1),z=f(words,offset+2),size=f(words,offset+3);
        float dx=f(words,offset+10),dy=f(words,offset+11),dz=f(words,offset+12),forward=f(words,offset+13),side=f(words,offset+14);
        int color=words[offset+8],light=words[offset+9];
        for(int vertex=0;vertex<4;vertex++){
            boolean right=vertex>=2,top=vertex==1||vertex==2;int p=vertex*7;
            float px=right?x+dx*size:x-dx*size,pz=right?z+dz*size:z-dz*size;
            px=top?px+forward*size:px-forward*size;pz=top?pz+side*size:pz-side*size;
            out[p]=Float.floatToRawIntBits(px);out[p+1]=Float.floatToRawIntBits(top?y+dy*size:y-dy*size);out[p+2]=Float.floatToRawIntBits(pz);
            out[p+3]=words[offset+(right?4:5)];out[p+4]=words[offset+(top?6:7)];out[p+5]=color;out[p+6]=light;
        }
        return out;
    }
    private static float f(int[] words,int index){return Float.intBitsToFloat(words[index]);}
}
