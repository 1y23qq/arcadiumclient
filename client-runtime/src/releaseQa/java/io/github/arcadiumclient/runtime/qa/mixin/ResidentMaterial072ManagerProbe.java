// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ResidentMaterial072Evidence;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
public abstract class ResidentMaterial072ManagerProbe {
    @Inject(method="get",at=@At("HEAD"))
    private void material072$get(Identifier texture,CallbackInfoReturnable<?> callback){ResidentMaterial072Evidence.managerRead();}
}
