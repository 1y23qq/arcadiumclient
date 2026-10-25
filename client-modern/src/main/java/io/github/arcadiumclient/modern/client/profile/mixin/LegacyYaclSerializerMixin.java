// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile.mixin;

import com.google.gson.Gson;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.ConfigSerializer;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import dev.isxander.yacl3.config.v2.impl.serializer.GsonConfigSerializer;
import io.github.arcadiumclient.modern.client.profile.NativeYaclProfiles;
import java.nio.file.Path;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GsonConfigSerializer.class, remap = false)
public abstract class LegacyYaclSerializerMixin<T> extends ConfigSerializer<T> {
    @Shadow @Final private Gson gson;
    @Shadow @Final private Path path;
    protected LegacyYaclSerializerMixin(ConfigClassHandler<T> config) { super(config); }
    @Inject(method = "loadSafely", at = @At("HEAD"), cancellable = true)
    private void arcadium$load(Map<ConfigField<?>, FieldAccess<?>> fields, CallbackInfoReturnable<LoadResult> cir) {
        LoadResult result = NativeYaclProfiles.loadNative(config, gson, path, fields);
        if (result != null) cir.setReturnValue(result);
    }
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void arcadium$save(CallbackInfo ci) { if (NativeYaclProfiles.saveNative(config, gson, path)) ci.cancel(); }
}
