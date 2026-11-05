// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=ModernArmorAuxiliary.class,remap=false)
public abstract class ModernGlint072ColdCopyProbe {
    @WrapOperation(method="prepareStores",at=@At(value="INVOKE",target="Lorg/embeddedt/embeddium/impl/gl/device/CommandList;copyBufferSubData(Lorg/embeddedt/embeddium/impl/gl/buffer/GlBuffer;Lorg/embeddedt/embeddium/impl/gl/buffer/GlBuffer;JJJ)V",remap=false),remap=false)
    private static void modern$coldCopy(CommandList commands,GlBuffer source,GlBuffer target,long read,long write,long bytes,Operation<Void> original)throws Throwable{
        ModernArmorAuxiliary072Qa.beforeColdCopy();original.call(commands,source,target,read,write,bytes);ModernArmorAuxiliary072Qa.coldCopyCompleted();
    }
}
