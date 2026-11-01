// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorRenderer;
import io.github.arcadiumclient.runtime.mixin.client.access.CategoryColorWorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CategoryColorSceneMixin {
    @Shadow private Minecraft minecraft;
    @Shadow private PostChain shaderEffect;
    @Shadow public static boolean anaglyphEnabled;

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void arcadium$resetCategoryColors(float delta, long deadline, CallbackInfo callback) { CategoryColorRenderer.resetFrame(); }

    @WrapMethod(method = "render(IFJ)V")
    private void arcadium$colorWorld(int pass, float delta, long deadline, Operation<Void> original) {
        boolean outline = ((CategoryColorWorldAccess) minecraft.worldRenderer).arcadium$colorOutlines();
        boolean active = CategoryColorRenderer.beginWorld(shaderEffect != null || anaglyphEnabled || outline);
        try { original.call(pass, delta, deadline); }
        finally { if (active) CategoryColorRenderer.endWorld(); }
    }

    @WrapOperation(method = "render(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;F)V"))
    private void arcadium$entityColors(WorldRenderer renderer, Entity camera, Culler culler, float delta, Operation<Void> original) {
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        try { original.call(renderer, camera, culler, delta); }
        finally { CategoryColorRenderer.endLayer(); }
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"))
    private void arcadium$particleColors(int pass, float delta, long deadline, CallbackInfo callback) {
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER))
    private void arcadium$finishParticleColors(int pass, float delta, long deadline, CallbackInfo callback) { CategoryColorRenderer.endLayer(); }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
    private void arcadium$lateBlockColors(int pass, float delta, long deadline, CallbackInfo callback) {
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.WORLD);
    }

    @WrapOperation(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderItemInHand(FI)V"))
    private void arcadium$handColors(GameRenderer renderer, float delta, int pass, Operation<Void> original) {
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        try { original.call(renderer, delta, pass); }
        finally { CategoryColorRenderer.endLayer(); }
    }
}
