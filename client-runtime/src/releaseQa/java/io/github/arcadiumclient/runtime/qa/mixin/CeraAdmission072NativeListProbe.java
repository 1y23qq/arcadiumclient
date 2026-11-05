// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GlStateManager.class,priority=2000)
public abstract class CeraAdmission072NativeListProbe {
    @Inject(method="callList",at=@At("HEAD"))
    private static void cera$list(int list,CallbackInfo callback){CeraAdmission072Qa.originalVoxelPath();}
}
