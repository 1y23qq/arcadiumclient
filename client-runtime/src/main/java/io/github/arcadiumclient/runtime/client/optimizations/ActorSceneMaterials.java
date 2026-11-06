// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resource.Identifier;

final class ActorSceneMaterials {
    private static final int MAX=1024;
    private static final class Key {
        final Object pool,fact;
        final int layer,hash;
        Key(Object pool,Object fact,int layer){this.pool=pool;this.fact=fact;this.layer=layer;hash=31*(31*System.identityHashCode(pool)+System.identityHashCode(fact))+layer;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Key key&&pool==key.pool&&fact==key.fact&&layer==key.layer;}
    }
    private final HashMap<Key,Integer> identities=new HashMap<>();
    private Access access;
    long checks,reuses,created,fallbacks;
    int selected(Object main,Object batch,Identifier texture,int selected){
        checks++;
        try{
            if(main==null||batch==null||texture==null||selected<0||selected>255)return fallback();
            if(access==null)access=new Access();
            Object pool=access.batchPool.get(batch);if(pool==null)return fallback();
            Object layer=((Map<?,?>)access.layers.get(pool)).get(texture);
            if(layer==null||(int)access.index.get(layer)!=selected||(int)access.layerFrame.get(layer)!=(int)access.modelFrame.get(main))return fallback();
            Object source=access.source.get(layer);int name=(int)access.sourceId.get(layer),generation=(int)access.generation.get(layer);
            var fact=GpuVoxelSkinUploadFacts.current(source,name,generation);
            if(fact==null||!GpuVoxelSkinUploadFacts.matches(pool,selected,fact))return fallback();
            return identity(pool,fact,selected);
        }catch(Throwable unsupported){return fallback();}
    }
    int identity(Object pool,Object fact,int layer){
        if(pool==null||fact==null||layer<0||layer>255)return fallback();
        Key key=new Key(pool,fact,layer);Integer known=identities.get(key);if(known!=null){reuses++;return known;}
        if(identities.size()==MAX)return fallback();int id=identities.size()+1;identities.put(key,id);created++;return id;
    }
    private int fallback(){fallbacks++;return 0;}
    int size(){return identities.size();}
    void clear(){identities.clear();}
    private static final class Access {
        final VarHandle batchPool,layers,index,layerFrame,modelFrame,source,sourceId,generation;
        Access()throws ReflectiveOperationException{
            String root="dev.rdh.argentum.";
            Class<?> model=Class.forName(root+"impl.render.entity.instancing.ModelInstancer");
            Class<?> batch=Class.forName(root+"impl.render.entity.instancing.InstanceBatcher$TextureBatch");
            Class<?> pool=Class.forName(root+"api.render.instancing.TextureArrayManager$Pool"),layer=Class.forName(root+"api.render.instancing.TextureArrayManager$Layer");
            batchPool=field(batch,"pool");layers=field(pool,"layers");index=field(layer,"index");layerFrame=field(layer,"frame");modelFrame=field(model,"frame");
            source=field(layer,"source");sourceId=field(layer,"sourceId");generation=field(layer,"generation");
        }
        private static VarHandle field(Class<?> owner,String name)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).unreflectVarHandle(owner.getDeclaredField(name));}
    }
}
