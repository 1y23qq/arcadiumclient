// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import io.github.arcadiumclient.modern.client.ZoomProfiles;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("zoomify")
@Mixin(targets = "dev.isxander.yacl3.config.v3.JsonFileCodecConfig", remap = false)
public abstract class ZoomifySaveMixin {
    @Shadow @Final private Path configPath;
    @Inject(method = "saveToFile", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$profileSave(CallbackInfo ci) {
        if (ZoomProfiles.interceptSave(this, configPath)) ci.cancel();
    }
    @Inject(method = "saveToFile", at = @At("RETURN"), remap = false)
    private void arcadium$preserve(CallbackInfo ci) {
        try { ModernSettingsMigration.afterZoomSave(configPath, FabricLoader.getInstance().getConfigDir()); }
        catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium/Migration").warn("Could not preserve shared zoom settings", failure); }
    }
}
