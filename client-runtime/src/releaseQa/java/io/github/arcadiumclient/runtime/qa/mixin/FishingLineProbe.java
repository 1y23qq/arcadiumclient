package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FishingBobberRenderer.class, priority = 500)
public abstract class FishingLineProbe {
    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
    private void release$observe(Tesselator buffer, Operation<Void> draw) {
        if (ReleaseQa.rodProbe) ReleaseQa.lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        draw.call(buffer);
    }
}
