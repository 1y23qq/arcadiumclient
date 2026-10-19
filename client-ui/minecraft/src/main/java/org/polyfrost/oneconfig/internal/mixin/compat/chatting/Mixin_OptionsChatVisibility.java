package org.polyfrost.oneconfig.internal.mixin.compat.chatting;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.polyfrost.oneconfig.internal.ui.compose.OptionsChatOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.polyfrost.chatting.hud.ChatWindowHud$Companion", remap = false)
public abstract class Mixin_OptionsChatVisibility {
    @ModifyExpressionValue(method = "shouldHideForVisibility", at = @At(value = "FIELD",
        target = "Lorg/polyfrost/oneconfig/api/hud/v1/HudManager;isGuiScreenOpen:Z"))
    private boolean arcadium$showOptionsChat(boolean screenOpen) {
        return screenOpen && !OptionsChatOverlay.isReplaying();
    }
}
*///?}
