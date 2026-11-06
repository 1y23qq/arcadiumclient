// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.util.Arrays;
import org.joml.Matrix4fc;

final class ActorSceneData {
    static final int TICK_WORDS=16,FRAME_WORDS=27,MAX_GPU_ID=0xffffff,MAX_ROWS=8192;
    static final class Tick {
        final long handle,tick,revision;
        private final float[] words;
        Tick(long handle,long tick,long revision,float[] source){this.handle=handle;this.tick=tick;this.revision=revision;words=source.clone();}
        float word(int index){return words[index];}
        void copy(FloatBuffer target){target.put(words);}
        boolean same(float[] source){for(int i=0;i<TICK_WORDS;i++)if(Float.floatToRawIntBits(words[i])!=Float.floatToRawIntBits(source[i]))return false;return true;}
    }
    static final class Batch {
        private final ActorSceneData source;
        private final long generation,lifetime;
        Batch(ActorSceneData source){this.source=source;generation=source.generation;lifetime=source.epoch;}
        private void check(){if(source.owner!=Thread.currentThread()||generation!=source.generation||lifetime!=source.epoch)throw new IllegalStateException("Actor batch view is stale or belongs to another thread");}
        Tick tick(int job){check();return source.rowTicks[job];}
        int slot(int job){check();return source.rowSlots[job];}
        long batch(){check();return source.batch;}
        void input(int job,FloatBuffer target){check();source.input(job,target);}
        void cpu(int job,FloatBuffer target,int offset){check();source.cpu(job,target,offset);}
    }
    private final Thread owner=Thread.currentThread();
    private final Tick[] previous,current,rowTicks;
    private final long[] observed;
    private final float[] partial,scratch=new float[TICK_WORDS],rowPartial,words;
    private final long[] rowHandles;
    private final int[] rowSlots,materials;
    private final boolean[] present,discarded,exact;
    private long revision,batch,epoch,generation;
    private int used;
    private Batch sealed;
    long tickWrites,tickReuses,frameCaptures,poseMismatches,materialChanges,gpuOnlyCaptures,cpuInterpolationRows,captureValidationRows,sealedViews;
    static final int PER_ACTOR_FRAME_ARRAY_ALLOCATIONS=0,PER_ACTOR_FRAME_OBJECT_ALLOCATIONS=0;
    ActorSceneData(int capacity){
        if(capacity<1||capacity>8192)throw new IllegalArgumentException("Invalid actor scene capacity");
        previous=new Tick[capacity];current=new Tick[capacity];observed=new long[capacity];partial=new float[capacity];Arrays.fill(observed,Long.MIN_VALUE);
        rowTicks=new Tick[MAX_ROWS];rowPartial=new float[MAX_ROWS];rowHandles=new long[MAX_ROWS];rowSlots=new int[MAX_ROWS];materials=new int[MAX_ROWS];
        present=new boolean[MAX_ROWS];discarded=new boolean[MAX_ROWS];exact=new boolean[MAX_ROWS];words=new float[MAX_ROWS*32];
    }
    void move(int from,int to){previous[to]=previous[from];current[to]=current[from];observed[to]=observed[from];partial[to]=partial[from];previous[from]=current[from]=null;observed[from]=Long.MIN_VALUE;}
    void retain(int slot,long handle){if(current[slot]!=null&&current[slot].handle!=handle){previous[slot]=current[slot]=null;observed[slot]=Long.MIN_VALUE;}}
    void truncate(int size){Arrays.fill(previous,size,previous.length,null);Arrays.fill(current,size,current.length,null);Arrays.fill(observed,size,observed.length,Long.MIN_VALUE);}
    void begin(long nextBatch){
        if(generation==Long.MAX_VALUE)throw new IllegalStateException("Actor batch generation exhausted");generation++;
        Arrays.fill(rowTicks,0,used,null);Arrays.fill(present,0,used,false);Arrays.fill(discarded,0,used,false);used=0;batch=nextBatch;sealed=null;
    }
    boolean observe(int slot,long handle,long tick,float delta,float oldBody,float body,float oldHead,float head,float oldPitch,float pitch,
                    float walk,float speed,float oldSpeed,float oldSwing,float swing){
        if(slot>=0&&slot<observed.length)observed[slot]=Long.MIN_VALUE;
        if(slot<0||slot>=current.length||handle<=0||handle>MAX_GPU_ID||!Float.isFinite(delta)||delta<0||delta>1)return false;
        scratch[0]=oldBody;scratch[1]=body;scratch[2]=oldHead;scratch[3]=head;scratch[4]=oldPitch;scratch[5]=pitch;scratch[6]=walk;scratch[7]=speed;scratch[8]=oldSpeed;scratch[9]=(float)tick;scratch[10]=oldSwing;scratch[11]=swing;
        for(int i=12;i<TICK_WORDS;i++)scratch[i]=0;for(float value:scratch)if(!Float.isFinite(value))return false;
        if(Math.abs(body-oldBody)>4096||Math.abs(head-oldHead)>4096||oldSwing<0||oldSwing>1||swing<0||swing>1)return false;
        Tick old=current[slot];
        if(old!=null&&old.handle==handle&&old.tick==tick&&old.same(scratch))tickReuses++;
        else{if(revision==Long.MAX_VALUE)throw new IllegalStateException("Actor tick revision exhausted");if(old==null||old.handle!=handle)previous[slot]=null;else if(old.tick!=tick)previous[slot]=old;current[slot]=new Tick(handle,tick,++revision,scratch);tickWrites++;}
        observed[slot]=batch;partial[slot]=delta;return true;
    }
    boolean canCapture(int slot,long handle){return sealed==null&&slot>=0&&slot<current.length&&observed[slot]==batch&&current[slot]!=null&&current[slot].handle==handle;}
    private boolean store(int slot,long handle,int job,Matrix4fc root,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,int options,float reserved0,float reserved1){
        if(!canCapture(slot,handle)||job<0||job>=MAX_ROWS||left<0||left>255||right<0||right>255||options<0||options>8191||template<0||!Float.isFinite(swing)||!Float.isFinite(reserved0)||!Float.isFinite(reserved1))return false;
        int offset=job*32;for(int column=0;column<4;column++)for(int row=0;row<4;row++){float value=root.get(column,row);if(!Float.isFinite(value))return false;words[offset+column*4+row]=value;}
        rowTicks[job]=current[slot];rowPartial[job]=partial[slot];rowHandles[job]=handle;rowSlots[job]=slot;materials[job]=0;present[job]=true;discarded[job]=false;used=Math.max(used,job+1);
        words[offset+21]=swing;words[offset+22]=sneak?1:0;words[offset+23]=bow?1:0;words[offset+24]=left;words[offset+25]=right;words[offset+26]=riding?1:0;words[offset+27]=template;words[offset+28]=options;words[offset+29]=-1;words[offset+30]=reserved0;words[offset+31]=reserved1;frameCaptures++;return true;
    }
    boolean capture(int slot,long handle,int job,Matrix4fc root,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,int options){return capture(slot,handle,job,root,walk,speed,age,yaw,pitch,swing,left,right,sneak,riding,bow,template,options,0,0);}
    boolean capture(int slot,long handle,int job,Matrix4fc root,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,int options,float reserved0,float reserved1){
        if(!canCapture(slot,handle))return false;Tick tick=current[slot];float delta=partial[slot];captureValidationRows++;
        if(!same(walk,walk(tick,delta))||!same(speed,speed(tick,delta))||!same(age,age(tick,delta))||!same(yaw,yaw(tick,delta))||!same(pitch,pitch(tick,delta))){poseMismatches++;return false;}
        if(!store(slot,handle,job,root,swing,left,right,sneak,riding,bow,template,options,reserved0,reserved1))return false;int offset=job*32;
        words[offset+16]=walk;words[offset+17]=speed;words[offset+18]=age;words[offset+19]=yaw;words[offset+20]=pitch;exact[job]=true;return true;
    }
    boolean captureGpuOnly(int slot,long handle,int job,Matrix4fc root,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,int options,float reserved0,float reserved1){
        if(!store(slot,handle,job,root,swing,left,right,sneak,riding,bow,template,options,reserved0,reserved1))return false;exact[job]=false;gpuOnlyCaptures++;return true;
    }
    static float angle(float old,float value,float delta){float difference=value-old;while(difference< -180)difference+=360;while(difference>=180)difference-=360;return old+delta*difference;}
    private static float walk(Tick t,float d){return t.word(6)-t.word(7)*(1-d);}
    private static float speed(Tick t,float d){return Math.min(1,t.word(8)+(t.word(7)-t.word(8))*d);}
    private static float age(Tick t,float d){return t.word(9)+d;}
    private static float yaw(Tick t,float d){return angle(t.word(2),t.word(3),d)-angle(t.word(0),t.word(1),d);}
    private static float pitch(Tick t,float d){return t.word(4)+(t.word(5)-t.word(4))*d;}
    private static boolean same(float a,float b){return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);}
    boolean present(int job){return job>=0&&job<used&&present[job];}
    boolean active(int job){return present(job)&&!discarded[job];}
    boolean gpuReady(int job){return active(job)&&materials[job]>0;}
    int materialId(int job){return present(job)?materials[job]:0;}
    Tick current(int slot){return current[slot];}
    Tick previous(int slot){return previous[slot];}
    int capacity(){return current.length;}
    long epoch(){return epoch;}
    Batch seal(){if(sealed==null){sealed=new Batch(this);sealedViews++;}return sealed;}
    void cpu(int job,FloatBuffer target,int offset){
        if(!present(job))throw new IllegalArgumentException("Missing actor arena row");for(int i=0;i<32;i++)target.put(offset+i,words[job*32+i]);
        if(!exact[job]){Tick tick=rowTicks[job];float delta=rowPartial[job];target.put(offset+16,walk(tick,delta)).put(offset+17,speed(tick,delta)).put(offset+18,age(tick,delta)).put(offset+19,yaw(tick,delta)).put(offset+20,pitch(tick,delta));cpuInterpolationRows++;}
    }
    private void input(int job,FloatBuffer target){
        if(!present(job))throw new IllegalArgumentException("Missing actor arena input");int offset=job*32;
        target.put(words,offset,16).put(rowPartial[job]).put(rowSlots[job]).put(job).put(materials[job]).put(words[offset+27]);
        int flags=(words[offset+22]!=0?1:0)|(words[offset+23]!=0?2:0)|(words[offset+26]!=0?4:0)|((int)words[offset+28]<<3);
        target.put(flags).put((int)words[offset+24]|((int)words[offset+25]<<8)).put((float)rowHandles[job]).put(words[offset+30]).put(words[offset+31]).put(words[offset+21]);
    }
    boolean material(int job,int id){if(!active(job)||id<0)return false;if(materials[job]==id)return true;if(sealed!=null)return false;materials[job]=id;materialChanges++;return true;}
    boolean variant(int source,int target,int template){
        if(sealed!=null||!active(source)||target<0||target>=MAX_ROWS)return false;
        System.arraycopy(words,source*32,words,target*32,32);words[target*32+27]=template;rowTicks[target]=rowTicks[source];rowPartial[target]=rowPartial[source];rowHandles[target]=rowHandles[source];rowSlots[target]=rowSlots[source];materials[target]=materials[source];present[target]=true;discarded[target]=false;exact[target]=exact[source];used=Math.max(used,target+1);return true;
    }
    void discard(int job){if(present(job)){if(sealed!=null)discarded[job]=true;else{present[job]=false;rowTicks[job]=null;}}}
    void clear(){if(epoch==Long.MAX_VALUE)throw new IllegalStateException("Actor scene lifetime epoch exhausted");epoch++;Arrays.fill(previous,null);Arrays.fill(current,null);Arrays.fill(observed,Long.MIN_VALUE);begin(batch+1);}
}
