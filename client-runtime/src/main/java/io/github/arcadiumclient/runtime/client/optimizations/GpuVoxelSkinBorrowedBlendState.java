// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.system.Platform;
import org.lwjgl.opengl.*;

final class GpuVoxelSkinBorrowedBlendState {
    interface Access {
        Object context();
        boolean enabled();
        int factor(int index);
        int equation(boolean alpha);
        boolean cachedEnabled();
        int cachedFactor(int index);
        void restoreCache(boolean enabled,int srcRgb,int dstRgb,int srcAlpha,int dstAlpha);
        void restoreActual(boolean enabled,int srcRgb,int dstRgb,int srcAlpha,int dstAlpha,int rgbEquation,int alphaEquation);
    }
    private static final int LIMIT=4,WORDS=12;
    private final Access access;
    private final int[] data=new int[LIMIT*WORDS];
    private final Object[] contexts=new Object[LIMIT];
    private final Thread[] threads=new Thread[LIMIT];
    private final long[] tickets=new long[LIMIT];
    private int depth;
    private long serial;

    GpuVoxelSkinBorrowedBlendState(Access access){this.access=java.util.Objects.requireNonNull(access);}
    static GpuVoxelSkinBorrowedBlendState live()throws ReflectiveOperationException{return new GpuVoxelSkinBorrowedBlendState(new NativeAccess());}
    boolean available(){return access.context()!=null;}
    int pending(){return depth;}
    long capture(){
        if(depth==LIMIT||serial==Long.MAX_VALUE)throw new IllegalStateException("Auxiliary blend scope budget exceeded");
        Object context=access.context();Thread thread=Thread.currentThread();
        if(context==null||depth!=0&&(contexts[depth-1]!=context||threads[depth-1]!=thread))throw new IllegalStateException("Auxiliary blend scope belongs to another context");
        int offset=depth*WORDS;data[offset]=access.enabled()?1:0;
        for(int i=0;i<4;i++)data[offset+1+i]=access.factor(i);
        data[offset+5]=access.equation(false);data[offset+6]=access.equation(true);data[offset+7]=access.cachedEnabled()?1:0;
        for(int i=0;i<4;i++)data[offset+8+i]=access.cachedFactor(i);
        contexts[depth]=context;threads[depth]=thread;tickets[depth]=++serial;return tickets[depth++];
    }
    void restore(long ticket){
        int index=depth-1;
        if(index<0||tickets[index]!=ticket)throw new IllegalStateException("Auxiliary blend scope order changed");
        if(threads[index]!=Thread.currentThread()||contexts[index]!=access.context())throw new IllegalStateException("Cannot restore auxiliary blend state in another context");
        int offset=index*WORDS;
        try{
            // Restore the cache and driver separately, including a preexisting disagreement.
            try{access.restoreCache(data[offset+7]!=0,data[offset+8],data[offset+9],data[offset+10],data[offset+11]);}
            finally{access.restoreActual(data[offset]!=0,data[offset+1],data[offset+2],data[offset+3],data[offset+4],data[offset+5],data[offset+6]);}
        }finally{contexts[index]=null;threads[index]=null;tickets[index]=0;depth=index;}
    }

    static final class Owner {
        private Object capabilities;
        private Thread thread;
        private long handle;
        Object current(Object currentCapabilities,long currentHandle,Thread currentThread){
            if(currentCapabilities==null||currentHandle==0||currentThread==null)return null;
            if(capabilities==null){capabilities=currentCapabilities;handle=currentHandle;thread=currentThread;}
            return capabilities==currentCapabilities&&handle==currentHandle&&thread==currentThread?capabilities:null;
        }
    }
    private static final class NativeAccess implements Access {
        private final Owner owner=new Owner();
        private final Object blend,state;
        private final VarHandle enabled;
        private final VarHandle[] factors=new VarHandle[4];
        NativeAccess()throws ReflectiveOperationException{
            Field root=field(GlStateManager.class,"BLEND","f_11171919");
            if(!Modifier.isStatic(root.getModifiers()))throw new NoSuchFieldException("Native blend cache is not static");
            blend=handle(root).get();Field nested=field(blend.getClass(),"state","f_71274619");state=handle(nested).get(blend);
            enabled=typed(state.getClass(),"enabled","f_52913165",boolean.class);
            VarHandle cap=typed(state.getClass(),"cap","f_88189650",int.class);
            if((int)cap.get(state)!=GL11.GL_BLEND)throw new NoSuchFieldException("Unknown native blend capability");
            factors[0]=typed(blend.getClass(),"sfactorRGB","f_56118113",int.class);factors[1]=typed(blend.getClass(),"dfactorRGB","f_42749517",int.class);
            factors[2]=typed(blend.getClass(),"sfactorAlpha","f_02061329",int.class);factors[3]=typed(blend.getClass(),"dfactorAlpha","f_29619159",int.class);
        }
        private static Field field(Class<?> owner,String named,String runtime)throws NoSuchFieldException{try{return owner.getDeclaredField(named);}catch(NoSuchFieldException absent){return owner.getDeclaredField(runtime);}}
        private static VarHandle handle(Field field)throws IllegalAccessException{return MethodHandles.privateLookupIn(field.getDeclaringClass(),MethodHandles.lookup()).unreflectVarHandle(field);}
        private static VarHandle typed(Class<?> owner,String named,String runtime,Class<?> type)throws ReflectiveOperationException{Field field=field(owner,named,runtime);if(field.getType()!=type||Modifier.isStatic(field.getModifiers()))throw new NoSuchFieldException("Native blend field type changed: "+named);return handle(field);}
        public Object context(){if(Platform.get()!=Platform.WINDOWS)return null;long handle=WGL.wglGetCurrentContext(null);return handle==0?null:owner.current(GL.getCapabilities(),handle,Thread.currentThread());}
        public boolean enabled(){return GL11.glIsEnabled(GL11.GL_BLEND);}
        public int factor(int index){return GL11.glGetInteger(switch(index){case 0->GL14.GL_BLEND_SRC_RGB;case 1->GL14.GL_BLEND_DST_RGB;case 2->GL14.GL_BLEND_SRC_ALPHA;case 3->GL14.GL_BLEND_DST_ALPHA;default->throw new IllegalArgumentException("Blend factor index");});}
        public int equation(boolean alpha){return GL11.glGetInteger(alpha?GL20.GL_BLEND_EQUATION_ALPHA:GL20.GL_BLEND_EQUATION_RGB);}
        public boolean cachedEnabled(){return(boolean)enabled.get(state);}
        public int cachedFactor(int index){return(int)factors[index].get(blend);}
        public void restoreCache(boolean active,int srcRgb,int dstRgb,int srcAlpha,int dstAlpha){GlStateManager.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);if(active)GlStateManager.enableBlend();else GlStateManager.disableBlend();}
        public void restoreActual(boolean active,int srcRgb,int dstRgb,int srcAlpha,int dstAlpha,int rgbEquation,int alphaEquation){GL20.glBlendEquationSeparate(rgbEquation,alphaEquation);GL14.glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);if(active)GL11.glEnable(GL11.GL_BLEND);else GL11.glDisable(GL11.GL_BLEND);}
    }
}
