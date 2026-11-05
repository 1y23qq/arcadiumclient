// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.*;
import net.minecraft.resource.Identifier;

public final class CeraAdmission072PoolEvidence {
    private static final int MAX_POOLS=8,MAX_BINDINGS=2048,MAX_NAMES=64,MAX_DEPTH=64;
    private static final IdentityHashMap<Object,Pool> pools=new IdentityHashMap<>();
    private static final Map<Long,String> sources=new HashMap<>();
    private static final Map<String,Long> counters=new TreeMap<>(),drawn=new TreeMap<>();
    private static final Set<String> names=new HashSet<>();
    private static final Deque<Frame> frames=new ArrayDeque<>();
    private static int overflowDepth;
    private static final Frame INACTIVE=new Frame(null,null,null,null,null,0,0,0,false);
    private static final class Pool{long copies;}
    private record Frame(Object pool,Pool stats,String name,Object previous,Object source,int sourceId,int generation,long copies,boolean matches){}
    private CeraAdmission072PoolEvidence(){}

    public static void reset(){pools.clear();sources.clear();counters.clear();drawn.clear();names.clear();frames.clear();overflowDepth=0;}
    public static Map<String,Long> counts(){return Collections.unmodifiableMap(new TreeMap<>(counters));}
    public static Map<String,Long> drawnCounts(){return Collections.unmodifiableMap(new TreeMap<>(drawn));}
    public static String sourceFor(int array,int layer){return sources.get(key(array,layer));}

    public static void head(Object pool,Identifier texture,Object source,int sourceId,int generation,int frame){
        if(overflowDepth!=0||frames.size()==MAX_DEPTH){overflowDepth++;add(counters,"depthOverflow",1);return;}
        if(!CeraAdmission072Qa.analyticScope()){frames.push(INACTIVE);return;}
        try{
            String name=texture.toString();if(!names.contains(name)&&names.size()==MAX_NAMES){add(counters,"nameOverflow",1);frames.push(INACTIVE);return;}names.add(name);
            Pool stats=pools.get(pool);if(stats==null){if(pools.size()==MAX_POOLS){add(counters,"poolOverflow",1);frames.push(INACTIVE);return;}stats=new Pool();pools.put(pool,stats);}
            Object previous=((Map<?,?>)value(pool,"layers")).get(texture);
            boolean same=previous!=null&&value(previous,"source")==source&&(int)value(previous,"sourceId")==sourceId&&(int)value(previous,"generation")==generation;
            frames.push(new Frame(pool,stats,name,previous,source,sourceId,generation,stats.copies,same));add(counters,"calls",1);add(counters,"calls:"+name,1);
        }catch(Exception mismatch){add(counters,"unknownLayout",1);frames.push(INACTIVE);}
    }
    public static void returned(Object pool,Object layer){
        if(overflowDepth!=0){overflowDepth--;return;}
        if(frames.isEmpty()){add(counters,"unbalanced",1);return;}
        Frame frame=frames.pop();if(frame==INACTIVE)return;
        if(frame.pool!=pool){add(counters,"unbalanced",1);return;}
        if(layer==null){add(counters,"nullSelections",1);return;}
        try{
            int array=(int)value(pool,"texture"),index=(int)value(layer,"index");
            if(array<=0||index<0||index>=256){add(counters,"unknownLayout",1);return;}
            long binding=key(array,index);if(!sources.containsKey(binding)&&sources.size()==MAX_BINDINGS){add(counters,"bindingOverflow",1);return;}sources.put(binding,frame.name);
            boolean descriptor=value(layer,"source")==frame.source&&(int)value(layer,"sourceId")==frame.sourceId&&(int)value(layer,"generation")==frame.generation;
            if(layer==frame.previous&&frame.matches&&descriptor&&frame.stats.copies==frame.copies){add(counters,"cacheHits",1);add(counters,"hits:"+frame.name,1);add(counters,frame.name,1);}
            else{add(counters,"nonHitSelections",1);add(counters,"nonHits:"+frame.name,1);}
        }catch(Exception mismatch){add(counters,"unknownLayout",1);}
    }
    public static void copied(Object pool){
        if(!CeraAdmission072Qa.analyticScope())return;Pool state=pools.get(pool);
        if(state==null){if(pools.size()==MAX_POOLS){add(counters,"poolOverflow",1);return;}state=new Pool();pools.put(pool,state);}
        state.copies++;add(counters,"copies",1);
        if(!frames.isEmpty()&&frames.peek()!=INACTIVE&&frames.peek().pool==pool)add(counters,"copiesDuring:"+frames.peek().name,1);
    }
    public static void recordDraw(int array,IntBuffer payload,int count){
        if(!CeraAdmission072Qa.enabled()||sources.isEmpty())return;
        if(array<=0||payload==null||count<0||count>65536||payload.remaining()!=(long)count*19){add(drawn,"invalidDrawRange",1);return;}
        int base=payload.position();for(int instance=0;instance<count;instance++){
            int layer=(payload.get(base+instance*19+13)>>>16)&255;String name=sourceFor(array,layer);
            if(name==null){add(drawn,"unmappedLayer",1);continue;}
            add(drawn,name,1);add(drawn,"instances",1);
        }
        add(drawn,"calls",1);
    }
    private static long key(int array,int layer){return ((long)array<<32)|(layer&0xFFFFFFFFL);}
    private static void add(Map<String,Long> target,String key,long amount){if(target.size()<512||target.containsKey(key))target.merge(key,amount,Long::sum);}
    private static Object value(Object target,String name)throws ReflectiveOperationException{
        for(Class<?> owner=target.getClass();owner!=null;owner=owner.getSuperclass())try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
