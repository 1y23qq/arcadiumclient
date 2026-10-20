package org.codeberg.chromatic.countdown.mixin;

//? if >1.8.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
//? if <1.21.4
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
*///?} else {
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PrimedTntRenderer;
import net.minecraft.entity.PrimedTntEntity;
//?}
import org.codeberg.chromatic.countdown.util.CountdownFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.4 {
/*import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.world.entity.EntityAttachment;
*///?}

//? if >1.8.9 {
/*@Mixin(TntRenderer.class)
*///?} else
@Mixin(PrimedTntRenderer.class)
abstract class TntRendererMixin
    //? if <1.21.4 && >1.8.9
    //extends EntityRenderer<PrimedTnt>
    //? if = 1.7.10
    extends EntityRenderer
    //? if = 1.8.9
    //extends EntityRenderer<PrimedTntEntity>
{

    //? if <1.21.4 && >1.8.9 {
    /*private TntRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }
    *///?} elif 1.8.9 {
    /*private TntRendererMixin(EntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }
    *///?}

    //? if >=1.21.4 {
    /*@Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V",
            at = @At("TAIL")
    )
    private void addNameTag(PrimedTnt entity, TntRenderState state, float partialTicks, CallbackInfo ci) {
        state.nameTag = state.fuseRemainingInTicks > 0 ? countdown$buildNametag(state.fuseRemainingInTicks) : null;
        state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
    }
    *///?} elif >1.8.9 {
    /*@Inject(
            method = "render(Lnet/minecraft/world/entity/item/PrimedTnt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void addNameTag(PrimedTnt entity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        // TODO: Better mixin(?)
        Component nametag = entity.getFuse() > 0 ? countdown$buildNametag(entity.getFuse()) : null;
        renderNameTag(entity, nametag, poseStack, multiBufferSource, i, g);
    }
    *///?} else {
    @Inject(
            method = "render(Lnet/minecraft/entity/PrimedTntEntity;DDDFF)V",
            at = @At("TAIL")
    )
    private void addNameTag(PrimedTntEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (entity.fuseTimer > 0) {
            CountdownFormat.nametagColor = CountdownFormat.color(entity.fuseTimer);
            try {
                renderNameTag(entity, CountdownFormat.text(entity.fuseTimer), x, y, z, 64);
            } finally {
                CountdownFormat.nametagColor = 0;
            }
        }
    }
    //?}

    //? if >1.8.9 {
    /*@Unique
    private Component countdown$buildNametag(float remainingTicks) {
        return Component.literal(CountdownFormat.text(remainingTicks)).withColor(CountdownFormat.color(remainingTicks));
    }
    *///?}

}
