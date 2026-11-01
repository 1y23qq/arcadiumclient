// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.util.*;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.GL11;

final class NameTagPasses {
    record State(List<Object> values) { }
    private final VarHandle count,passes,buffer;
    private final VarHandle[] fields;
    private final MethodHandle constructor;
    private final IdentityHashMap<Object,State> observed=new IdentityHashMap<>();
    private final Map<State,BufferBuilder> restored=new HashMap<>();

    NameTagPasses()throws ReflectiveOperationException {
        Class<?> batch=Class.forName("dev.rdh.argentum.impl.render.entity.NameTagBatch");
        Class<?> pass=Class.forName(batch.getName()+"$Pass");
        count=field(batch,"passCount");passes=field(batch,"passes");buffer=field(pass,"buffer");
        constructor=MethodHandles.privateLookupIn(pass,MethodHandles.lookup()).unreflectConstructor(pass.getDeclaredConstructor());
        String[] names={"texture","depthTest","depthMask","depthFunc","blend","sourceRgb","destinationRgb","sourceAlpha","destinationAlpha","alphaTest","alphaFunc","alphaRef","cull"};
        fields=new VarHandle[names.length];for(int i=0;i<names.length;i++)fields[i]=field(pass,names[i]);
    }
    void begin(){observed.clear();restored.clear();}
    State snapshot(Object pass){
        State state=observed.get(pass);if(state!=null)return state;
        Object[] values=new Object[fields.length];for(int i=0;i<values.length;i++)values[i]=fields[i].get(pass);
        state=new State(List.of(values));observed.put(pass,state);return state;
    }
    @SuppressWarnings("unchecked") BufferBuilder restore(Object batch,State state)throws Throwable {
        BufferBuilder cached=restored.get(state);if(cached!=null)return cached;
        List<Object> list=(List<Object>)passes.get(batch);int active=(int)count.get(batch);Object selected=null;
        for(int i=0;i<active;i++)if(snapshot(list.get(i)).equals(state)){selected=list.get(i);break;}
        if(selected==null){
            if(active==list.size())list.add(constructor.invoke());selected=list.get(active);
            for(int i=0;i<fields.length;i++)fields[i].set(selected,state.values.get(i));
            observed.put(selected,state);count.set(batch,active+1);
            ((BufferBuilder)buffer.get(selected)).begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
        }
        cached=(BufferBuilder)buffer.get(selected);restored.put(state,cached);return cached;
    }
    private static VarHandle field(Class<?> type,String name)throws ReflectiveOperationException{
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));
    }
}
