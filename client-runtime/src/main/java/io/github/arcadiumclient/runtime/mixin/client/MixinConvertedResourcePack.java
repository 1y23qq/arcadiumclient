// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import java.io.File;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ZippedResourcePack;
import net.minecraft.client.resource.pack.DirectoryResourcePack;
import io.github.arcadiumclient.runtime.client.resourcepack.ConvertedResourcePacks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class MixinConvertedResourcePack {
    @Shadow @Final private File file;
    @Shadow @Final ResourcePacks loader;
    @Shadow private ResourcePack pack;
    @Unique private File arcadium$adapted;

    @Inject(method = "load", at = @At("HEAD"))
    private void arcadium$prepare(CallbackInfo callback) { arcadium$adapted = ConvertedResourcePacks.prepare(file, loader, false); }

    @Inject(method = "open", at = @At("HEAD"))
    private void arcadium$resources(CallbackInfoReturnable<ResourcePack> callback) {
        File converted = ConvertedResourcePacks.prepare(file, loader, true);
        if (!converted.equals(arcadium$adapted)) {
            if (pack instanceof java.io.Closeable closeable) try { closeable.close(); } catch (java.io.IOException ignored) { }
            pack = converted.isDirectory() ? new DirectoryResourcePack(converted) : new ZippedResourcePack(converted);
            arcadium$adapted = converted;
        }
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/File;isDirectory()Z"))
    private boolean arcadium$directory(File ignored) { return arcadium$adapted.isDirectory(); }

    @ModifyArg(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/pack/DirectoryResourcePack;<init>(Ljava/io/File;)V"), index = 0)
    private File arcadium$folder(File original) { return arcadium$adapted; }

    @ModifyArg(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/pack/ZippedResourcePack;<init>(Ljava/io/File;)V"), index = 0)
    private File arcadium$zip(File original) { return arcadium$adapted; }
}
