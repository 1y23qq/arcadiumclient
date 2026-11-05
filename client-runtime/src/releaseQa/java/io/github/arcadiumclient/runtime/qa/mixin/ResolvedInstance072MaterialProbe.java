// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import java.lang.reflect.Field;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class ResolvedInstance072MaterialProbe {
    @Shadow private Identifier boundTexture;
    @Shadow private boolean armorLayer;
    @Unique private static Field resolved$pass;

    @Inject(method = "recordPart", at = @At("HEAD"), remap = false)
    private void resolved$material(ModelPart part, float scale, CallbackInfo callback) throws ReflectiveOperationException {
        if (!ResolvedInstance072Qa.enabled()) return;
        if (resolved$pass == null) { resolved$pass = ((Object)this).getClass().getDeclaredField("pass"); resolved$pass.setAccessible(true); }
        ResolvedInstance072Qa.material(part, boundTexture, resolved$pass.get(this), armorLayer);
    }
}
