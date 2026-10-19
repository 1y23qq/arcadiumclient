// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import java.io.File;
import java.util.List;
import net.minecraft.client.resource.pack.ResourcePacks;
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackFolders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePacks.class)
public abstract class MixinResourcePackFolders {
    @Shadow @Final private File directory;

    @Inject(method = "discoverAvailablePacks", at = @At("HEAD"), cancellable = true)
    private void arcadium$discoverFolders(CallbackInfoReturnable<List<File>> cir) {
        ResourcePackFolders.registerClientDirectory(directory);
        cir.setReturnValue(ResourcePackFolders.discover(directory));
    }
}
