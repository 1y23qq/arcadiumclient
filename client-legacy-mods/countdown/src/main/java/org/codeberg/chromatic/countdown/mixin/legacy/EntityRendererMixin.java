package org.codeberg.chromatic.countdown.mixin.legacy;

//? if <= 1.8.9 {
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PrimedTntEntity;
import org.codeberg.chromatic.countdown.util.CountdownFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 995)
abstract class EntityRendererMixin {

    @Unique
    private Entity countdown$capturedEntity;

    @Inject(
            method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
            at = @At("HEAD")
    )
    private void captureTarget(Entity entity, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        countdown$capturedEntity = entity;
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("RETURN"))
    private void clearTarget(Entity entity, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        countdown$capturedEntity = null;
    }

    @ModifyArg(
            method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I",
                    ordinal = 1
            ),
            index = 3
    )
    private int updateColor(int color) {
        if (countdown$capturedEntity instanceof PrimedTntEntity tnt) {
            color = CountdownFormat.color(tnt.fuseTimer);
        }

        return color;
    }

}
//?}
