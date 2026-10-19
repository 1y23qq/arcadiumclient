package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.FontControlsQa;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TextRenderer.class, priority = 350)
public abstract class FontControlsProbeMixin {
    @WrapMethod(method = "draw(Ljava/lang/String;FFIZ)I")
    private int arcadium$fontChat(String text, float x, float y, int color, boolean shadow, Operation<Integer> original) {
        int result = original.call(text, x, y, color, shadow);
        FontControlsQa.text(text, x, y);
        return result;
    }
}
