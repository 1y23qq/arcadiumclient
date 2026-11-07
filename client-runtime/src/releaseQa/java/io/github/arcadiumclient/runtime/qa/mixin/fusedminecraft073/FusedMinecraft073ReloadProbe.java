// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class FusedMinecraft073ReloadProbe {
 @Inject(method="reloadResources",at=@At("HEAD")) private void fused073$entry(CallbackInfo callback){FusedMinecraft073Evidence.reload(true);}
 @Inject(method="reloadResources",at=@At("RETURN")) private void fused073$returned(CallbackInfo callback){FusedMinecraft073Evidence.reload(false);}
}
