package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFieldWidget.class)
public abstract class PasswordRenderProbe {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I"))
    private int release$observe(TextRenderer renderer, String text, float x, float y, int color, Operation<Integer> draw) {
        if (ReleaseQa.passwordProbe) ReleaseQa.drawn.add(text);
        return draw.call(renderer, text, x, y, color);
    }
}
