// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import java.nio.FloatBuffer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

final class ModernArmorMvp {
    private static final int PARTS=14;
    private final Entry[] entries=new Entry[PARTS];
    private final float[] root=new float[16],projection=new float[16],eye=new float[16],mvp=new float[16];
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private Object capture,first,second;
    private int used;
    private long frame=-1;
    private boolean ready;
    private int nextPrelude=-1;
    static long layerRoots,clientMatrixConstructions,cacheHits,nativeSneakPreludeRoots,identityPreludeRoots;
    private static final class Entry{ModelPart part;final int[] state=new int[9];int index=-1;}
    ModernArmorMvp(){for(int i=0;i<PARTS;i++)entries[i]=new Entry();}
    void clear(){for(int i=0;i<used;i++){entries[i].part=null;entries[i].index=-1;}used=0;capture=first=second=null;frame=-1;ready=false;nextPrelude=-1;}
    void preparePrelude(int value){nextPrelude=value;}
    boolean begin(Object capture,Model first,Model second,long frame){
        int prelude=nextPrelude;clear();if(prelude<0||prelude>1)return false;
        if(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)>=GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH))return false;
        matrix.clear();GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,matrix);matrix.get(root);
        matrix.clear();GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,matrix);matrix.get(projection);
        for(int i=0;i<16;i++)if(!Float.isFinite(root[i])||!Float.isFinite(projection[i]))return false;
        if(root[3]!=0||root[7]!=0||root[11]!=0||root[15]!=1)return false;
        if(prelude==1){
            int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);boolean pushed=false;
            try{
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();pushed=true;matrix.clear();matrix.put(root).flip();GL11.glLoadMatrixf(matrix);
                GL11.glTranslatef(0,.2f,0);matrix.clear();GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,matrix);matrix.get(root);
            }finally{if(pushed)GL11.glPopMatrix();GL11.glMatrixMode(mode);}
            nativeSneakPreludeRoots++;
        }else identityPreludeRoots++;
        for(int i=0;i<16;i++)if(!Float.isFinite(root[i]))return false;
        this.capture=capture;this.first=first;this.second=second;this.frame=frame;ready=true;layerRoots++;return true;
    }
    int index(Object capture,ModelPart part,long frame,ModernArmorProjectiveMatrices table){
        Model model=((BetaModelPartAccess)part).arcadium$baseModel();
        if(!ready||this.capture!=capture||this.frame!=frame||model!=first&&model!=second)return -1;
        Entry found=null;
        for(int i=0;i<used;i++)if(entries[i].part==part){found=entries[i];break;}
        if(found!=null&&found.index>=0&&same(found,part)){cacheHits++;return found.index;}
        if(found==null){if(used==PARTS)return -1;found=entries[used++];found.part=part;}
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);boolean pushed=false;
        try{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();pushed=true;
            matrix.clear();matrix.put(root).flip();GL11.glLoadMatrixf(matrix);
            GL11.glTranslatef(part.translateX,part.translateY,part.translateZ);
            GL11.glTranslatef(part.x*.0625f,part.y*.0625f,part.z*.0625f);
            if(part.rotationZ!=0)GL11.glRotatef(part.rotationZ*57.295776f,0,0,1);
            if(part.rotationY!=0)GL11.glRotatef(part.rotationY*57.295776f,0,1,0);
            if(part.rotationX!=0)GL11.glRotatef(part.rotationX*57.295776f,1,0,0);
            matrix.clear();GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,matrix);matrix.get(eye);clientMatrixConstructions++;
        }finally{if(pushed)GL11.glPopMatrix();GL11.glMatrixMode(mode);}
        ModernArmorMvpMath.multiply(projection,eye,mvp);int index=table.intern(mvp);if(index<0)return -1;
        save(found,part);found.index=index;return index;
    }
    private static boolean same(Entry e,ModelPart p){return e.state[0]==bits(p.x)&&e.state[1]==bits(p.y)&&e.state[2]==bits(p.z)&&e.state[3]==bits(p.translateX)&&e.state[4]==bits(p.translateY)&&e.state[5]==bits(p.translateZ)&&e.state[6]==bits(p.rotationX)&&e.state[7]==bits(p.rotationY)&&e.state[8]==bits(p.rotationZ);}
    private static void save(Entry e,ModelPart p){e.state[0]=bits(p.x);e.state[1]=bits(p.y);e.state[2]=bits(p.z);e.state[3]=bits(p.translateX);e.state[4]=bits(p.translateY);e.state[5]=bits(p.translateZ);e.state[6]=bits(p.rotationX);e.state[7]=bits(p.rotationY);e.state[8]=bits(p.rotationZ);}
    static int selectPrelude(boolean sneaking,boolean baby,boolean firstModelBaby,boolean secondModelBaby,boolean oldFlagKnown,boolean oldSneak){
        if(baby||firstModelBaby||secondModelBaby||!oldFlagKnown)return -1;
        return sneaking&&!oldSneak?1:0;
    }
    private static int bits(float value){return Float.floatToRawIntBits(value);}
}
