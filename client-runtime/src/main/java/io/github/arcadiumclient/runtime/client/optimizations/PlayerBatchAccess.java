// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.resource.Identifier;

final class PlayerBatchAccess {
    final MethodHandle current,active,select,boxGeometry,boxTemplates,instances,nameAdd,beginCapture,firstPass,recorded;
    final VarHandle backend,nameTags,selectedLayer,backendCount,batchCount,data,size,count,entityCount,playerCount,captureMatrices,activeCapture;
    final VarHandle capturePlayer,captureArmor,captureActive,captureFinished,captureMode,capturePass,captureTexture,captureLight,captureColor,captureOverlay,captureTime,captureRecorded,captureGlint;
    private final MethodHandle[] templateFields;
    private static final String ROOT="dev.rdh.argentum.impl.render.entity.instancing.";
    PlayerBatchAccess()throws ReflectiveOperationException{
        Class<?> actor=Class.forName(ROOT+"EntityInstancing"),model=Class.forName(ROOT+"ModelInstancer"),batch=Class.forName(ROOT+"InstanceBatcher$TextureBatch"),geometry=Class.forName(ROOT+"InstanceGeometry"),
                instance=Class.forName("dev.rdh.argentum.api.render.instancing.InstanceDataBuffer"),names=Class.forName(ROOT+"DeferredNameTags"),box=Class.forName("dev.rdh.argentum.api.render.instancing.BoxTemplate");
        current=method(actor,"current");active=method(actor,"isBatchActive");backend=field(actor,"backend");nameTags=field(actor,"nameTags");
        select=method(model,"selectTexture",Identifier.class,Class.forName(ROOT+"InstanceRenderPass"));boxGeometry=method(model,"boxGeometry",boolean.class);boxTemplates=method(model,"boxTemplates",ModelPart.class);
        instances=method(geometry,"instances",batch);selectedLayer=field(model,"selectedTextureLayer");backendCount=field(model,"instanceCount");batchCount=field(batch,"count");
        data=field(instance,"data");size=field(instance,"size");count=field(instance,"count");entityCount=field(actor,"entityCount");playerCount=field(actor,"playerCount");
        nameAdd=method(names,"add",LivingEntityRenderer.class,LivingEntity.class,double.class,double.class,double.class,int.class);
        Class<?> capture=Class.forName(ROOT+"EntityCapture");
        beginCapture=method(actor,"beginEntity",Model.class,Identifier.class,boolean.class,boolean.class,int.class,float.class,float.class,float.class,float.class,float.class);
        firstPass=method(capture,"firstModelPass");recorded=method(capture,"recorded");captureMatrices=field(capture,"matrices");
        activeCapture=field(actor,"activeCapture");capturePlayer=field(capture,"player");captureArmor=field(capture,"armorLayer");captureActive=field(capture,"modelActive");captureFinished=field(capture,"finished");captureMode=field(capture,"matrixMode");
        capturePass=field(capture,"pass");captureTexture=field(capture,"boundTexture");captureLight=field(capture,"packedLight");captureColor=field(capture,"color");captureOverlay=field(capture,"currentOverlayColor");captureTime=field(capture,"effectTime");captureRecorded=field(capture,"recorded");captureGlint=field(capture,"glintParts");
        String[] fields={"originX","minY","minZ","spanX","spanY","spanZ","flipped","textureU","textureV","textureWidth","textureHeight","sizeX","sizeY","sizeZ"};
        templateFields=new MethodHandle[fields.length];for(int i=0;i<fields.length;i++)templateFields[i]=method(box,fields[i]);
    }
    final Object translucent=pass();
    @SuppressWarnings({"rawtypes","unchecked"}) private static Object pass(){try{return Enum.valueOf((Class)Class.forName(ROOT+"InstanceRenderPass"),"TRANSLUCENT");}catch(Exception error){throw new IllegalStateException(error);}}
    private static MethodHandle method(Class<?> type,String name,Class<?>... parameters)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(type.getDeclaredMethod(name,parameters));}
    private static VarHandle field(Class<?> type,String name)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));}
    float number(Object box,int index)throws Throwable{return ((Number)templateFields[index].invoke(box)).floatValue();}
    boolean mirrored(Object box)throws Throwable{return (boolean)templateFields[6].invoke(box);}
}
