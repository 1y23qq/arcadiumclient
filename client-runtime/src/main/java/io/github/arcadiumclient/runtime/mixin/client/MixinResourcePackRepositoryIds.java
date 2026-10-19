// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import java.nio.file.Path;
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackFolders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.ornithemc.osl.resource.loader.impl.resource.repository.DirectoryPackSource", remap = false)
public abstract class MixinResourcePackRepositoryIds {
    @Inject(method = "packId", at = @At("HEAD"), cancellable = true)
    private static void arcadium$relativeRepositoryId(Path file, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ResourcePackFolders.repositoryId(file));
    }
}
