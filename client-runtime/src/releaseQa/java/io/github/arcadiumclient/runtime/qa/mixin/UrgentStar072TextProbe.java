// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.UrgentStar072Qa;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public abstract class UrgentStar072TextProbe {
    @Inject(method="draw(Ljava/lang/String;III)I",at=@At("HEAD"))
    private void star072$text(String text,int x,int y,int color,CallbackInfoReturnable<Integer> callback){UrgentStar072Qa.observed(text);}
}
