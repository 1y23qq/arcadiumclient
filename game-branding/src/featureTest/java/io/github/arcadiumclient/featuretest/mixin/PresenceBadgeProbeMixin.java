package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PresenceQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer", remap = false)
public class PresenceBadgeProbeMixin {
    @Inject(method = "draw(FFI)V", at = @At("HEAD"), remap = false)
    private static void arcadium$badge(float x, float y, int textColor, CallbackInfo ci) {
        PresenceQa.tabIcon(Math.round(x), Math.round(y));
        PresenceQa.inlineIcon();
        io.github.arcadiumclient.featuretest.Rendering042Qa.badge(x, y);
    }
}
