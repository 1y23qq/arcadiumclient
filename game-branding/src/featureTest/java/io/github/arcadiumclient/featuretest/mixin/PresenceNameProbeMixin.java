package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PresenceQa;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 400)
public class PresenceNameProbeMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("HEAD"))
    private void arcadium$presenceName(Entity entity, String name, double x, double y, double z, int distance, CallbackInfo ci) { PresenceQa.beginName(entity); }
    @Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("RETURN"))
    private void arcadium$presenceNameEnd(Entity entity, String name, double x, double y, double z, int distance, CallbackInfo ci) { PresenceQa.endName(); }
}
