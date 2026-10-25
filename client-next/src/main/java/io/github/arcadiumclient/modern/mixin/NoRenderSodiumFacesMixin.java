// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.features.NoRender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("sodium")
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class NoRenderSodiumFacesMixin {
    @Shadow protected BlockAndTintGetter level;
    @Shadow protected BlockPos pos;
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$side(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction != null && NoRender.block(level.getBlockState(pos.relative(direction)))) cir.setReturnValue(true);
    }
    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$culling(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction != null && NoRender.block(level.getBlockState(pos.relative(direction)))) cir.setReturnValue(false);
    }
}
