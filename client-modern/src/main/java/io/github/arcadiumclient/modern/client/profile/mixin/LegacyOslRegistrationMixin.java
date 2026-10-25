// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile.mixin;

import io.github.arcadiumclient.modern.client.profile.LegacyOslAliasStore;
import io.github.arcadiumclient.modern.client.profile.LegacyOslProfileAliases;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Config.class, remap = false)
public abstract class LegacyOslRegistrationMixin {
    @Shadow @Final public String id;
    @Inject(method = "makeTree", at = @At("HEAD"), remap = false)
    private void arcadium$beforeNativeDefaults(CallbackInfoReturnable<Tree> cir) {
        if (LegacyOslAliasStore.ITEM_FILE.equals(id)) LegacyOslProfileAliases.beforeItemRegistration();
    }
}
