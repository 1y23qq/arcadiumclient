// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.Field;
import java.util.*;

public final class CeraAdmission072LeaseEvidence {
    private static final IdentityHashMap<Object,Arm> arms=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,Use> pending=new IdentityHashMap<>();
    private static final Map<String,Object> prepared=new HashMap<>(),used=new HashMap<>();
    private static final Map<String,Long> counters=new TreeMap<>();
    private static final Map<String,Object> facts=new TreeMap<>();
    private record Arm(Object main,Object parts,int mask,Object texture,int source,int variant,long epoch,Object fact){}
    private record Use(Arm arm,Object main,Object parts,int mask,Object texture,int source,long epoch){}
    private CeraAdmission072LeaseEvidence(){}
    public static void reset(){arms.clear();pending.clear();prepared.clear();used.clear();counters.clear();facts.clear();}
    public static Map<String,Long> counts(){return new TreeMap<>(counters);}
    public static Map<String,Object> facts(){return new TreeMap<>(facts);}
    public static void prepared(Object texture,Object fact,boolean accepted){
        if(!CeraAdmission072Qa.observingSelection()||!accepted)return;
        try{String name=String.valueOf(texture);if(prepared.size()>=64&&!prepared.containsKey(name)){add("overflow");return;}prepared.put(name,fact);
            facts.put(name,Map.of("identity",System.identityHashCode(fact),"texture",value(fact,"texture"),"generation",value(fact,"generation"),"revision",value(fact,"revision")));add("prepared:"+name);
        }catch(ReflectiveOperationException failure){add("unknownFactLayout");}
    }
    public static void arm(Object lease,Object main,Object parts,int mask,Object texture,int source,int variant,long epoch,Object fact){
        if(!CeraAdmission072Qa.observingSelection())return;String name=String.valueOf(texture);
        if(arms.size()>=64&&!arms.containsKey(lease)){add("overflow");return;}
        if(fact==null||prepared.get(name)!=fact){add("unpreparedFact");return;}
        arms.put(lease,new Arm(main,parts,mask,texture,source,variant,epoch,fact));add("armed:"+name);
    }
    public static void consume(Object lease,Object main,Object parts,int mask,Object texture,int source,long epoch){
        if(!CeraAdmission072Qa.observingSelection())return;if(pending.size()>=64&&!pending.containsKey(lease)){add("overflow");return;}
        pending.put(lease,new Use(arms.remove(lease),main,parts,mask,texture,source,epoch));
    }
    public static void returned(Object lease,int variant){
        if(!CeraAdmission072Qa.observingSelection())return;Use use=pending.remove(lease);Arm arm=use==null?null:use.arm;String name=use==null?"unknown":String.valueOf(use.texture);
        if(arm==null||variant<0||variant!=arm.variant||arm.main!=use.main||arm.parts!=use.parts||arm.mask!=use.mask||!Objects.equals(arm.texture,use.texture)||arm.source!=use.source||arm.epoch!=use.epoch||prepared.get(name)!=arm.fact){add("invalidUse");return;}
        used.put(name,arm);add("used:"+name);
    }
    public static void queued(Object texture,int mask){
        if(!CeraAdmission072Qa.observingSelection()||mask==0)return;String name=String.valueOf(texture);
        if(used.get(name)==null){add("queueWithoutUse");return;}used.remove(name);add("queued:"+name);
    }
    private static void add(String name){if(counters.size()<256||counters.containsKey(name))counters.merge(name,1L,Long::sum);}
    public static Object value(Object target,String name)throws ReflectiveOperationException{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
