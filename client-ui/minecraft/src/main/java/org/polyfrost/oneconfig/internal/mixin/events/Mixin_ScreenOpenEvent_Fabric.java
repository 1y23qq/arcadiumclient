package org.polyfrost.oneconfig.internal.mixin.events;

//? fabric || ornithe {
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ScreenOpenEvent;
//? if <= 1.8.9
//import org.polyfrost.oneconfig.internal.legacy.LegacyPanoramaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
//?}

//~ if >= 26.2 'Minecraft' -> 'gui.Gui'
import net.minecraft.client.gui.Gui;

// 26.2 moved Minecraft#setScreen to Gui
// Minecraft#setScreenAndShow is only a wrapper most callers bypass
//~ if >= 26.2 'Minecraft' -> 'Gui'
@Mixin(Gui.class)
public class Mixin_ScreenOpenEvent_Fabric {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void screenOpenCallback(Screen screen, CallbackInfo ci) {
        //? if <= 1.8.9
        //LegacyPanoramaTracker.capture(((Minecraft) (Object) this).screen);
        ScreenOpenEvent event = new ScreenOpenEvent(screen);
        EventManager.INSTANCE.post(event);
        if (event.cancelled) {
            ci.cancel();
        }
    }

    //? if > 1.8.9 {
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private void removeComposeScreen(Screen screen, Operation<Void> original) {
        if (screen instanceof ComposeScreen compose) {
            compose.beginNativeRemoval();
            try { original.call(screen); }
            finally { compose.endNativeRemoval(); }
        } else original.call(screen);
    }
    //?}
}
//?}
