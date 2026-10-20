package org.polyfrost.polynametag.mixin.client;

//? if > 1.8.9 && < 1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.polyfrost.polynametag.client.NametagRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderer")
public abstract class Mixin_WrapNametagRender {
    @WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
			//~ if < 1.21.8 ')V' -> ')I'
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        )
    )
    //~ if < 1.21.8 'void' -> 'int'
    private void wrapNametagRender(
        //~ if < 1.21.8 'Void' -> 'Integer'
        Font font, Component str, float x, float y, int color, boolean dropShadow, Matrix4f pose, MultiBufferSource bufferSource, Font.DisplayMode displayMode, int backgroundColor, int packedLightCoords, Operation<Void> original
    ) {
        //~ if < 1.21.8 'original.call(' -> 'return original.call('
        original.call(
            font,
            str,
            x,
            NametagRenderer.translateY(y),
            NametagRenderer.textColor(color),
            NametagRenderer.textShadow(dropShadow),
            pose,
            bufferSource,
            displayMode,
            NametagRenderer.backgroundColor(backgroundColor),
            packedLightCoords
        );
    }
}
*///?} elif <= 1.8.9 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.TextRenderer;
import org.lwjgl.opengl.GL11;
import org.polyfrost.oneconfig.internal.legacy.GlStateManager;
import net.minecraft.world.entity.Entity;
import org.polyfrost.polynametag.client.NametagRenderer;
import org.polyfrost.polynametag.client.PolyNametagConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderer")
public abstract class Mixin_WrapNametagRender {
    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"
        )
    )
    private int wrapNametagRender(TextRenderer font, String str, int x, int y, int color, Operation<Integer> original) {
        float offset = NametagRenderer.translateY(y) - y;
        GL11.glTranslatef(0, offset, 0);
        try {
            return font.draw(str, x, y, NametagRenderer.textColor(color), NametagRenderer.textShadow(false));
        } finally {
            GL11.glTranslatef(0, -offset, 0);
        }
    }

    @Unique
    private static final float POLYNAMETAG$SNEAK_OFFSET = 9.374999F;

    @Unique
    private static boolean polynametag$sneakLook(Entity entity) {
        return PolyNametagConfig.isEnabled() && entity.isSneaking();
    }

    @Inject(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V",
            ordinal = 0,
            remap = false,
            shift = At.Shift.AFTER
        )
    )
    private void polynametag$lowerSneakingNametag(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (polynametag$sneakLook(entity)) {
            GlStateManager.translatef(0.0F, POLYNAMETAG$SNEAK_OFFSET, 0.0F);
        }
    }

    @WrapWithCondition(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V",
            remap = false
        )
    )
    private boolean polynametag$occludeSneakingNametag(int capability, @Local(argsOnly = true) Entity entity) {
        return capability != GL11.GL_DEPTH_TEST || !polynametag$sneakLook(entity);
    }

    @WrapWithCondition(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I",
            ordinal = 1
        )
    )
    private boolean polynametag$skipOpaqueSneakingPass(TextRenderer font, String str, int textX, int textY, int color, @Local(argsOnly = true) Entity entity) {
        return !polynametag$sneakLook(entity);
    }
}
//?}
