// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;

public final class FusedUploadReuse073Test {
    @org.junit.jupiter.api.Test void uploadAndCommittedViewContracts()throws Exception{main(new String[0]);}
    private static int checks;
    private static void require(boolean ok){checks++;if(!ok)throw new AssertionError("Upload check "+checks);}
    private interface Action {void run()throws Exception;}
    private static void rejects(Action action)throws Exception{boolean failed=false;try{action.run();}catch(IllegalStateException expected){failed=true;}require(failed);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static final class Synthetic {
        final ActorFusedInput input=new ActorFusedInput();final Object context;
        long generation;
        Synthetic(Object context){this.context=context;}
        ActorFusedInput.View view(int[] words,int[] ranges,long world,long batch)throws Exception{
            int capacity=words.length/57;set(input,"owner",context);set(input,"generation",++generation);set(input,"capacity",capacity);set(input,"words",words.clone());set(input,"ranges",ranges.clone());set(input,"rangeCount",ranges.length/2);
            return new ActorFusedInput.View(input,context,world,batch,generation,capacity,0);
        }
    }
    private static boolean inside(ActorFusedInput.View view,int start,int length){for(int r=0;r<view.ranges();r++)if(start>=view.start(r)&&(long)start+length<=(long)view.start(r)+view.length(r))return true;return false;}
    private static void transmit(ActorFusedInput.Diff diff,ActorFusedInput.View view,int[] gpu,int limit){
        for(int r=0;r<Math.min(diff.ranges,limit);r++){int start=diff.start(r),count=diff.length(r);require(inside(view,start,count));for(int p=start;p<start+count;p++)gpu[p]=view.word(p);}
    }
    private static void matches(ActorFusedInput.View view,int[] gpu){for(int r=0;r<view.ranges();r++)for(int p=view.start(r),end=p+view.length(r);p<end;p++)require(gpu[p]==view.word(p));}
    private static int oldCalls(ActorFusedInput.View view,int[] gpu){int calls=0,last=-2;for(int r=0;r<view.ranges();r++)for(int p=view.start(r),end=p+view.length(r);p<end;p++)if(gpu[p]!=view.word(p)){if(last+1!=p)calls++;last=p;}return calls;}
    private static ActorFusedRows rows(int count,int...jobs){IntBuffer payload=IntBuffer.allocate(jobs.length*19);for(int i=0;i<jobs.length;i++)payload.put(i*19,Float.floatToRawIntBits(-(jobs[i]*16+1f))).put(i*19+1,Float.floatToRawIntBits(-9876543f)).put(i*19+2,Float.floatToRawIntBits(-8765432f));var selected=new ActorFusedRows();require(selected.collect(payload,count));return selected;}
    private static ActorFusedInput.View dynamic(ActorFusedInput input,ActorSceneData scene,Object context,int batch,float partial){
        scene.begin(batch);for(int i=0;i<64;i++){require(scene.observe(i,i+1,100,partial,0,0,10,10,0,0,2,.6f,.6f,0,0));require(scene.captureGpuOnly(i,i+1,i,new Matrix4f(),0,0,0,false,false,false,0,0,0,0));}
        int[] jobs=new int[64];for(int i=0;i<64;i++)jobs[i]=i;return input.prepare(context,scene.seal().raw(),FloatBuffer.allocate(64*32),64,rows(64,jobs));
    }
    private static void dynamicPartials()throws Exception{
        var input=new ActorFusedInput();var scene=new ActorSceneData(64);Object context=new Object();var shadow=new ActorFusedInput.Shadow();int[] gpu=new int[64*57];Arrays.fill(gpu,0xdeadbeef);
        var a=dynamic(input,scene,context,1,.25f);var first=shadow.diff(a);transmit(first,a,gpu,Integer.MAX_VALUE);first.commit();matches(a,gpu);
        var b=dynamic(input,scene,context,2,.5f);int old=oldCalls(b,gpu);var next=shadow.diff(b);
        require(old==64&&next.ranges==1&&next.dirtyWords==64&&next.transmittedWords==253&&!next.exactCommittedViewReuse);transmit(next,b,gpu,Integer.MAX_VALUE);next.commit();matches(b,gpu);
        var second=shadow.diff(b);require(second.ranges==0&&second.comparedWords==0&&second.exactCommittedViewReuse);second.commit();
        var ignored=shadow.diff(b);var superseding=shadow.diff(b);require(superseding.ranges==0&&superseding.comparedWords>0&&!superseding.exactCommittedViewReuse);rejects(ignored::commit);superseding.commit();
        require(shadow.diff(b).exactCommittedViewReuse);
        System.out.println("DYNAMIC64 oldCalls="+old+" newCalls="+next.ranges+" dirty="+next.dirtyWords+" transmitted="+next.transmittedWords+" secondConsumerCompared="+second.comparedWords);
    }
    private static void rangesFailuresAndBits()throws Exception{
        Object context=new Object();var input=new Synthetic(context);var shadow=new ActorFusedInput.Shadow();int[] words=new int[57],gpu=new int[57];Arrays.fill(gpu,0x12345678);int[] ranges={0,4,8,4};
        var a=input.view(words,ranges,1,1);var full=shadow.diff(a);transmit(full,a,gpu,99);full.commit();
        words[1]=0x80000000;words[9]=0x7fc00123;var b=input.view(words,ranges,1,2);var changed=shadow.diff(b);require(changed.ranges==2&&changed.dirtyWords==2);transmit(changed,b,gpu,1);shadow.invalidate();rejects(changed::commit);
        var retry=shadow.diff(b);require(retry.ranges==2&&retry.dirtyWords==8&&!retry.exactCommittedViewReuse);transmit(retry,b,gpu,99);retry.commit();matches(b,gpu);for(int p=4;p<8;p++)require(gpu[p]==0x12345678);
        require(gpu[1]==0x80000000&&gpu[9]==0x7fc00123);
        words[9]=0x7fc00456;words[1]=0;var c=input.view(words,ranges,1,3);var payload=shadow.diff(c);require(payload.dirtyWords==2);transmit(payload,c,gpu,99);payload.commit();matches(c,gpu);
        var reuse=shadow.diff(c);reuse.commit();shadow.invalidate();var forced=shadow.diff(c);require(forced.dirtyWords==8&&!forced.exactCommittedViewReuse);forced.commit();
        var pending=shadow.diff(c);input.view(words,ranges,1,4);rejects(pending::commit);rejects(()->shadow.diff(c));
        var foreign=new Synthetic(new Object());var fv=foreign.view(words,ranges,1,1);rejects(()->shadow.diff(fv));
    }
    private static void gapAndAssociation()throws Exception{
        Object context=new Object();var input=new Synthetic(context);var shadow=new ActorFusedInput.Shadow();int[] words=new int[57];var a=input.view(words,new int[]{0,40},1,1);shadow.diff(a).commit();
        words[1]=1;words[18]=1;var b=input.view(words,new int[]{0,40},1,2);var near=shadow.diff(b);require(near.ranges==1&&near.transmittedWords==18&&near.dirtyWords==2);near.commit();
        words[1]=2;words[19]=2;var c=input.view(words,new int[]{0,40},1,3);var far=shadow.diff(c);require(far.ranges==2);far.commit();
        var otherEncoder=new Synthetic(context);otherEncoder.generation=2;int[] different=words.clone();different[2]=7;var sameTuple=otherEncoder.view(different,new int[]{0,40},1,3);var collision=shadow.diff(sameTuple);require(!collision.exactCommittedViewReuse&&collision.ranges>0);collision.commit();
        var world=otherEncoder.view(different,new int[]{0,40},2,4);var reset=shadow.diff(world);require(reset.dirtyWords==40&&!reset.exactCommittedViewReuse);reset.commit();
        var capacity=otherEncoder.view(new int[114],new int[]{57,4},2,5);var grown=shadow.diff(capacity);require(grown.dirtyWords==4&&!grown.exactCommittedViewReuse);grown.commit();
        var valid=(BitSet)get(shadow,"valid");require(valid.cardinality()==4&&valid.get(57)&&!valid.get(0));
    }
    private static void sparseOrdinary()throws Exception{
        var scene=new ActorSceneData(1);scene.begin(1);Object context=new Object();FloatBuffer legacy=FloatBuffer.allocate(8192*32);legacy.put(8191*32,Float.intBitsToFloat(0x80000000));legacy.put(8191*32+1,Float.intBitsToFloat(0x7fc00123));var input=new ActorFusedInput();var view=input.prepare(context,scene.seal().raw(),legacy,8192,rows(8192,8191));
        var shadow=new ActorFusedInput.Shadow();int[] gpu=new int[8192*57];Arrays.fill(gpu,0x11223344);var diff=shadow.diff(view);require(diff.transmittedWords==36&&diff.ranges==2);transmit(diff,view,gpu,99);diff.commit();matches(view,gpu);require(gpu[0]==0x11223344&&gpu[8191*32]==0x80000000&&gpu[8191*32+1]==0x7fc00123);
    }
    public static void main(String[] args)throws Exception{dynamicPartials();rangesFailuresAndBits();gapAndAssociation();sparseOrdinary();System.out.println("{\"passed\":true,\"checks\":"+checks+",\"dynamic64Calls64To1\":true,\"sparseRangeHolesPreserved\":true,\"committedViewOnlyZeroScan\":true,\"failedUploadStalePlanContextWorldCapacityBitsCovered\":true,\"gameOrGlLaunched\":false}");}
}
