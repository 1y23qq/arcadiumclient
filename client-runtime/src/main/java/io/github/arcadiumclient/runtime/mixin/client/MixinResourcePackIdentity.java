// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import java.io.File;
import net.minecraft.client.resource.pack.ResourcePacks;
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackFolders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class MixinResourcePackIdentity {
    @Shadow @Final private File file;
    @Shadow @Final ResourcePacks loader;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void arcadium$relativeName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ResourcePackFolders.relativeId(loader.getDirectory(), file));
    }

    @Redirect(method = "toString", at = @At(value = "INVOKE", target = "Ljava/io/File;getName()Ljava/lang/String;"))
    private String arcadium$relativeIdentity(File packFile) {
        return ResourcePackFolders.relativeId(loader.getDirectory(), packFile);
    }
}
