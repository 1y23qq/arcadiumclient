// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.ForgeBakedItemVertices;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.BakedItemGeometryCache",remap=false)
public abstract class MixinGpuModelBakedItemAdmission {
    @Unique private final ForgeBakedItemVertices.Rejections arcadium$unsupportedItemQuads=new ForgeBakedItemVertices.Rejections();
    @Coerce
    @WrapMethod(method="getItem",remap=false)
    private Object arcadium$safeItemGeometry(BakedModel model,ItemStack stack,Operation<Object> original){
        if(arcadium$unsupportedItemQuads.contains(model))return null;
        try{return original.call(model,stack);}
        catch(ForgeBakedItemVertices.Rejected unsupported){arcadium$unsupportedItemQuads.add(model);return null;}
    }
    @Coerce
    @WrapOperation(method="getItem",at=@At(value="NEW",target="dev/rdh/argentum/impl/render/entity/instancing/BakedItemGeometryCache$BakedItemGeometry"),remap=false)
    private Object arcadium$coldItemGeometry(BakedModel model,int[] tints,Operation<Object> original){int token=ForgeBakedItemVertices.begin();try{return original.call(model,tints);}finally{ForgeBakedItemVertices.end(token);}}
    @Inject(method="delete(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)V",at=@At("HEAD"),remap=false)
    private void arcadium$forgetUnsupportedItems(@Coerce Object commands,CallbackInfo callback){arcadium$unsupportedItemQuads.clear();}
}
