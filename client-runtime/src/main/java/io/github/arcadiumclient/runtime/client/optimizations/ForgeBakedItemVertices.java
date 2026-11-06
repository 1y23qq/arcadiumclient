// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.render.model.block.BakedQuad;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexFormat;

public final class ForgeBakedItemVertices {
    private static final String UNPACKED="net.minecraftforge.client.model.pipeline.UnpackedBakedQuad";
    private static final ThreadLocal<int[]> DEPTH=ThreadLocal.withInitial(()->new int[1]);
    private static final VarHandle BACKING=backing();
    private static MethodHandle formatGetter;
    private static final Rejected REJECTED=new Rejected();
    public static long materializedQuads,verifiedQuads,nativeModelFallbacks;
    private ForgeBakedItemVertices(){}

    public static int begin(){int[] depth=DEPTH.get();if(depth[0]==8)throw REJECTED;return ++depth[0];}
    public static void end(int token){int[] depth=DEPTH.get();if(token!=depth[0])throw new IllegalStateException("Item quad construction scope changed");depth[0]--;}
    public static Object consume(Object value){
        if(DEPTH.get()[0]==0)return value;
        if(!(value instanceof BakedQuad quad)||BACKING==null)throw REJECTED;
        int[] before=(int[])BACKING.get(quad);
        if(before==null||before.length!=28)throw REJECTED;
        if(quad.getClass()==BakedQuad.class)return quad;
        if(!quad.getClass().getName().equals(UNPACKED))throw REJECTED;
        try{
            materialize(quad,format(quad));return quad;
        }catch(Rejected unsupported){throw unsupported;}
        catch(Error fatal){throw fatal;}
        catch(Throwable unsupported){throw REJECTED;}
    }
    static void materialize(BakedQuad quad,Object format){
        if(BACKING==null||!(format instanceof VertexFormat layout)||layout.getIntSize()!=7||!layout.equals(DefaultVertexFormat.BLOCK_NORMALS))throw REJECTED;
        int[] before=(int[])BACKING.get(quad);if(before==null||before.length!=28)throw REJECTED;
        int[] packed=quad.getVertices();if(packed!=(int[])BACKING.get(quad)||packed.length!=28)throw REJECTED;
        materializedQuads++;verifiedQuads++;
    }
    private static Object format(Object quad)throws Throwable{
        if(formatGetter==null){Class<?> type=quad.getClass();Field field=type.getDeclaredField("format");formatGetter=MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectGetter(field).asType(MethodType.methodType(Object.class,Object.class));}
        return (Object)formatGetter.invokeExact((Object)quad);
    }
    private static VarHandle backing(){
        for(String name:new String[]{"vertices","f_40952693"})try{return MethodHandles.privateLookupIn(BakedQuad.class,MethodHandles.lookup()).findVarHandle(BakedQuad.class,name,int[].class);}catch(ReflectiveOperationException ignored){}
        return null;
    }
    public static final class Rejected extends RuntimeException {
        private Rejected(){super("Unsupported native item quad; preserve the original item renderer",null,false,false);}
    }
    public static final class Rejections {
        private final WeakReference<?>[] models=new WeakReference<?>[128];
        private int size;
        public boolean contains(Object model){for(int i=0;i<size;){Object value=models[i].get();if(value==null){models[i]=models[--size];models[size]=null;}else{if(value==model)return true;i++;}}return false;}
        public void add(Object model){if(contains(model))return;if(size==models.length){System.arraycopy(models,1,models,0,--size);models[size]=null;}models[size++]=new WeakReference<>(model);nativeModelFallbacks++;}
        public void clear(){Arrays.fill(models,null);size=0;}
        public int size(){return size;}
    }
}
