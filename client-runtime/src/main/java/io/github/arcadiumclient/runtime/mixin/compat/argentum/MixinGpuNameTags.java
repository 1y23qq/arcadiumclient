// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import java.lang.invoke.*;
import java.nio.*;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.resource.Identifier;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.NameTagBatch",remap=false)
public abstract class MixinGpuNameTags {
    @Shadow @Final private Matrix4fStack matrices;
    @Shadow private float lightU;
    @Shadow private float lightV;
    @Shadow private int depth;
    @Unique private final GpuNameTags arcadium$packets=new GpuNameTags();
    @Unique private static MethodHandle arcadium$pass,arcadium$texture,arcadium$buffer;
    @Unique private int[] arcadium$source=new int[64];
    @Unique private boolean arcadium$visibleText;

    @Unique private void arcadium$access() throws Throwable {
        if(arcadium$pass!=null)return;
        Class<?> type=((Object)this).getClass();var lookup=MethodHandles.privateLookupIn(type,MethodHandles.lookup());
        var pass=type.getDeclaredMethod("pass",int.class);arcadium$pass=lookup.unreflect(pass);
        arcadium$texture=lookup.unreflect(type.getDeclaredMethod("texture",Identifier.class));
        var field=pass.getReturnType().getDeclaredField("buffer");field.setAccessible(true);arcadium$buffer=lookup.unreflectGetter(field);
    }
    @Inject(method="begin",at=@At("HEAD"),remap=false)
    private void arcadium$begin(CallbackInfo callback){arcadium$packets.beginFrame();}
    @Inject(method="enter",at=@At("HEAD"),remap=false)
    private void arcadium$count(CallbackInfo callback){if(depth==0)arcadium$visibleText=false;}
    @Inject(method="discard",at=@At("HEAD"),remap=false)
    private void arcadium$discard(CallbackInfo callback){arcadium$packets.discard();}

    @Inject(method="text(Lnet/minecraft/resource/Identifier;[IIFFI)V",at=@At("HEAD"),cancellable=true,remap=true)
    private void arcadium$text(Identifier location,int[] vertices,int length,float x,float y,int alpha,CallbackInfo callback)throws Throwable {
        if(length>0&&!arcadium$visibleText){arcadium$visibleText=true;arcadium$packets.noteLabel();}
        if(!arcadium$packets.enabled())return;
        arcadium$access();int texture=location==null?0:(int)arcadium$texture.invoke((Object)this,location);
        Object pass=arcadium$pass.invoke((Object)this,texture);BufferBuilder buffer=(BufferBuilder)arcadium$buffer.invoke(pass);
        boolean textured=location!=null;
        if(arcadium$packets.queue(buffer,vertices,length,textured?6:4,textured?5:3,true,alpha,matrices,x,y,lightU,lightV))callback.cancel();
    }
    @Inject(method="quads",at=@At("HEAD"),cancellable=true,remap=false)
    private void arcadium$quads(@Coerce Object pass,BufferBuilder buffer,boolean textured,CallbackInfo callback)throws Throwable {
        if(!arcadium$packets.enabled())return;
        arcadium$access();int stride=buffer.getFormat().getIntSize(),length=buffer.getVertexCount()/4*4*stride;
        if(arcadium$source.length<length)arcadium$source=new int[length];
        buffer.getBuffer().duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().get(arcadium$source,0,length);
        BufferBuilder destination=(BufferBuilder)arcadium$buffer.invoke(pass);
        if(arcadium$packets.queue(destination,arcadium$source,length,stride,textured?5:3,false,-1,matrices,0,0,lightU,lightV))callback.cancel();
    }
    @WrapOperation(method="flush",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/vertex/BufferUploader;end(Lnet/minecraft/client/render/vertex/BufferBuilder;)V",remap=true),remap=false)
    private void arcadium$drawPackets(BufferUploader uploader,BufferBuilder buffer,Operation<Void> original){
        if(!arcadium$packets.draw(buffer))original.call(uploader,buffer);
    }
}
