// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073QuadEvidence;
import java.nio.FloatBuffer;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.BakedItemGeometryCache$BakedItemGeometry",remap=false)
public abstract class PlayerFeatures073QuadProbe {
    @WrapMethod(method="putQuads",remap=false)
    private static void features073$build(FloatBuffer buffer,Iterable<?> quads,float r,float g,float b,float a,boolean flag,int color,boolean other,int[] tints,Operation<Void> original){Object token=PlayerFeatures073QuadEvidence.begin(buffer,quads);try{original.call(buffer,quads,r,g,b,a,flag,color,other,tints);}finally{PlayerFeatures073QuadEvidence.end(token,buffer);}}
    @WrapOperation(method="putQuads",at=@At(value="INVOKE",target="Ljava/util/Iterator;next()Ljava/lang/Object;"),remap=false)
    private static Object features073$consume(Iterator<?> iterator,Operation<Object> original){return PlayerFeatures073QuadEvidence.consume(original.call(iterator));}
}
