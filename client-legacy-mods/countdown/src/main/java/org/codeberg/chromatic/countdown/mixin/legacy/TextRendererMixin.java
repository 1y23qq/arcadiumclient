package org.codeberg.chromatic.countdown.mixin.legacy;

//? if <= 1.8.9 {
import net.minecraft.client.render.TextRenderer;
import org.codeberg.chromatic.countdown.util.CountdownFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
abstract class TextRendererMixin {

    @ModifyVariable(
            //? if = 1.7.10
            method = "draw(Ljava/lang/String;IIIZ)I",
            //? if = 1.8.9
            //method = "draw(Ljava/lang/String;FFIZ)I",
            at = @At("HEAD"),
            argsOnly = true, index = 4
    )
    private int updateColor(int color) {
        if (CountdownFormat.nametagColor != 0) {
            color = (color & 0xFF000000) | (CountdownFormat.nametagColor & 0xFFFFFF);
        }

        return color;
    }

}
//?}
