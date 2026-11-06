// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorMvp072Qa;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.ModernArmorMvp",remap=false)
public abstract class ModernGlint072MvpStateProbe {
    @WrapMethod(method="begin")
    private boolean modern$prelude(Object capture,net.minecraft.client.render.model.Model first,net.minecraft.client.render.model.Model second,long frame,Operation<Boolean> original){int token=ModernArmorMvp072Qa.beforeClient();try{return original.call(capture,first,second,frame);}finally{ModernArmorMvp072Qa.afterClient(token,null,-1);}}
    @WrapMethod(method="index")
    private int modern$mvp(Object capture,ModelPart part,long frame,@Coerce Object table,Operation<Integer> original){int token=ModernArmorMvp072Qa.beforeClient();int result=-1;try{result=original.call(capture,part,frame,table);return result;}finally{ModernArmorMvp072Qa.afterClient(token,part,result);}}
}
