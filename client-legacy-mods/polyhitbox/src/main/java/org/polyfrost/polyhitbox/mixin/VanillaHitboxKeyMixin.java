// Copyright (c) Polyfrost and contributors.
// Arcadium modifications copyright (c) 2026 0aurorq3 and contributors.
package org.polyfrost.polyhitbox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.polyfrost.polyhitbox.config.ModConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.Minecraft.class)
public class VanillaHitboxKeyMixin {
    @WrapOperation(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitboxes:Z", opcode = Opcodes.PUTSTATIC))
    private void polyhitbox$suppressVanillaToggle(boolean value, Operation<Void> original) {
        if (!ModConfig.INSTANCE.getEnabled()) original.call(value);
    }
}
