package org.polyfrost.oneconfig.internal.mixin.skia;

//? if <= 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.chat.ChatGui;
import org.polyfrost.oneconfig.internal.ui.compose.OptionsChatOverlay;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatGui.class)
public abstract class Mixin_OptionsChat {
    @WrapMethod(method = "render")
    private void arcadium$deferChat(int ticks, Operation<Void> original) {
        if (!OptionsChatOverlay.defer((ChatGui) (Object) this, ticks)) original.call(ticks);
    }
}
*///?}
