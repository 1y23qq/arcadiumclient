// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.awt.image.BufferedImage;
import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.IntSupplier;

public final class GpuVoxelSkinUploadFacts {
    static final int MAX_SOURCES=1024,MAX_POOLS=16,MAX_LAYERS=256;
    static final class Fact {
        final int texture,generation;
        final long revision;
        final byte[] alpha;
        Fact(int texture,int generation,long revision,byte[] alpha){this.texture=texture;this.generation=generation;this.revision=revision;this.alpha=alpha;}
    }
    private static final class PoolFacts {
        final long[] revisions=new long[MAX_LAYERS];
        final int[] textures=new int[MAX_LAYERS],generations=new int[MAX_LAYERS];
    }
    private static final GpuVoxelSkinWeakIdentityMap<Fact> sources=new GpuVoxelSkinWeakIdentityMap<>(MAX_SOURCES);
    private static final GpuVoxelSkinWeakIdentityMap<PoolFacts> pools=new GpuVoxelSkinWeakIdentityMap<>(MAX_POOLS);
    private static final HashMap<Integer,WeakReference<Object>> byTexture=new HashMap<>();
    private static final ClassValue<MethodHandle[]> generations=new ClassValue<>(){
        @Override protected MethodHandle[] computeValue(Class<?> type){
            try{var getter=type.getMethod("argentum$getGeneration");if(getter.getReturnType()!=int.class)return new MethodHandle[0];
                return new MethodHandle[]{MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(getter).asType(MethodType.methodType(int.class,Object.class))};
            }catch(ReflectiveOperationException failure){return new MethodHandle[0];}
        }
    };
    private static long revision;
    public static long uploadAttempts,published,uploadFailures,budgetFallbacks,unsupported,invalidations,nativeCopies,copiesWithoutFacts,copyBudgetFallbacks;
    private GpuVoxelSkinUploadFacts(){}

    public static int upload(Object source,int texture,BufferedImage image,IntSupplier original){
        byte[] alpha=null;
        synchronized(GpuVoxelSkinUploadFacts.class){uploadAttempts++;}
        if(source!=null&&texture>0&&image!=null&&image.getWidth()==64&&image.getHeight()==64&&sources.canAdmit(source)){
            try{int[] argb=image.getRGB(0,0,64,64,null,0,64);alpha=new byte[4096];for(int i=0;i<4096;i++)alpha[i]=(byte)(argb[i]>>>24);}
            catch(RuntimeException unavailable){synchronized(GpuVoxelSkinUploadFacts.class){unsupported++;}}
        }
        int result;
        try{result=original.getAsInt();}
        catch(RuntimeException|Error failure){invalidate(source);synchronized(GpuVoxelSkinUploadFacts.class){uploadFailures++;}throw failure;}
        int generation=generation(source);
        synchronized(GpuVoxelSkinUploadFacts.class){
            if(alpha==null||result!=texture||generation==Integer.MIN_VALUE){invalidate(source);unsupported++;return result;}
            Fact old=sources.get(source),fact=new Fact(texture,generation,++revision,alpha);
            if(!sources.put(source,fact)){budgetFallbacks++;return result;}
            if(old!=null&&old.texture!=texture)removeIndex(old.texture,source);
            cleanIndex();
            if(!byTexture.containsKey(texture)&&byTexture.size()==MAX_SOURCES){sources.remove(source);budgetFallbacks++;return result;}
            byTexture.put(texture,new WeakReference<>(source));published++;
        }
        return result;
    }
    static synchronized Fact current(Object source,int texture,int generation){
        Fact fact=sources.get(source);return fact!=null&&fact.texture==texture&&fact.generation==generation?fact:null;
    }
    public static synchronized void invalidate(Object source){
        if(source==null)return;Fact old=sources.remove(source);if(old!=null){removeIndex(old.texture,source);invalidations++;}
    }
    public static synchronized void copied(Object pool,int texture,int layer,boolean successful){
        if(pool==null||layer<0||layer>=MAX_LAYERS)return;nativeCopies++;
        PoolFacts tags=pools.get(pool);
        WeakReference<Object> reference=byTexture.get(texture);Object source=reference==null?null:reference.get();
        Fact fact=source==null?null:sources.get(source);
        if(!successful||fact==null||fact.texture!=texture||generation(source)!=fact.generation){
            copiesWithoutFacts++;if(tags!=null)tags.revisions[layer]=0;return;
        }
        if(tags==null){tags=new PoolFacts();if(!pools.put(pool,tags)){copyBudgetFallbacks++;return;}}
        tags.revisions[layer]=fact.revision;tags.textures[layer]=texture;tags.generations[layer]=fact.generation;
    }
    static synchronized boolean matches(Object pool,int layer,Object identity){
        if(!(identity instanceof Fact fact)||layer<0||layer>=MAX_LAYERS)return false;PoolFacts tags=pools.get(pool);
        return tags!=null&&tags.revisions[layer]==fact.revision&&tags.textures[layer]==fact.texture&&tags.generations[layer]==fact.generation;
    }
    public static synchronized void releaseManager(Object manager){
        if(manager==null)return;
        try{for(Object pool:((Map<?,?>)field(manager.getClass(),"pools").get(manager)).values())pools.remove(pool);}
        catch(ReflectiveOperationException unknown){unsupported++;}
    }
    public static synchronized void shutdown(){sources.clear();pools.clear();byTexture.clear();}
    public static synchronized int retainedSources(){return sources.size();}
    public static synchronized int retainedPools(){return pools.size();}
    public static synchronized int retainedTextureIds(){cleanIndex();return byTexture.size();}
    public static synchronized long retainedAlphaBytes(){return (long)sources.size()*4096;}
    static int generation(Object source){
        if(source==null)return Integer.MIN_VALUE;
        try{MethodHandle[] getters=generations.get(source.getClass());return getters.length==0?Integer.MIN_VALUE:(int)getters[0].invokeExact(source);}
        catch(Throwable unknown){return Integer.MIN_VALUE;}
    }
    private static void removeIndex(int id,Object source){WeakReference<Object> ref=byTexture.get(id);if(ref!=null&&ref.get()==source)byTexture.remove(id);}
    private static void cleanIndex(){byTexture.entrySet().removeIf(entry->{Object source=entry.getValue().get();Fact fact=source==null?null:sources.get(source);return fact==null||fact.texture!=entry.getKey();});}
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException{
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
