package org.polyfrost.vanillahud.mixin;

//? if = 1.7.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GameGui;
import org.polyfrost.vanillahud.hud.LegacyTabRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class LegacyTabListMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
    private boolean vanillahud$tabKey(boolean down) {
        LegacyTabRenderer.updateKey(down);
        return false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanillahud$drawTab(CallbackInfo callback) {
        LegacyTabRenderer.render();
    }
}
//?}
