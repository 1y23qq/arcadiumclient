// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.BitSet;

final class ActorFusedInput {
    static final int MAX=8192,WORDS=57;static final long MAX_BYTES=3L*MAX*WORDS*4;
    private Object owner;private long generation;private int capacity,rangeCount;
    private int[] words=new int[0],scratch=new int[0],ranges=new int[0],scratchRanges=new int[0],touched=new int[0];
    private ActorSceneData.Tick[] ticks=new ActorSceneData.Tick[0];
    long rowVisits,tickVisits,overrideWords,cachedOverrideWords;
    View prepare(Object context,ActorSceneData.Raw raw,FloatBuffer legacy,int rows,ActorFusedRows selected){
        if(context==null||owner!=null&&owner!=context||raw==null||legacy==null||rows<0||rows>MAX||legacy.limit()<rows*32||generation==Long.MAX_VALUE)return null;
        raw.check();int needed=1;
        for(int i=0;i<selected.count();i++){int row=selected.at(i);if(row>=rows)return null;needed=Math.max(needed,row+1);if(raw.active(row)&&!raw.exact(row)){int slot=raw.slot(row);var tick=raw.tick(row);float delta=raw.partial(row);if(slot<0||slot>=MAX||tick==null||tick.handle<=0||!Float.isFinite(delta)||delta<0||delta>1)return null;needed=Math.max(needed,slot+1);}}
        int nextCapacity=Math.max(capacity,needed==1?1:Integer.highestOneBit(needed-1)<<1);if(nextCapacity>MAX)return null;
        boolean grow=nextCapacity!=capacity;int[] next=grow?new int[nextCapacity*WORDS]:scratch;
        int[] nextRanges=grow?new int[(nextCapacity*3+2)*2]:scratchRanges;ActorSceneData.Tick[] known=grow?new ActorSceneData.Tick[nextCapacity]:ticks;int[] touchedSlots=grow?new int[nextCapacity]:touched;
        int count=0,slotCount=0,tagged=0,metaBase=nextCapacity*32,tickBase=nextCapacity*36,overrideBase=nextCapacity*52;boolean slotsOrdered=true;
        try{
            for(int i=0;i<selected.count();i++){
                int row=selected.at(i),p=row*32,m=metaBase+row*4;boolean active=raw.active(row),tag=active&&!raw.exact(row);
                if(active)raw.copyWords(row,next,p);else for(int w=0;w<32;w++)next[p+w]=Float.floatToRawIntBits(legacy.get(p+w));
                for(int w=0;w<4;w++)next[m+w]=0;count=append(nextRanges,count,p,32);rowVisits++;
                if(!tag)continue;
                for(int w=0;w<16;w++)if(!Float.isFinite(Float.intBitsToFloat(next[p+w])))return null;
                if(next[p+29]!=Float.floatToRawIntBits(-1)||!Float.isFinite(Float.intBitsToFloat(next[p+21])))return null;
                int slot=raw.slot(row);var tick=raw.tick(row);if(known[slot]!=null&&known[slot]!=tick)return null;if(known[slot]==null){known[slot]=tick;if(slotCount>0&&slot<touchedSlots[slotCount-1])slotsOrdered=false;touchedSlots[slotCount++]=slot;}
                float delta=raw.partial(row);int mask=tick.arithmeticMask;if(Float.floatToRawIntBits(delta)==0x80000000)mask|=31;else if(delta>0&&delta<0x1p-24f)mask|=30;
                int tail=overrideBase+row*5,cursor=tail;next[m]=1;next[m+1]=tickBase+slot*16;next[m+2]=Float.floatToRawIntBits(delta);next[m+3]=mask==0?0:(tail<<5)|mask;
                for(int w=16;w<=20;w++)next[p+w]=0;
                for(int w=0;w<5;w++)if((mask&(1<<w))!=0){float value;if(w==1&&tick.constantSpeed){value=Float.intBitsToFloat(tick.constantSpeedBits);cachedOverrideWords++;}else{value=pose(tick,delta,w);overrideWords++;}next[cursor++]=Float.floatToRawIntBits(value);}while(cursor<tail+5)next[cursor++]=0;tagged++;
            }
            for(int i=0;i<selected.count();i++)count=append(nextRanges,count,metaBase+selected.at(i)*4,4);
            if(!slotsOrdered)Arrays.sort(touchedSlots,0,slotCount);for(int i=0;i<slotCount;i++){int slot=touchedSlots[i],p=tickBase+slot*16;for(int w=0;w<16;w++)next[p+w]=Float.floatToRawIntBits(known[slot].word(w));count=append(nextRanges,count,p,16);tickVisits++;}
            for(int i=0;i<selected.count();i++){int row=selected.at(i);if(next[metaBase+row*4]==1)count=append(nextRanges,count,overrideBase+row*5,5);}
            raw.check();int[] previous=words,priorRanges=ranges;words=next;scratch=grow?new int[nextCapacity*WORDS]:previous;ranges=nextRanges;scratchRanges=grow?new int[nextRanges.length]:priorRanges;capacity=nextCapacity;rangeCount=count;ticks=known;touched=touchedSlots;owner=context;
            return new View(this,context,raw.lifetime(),raw.batch(),++generation,nextCapacity,tagged);
        }finally{for(int i=0;i<slotCount;i++)known[touchedSlots[i]]=null;}
    }
    private static int append(int[] target,int count,int start,int length){if(count>0&&target[(count-1)*2]+target[(count-1)*2+1]==start){target[(count-1)*2+1]+=length;return count;}target[count*2]=start;target[count*2+1]=length;return count+1;}
    void release(Object context){if(owner!=null&&owner!=context)throw new IllegalStateException("Foreign fused CPU arena release");owner=null;generation++;capacity=rangeCount=0;words=scratch=ranges=scratchRanges=touched=new int[0];ticks=new ActorSceneData.Tick[0];}
    private static float angle(float a,float b,float d){float x=b-a;while(x< -180)x+=360;while(x>=180)x-=360;return a+d*x;}
    private static float pose(ActorSceneData.Tick t,float d,int w){return switch(w){case 0->t.word(6)-t.word(7)*(1-d);case 1->Math.min(1,t.word(8)+(t.word(7)-t.word(8))*d);case 2->t.word(9)+d;case 3->angle(t.word(2),t.word(3),d)-angle(t.word(0),t.word(1),d);default->t.word(4)+(t.word(5)-t.word(4))*d;};}
    static final class View {
        private final ActorFusedInput source;final Object context;final long world,batch,generation;final int capacity,metadataBase,taggedRows;
        View(ActorFusedInput source,Object context,long world,long batch,long generation,int capacity,int tagged){this.source=source;this.context=context;this.world=world;this.batch=batch;this.generation=generation;this.capacity=capacity;metadataBase=capacity*32;taggedRows=tagged;}
        void check(){if(source.owner!=context||source.generation!=generation)throw new IllegalStateException("Stale fused input view");}
        int word(int index){check();if(index<0||index>=capacity*WORDS)throw new IndexOutOfBoundsException();return source.words[index];}
        int ranges(){check();return source.rangeCount;}int start(int range){check();return source.ranges[range*2];}int length(int range){check();return source.ranges[range*2+1];}
    }
    static final class Shadow {
        private static final int MAX_CLEAN_GAP=16;
        private int[] words=new int[0],ranges=new int[0];private final BitSet valid=new BitSet();private Object owner;private long world=Long.MIN_VALUE,plan;private boolean invalid=true;
        private Object committedContext,committedSource;private long committedWorld,committedBatch,committedGeneration,committedPlan=Long.MIN_VALUE;private int committedCapacity;
        void invalidate(){invalid=true;valid.clear();committedContext=committedSource=null;committedPlan=Long.MIN_VALUE;plan++;}
        Diff diff(View view){
            view.check();if(owner!=null&&owner!=view.context)throw new IllegalStateException("Foreign fused diff shadow");
            boolean fresh=invalid||words.length!=view.capacity*WORDS||world!=view.world;
            boolean exact=!fresh&&committedContext==view.context&&committedSource==view.source&&committedWorld==view.world&&committedBatch==view.batch
                &&committedGeneration==view.generation&&committedCapacity==view.capacity&&committedPlan==plan;
            if(exact)return new Diff(this,view,0,false,++plan,0,0,0,true);
            if(ranges.length<view.capacity*WORDS*2)ranges=new int[view.capacity*WORDS*2];int count=0,dirty=0,transmitted=0,compared=0;
            for(int r=0;r<view.ranges();r++){
                int start=view.start(r),end=start+view.length(r),first=-1,last=-1;
                if(fresh){ranges[count*2]=start;ranges[count*2+1]=end-start;count++;dirty+=end-start;transmitted+=end-start;continue;}
                for(int p=start;p<end;p++){
                    compared++;
                    if(valid.get(p)&&words[p]==view.word(p))continue;
                    dirty++;
                    if(first>=0&&p-last-1>MAX_CLEAN_GAP){ranges[count*2]=first;ranges[count*2+1]=last-first+1;count++;transmitted+=last-first+1;first=-1;}
                    if(first<0)first=p;last=p;
                }
                if(first>=0){ranges[count*2]=first;ranges[count*2+1]=last-first+1;count++;transmitted+=last-first+1;}
            }
            return new Diff(this,view,count,fresh,++plan,dirty,transmitted,compared,false);
        }
    }
    static final class Diff {
        private final Shadow target;private final View view;private final boolean fresh;private final long plan;final int ranges,dirtyWords,transmittedWords,comparedWords;final boolean exactCommittedViewReuse;
        Diff(Shadow target,View view,int ranges,boolean fresh,long plan,int dirty,int transmitted,int compared,boolean exact){this.target=target;this.view=view;this.ranges=ranges;this.fresh=fresh;this.plan=plan;dirtyWords=dirty;transmittedWords=transmitted;comparedWords=compared;exactCommittedViewReuse=exact;}
        private void check(){view.check();if(plan!=target.plan)throw new IllegalStateException("Superseded fused upload plan");}
        int start(int i){check();return target.ranges[i*2];}int length(int i){check();return target.ranges[i*2+1];}
        void commit(){check();if(target.words.length!=view.capacity*WORDS)target.words=new int[view.capacity*WORDS];if(fresh)target.valid.clear();for(int r=0;r<ranges;r++)for(int p=start(r),end=p+length(r);p<end;p++){target.words[p]=view.word(p);target.valid.set(p);}target.world=view.world;target.owner=view.context;target.invalid=false;
            target.committedContext=view.context;target.committedSource=view.source;target.committedWorld=view.world;target.committedBatch=view.batch;target.committedGeneration=view.generation;target.committedCapacity=view.capacity;target.committedPlan=plan;}
    }
}
