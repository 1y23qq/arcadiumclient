// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.polyhitbox;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("polyhitbox")
@Mixin(targets = "org.polyfrost.polyhitbox.config.ModConfig", remap = false)
public abstract class MixinHitboxBounds {
    @Inject(method = "getAccurateHitboxes", at = @At("HEAD"), cancellable = true)
    private void arcadium$vanillaBounds(CallbackInfoReturnable<Boolean> cir) {
        // Picking adds a margin; vanilla debug rendering uses the unexpanded entity box.
        cir.setReturnValue(false);
    }

    @Inject(method = "makeTree", at = @At("RETURN"))
    private void arcadium$hideExpandedBounds(CallbackInfoReturnable<Tree> cir) {
        Tree tree = cir.getReturnValue();
        if (tree == null) return;
        var option = tree.getProp("accurateHitboxes");
        if (option != null) option.addDisplayCondition(() -> Property.Display.HIDDEN);
    }
}
