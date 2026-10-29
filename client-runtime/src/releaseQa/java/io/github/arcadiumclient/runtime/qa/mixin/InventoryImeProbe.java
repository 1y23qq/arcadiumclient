package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.Ime058Qa;
import java.util.Map;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.inventory.slot.InventorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenuScreen.class)
public abstract class InventoryImeProbe {
    @Shadow private InventorySlot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void ime$nativeKey(char character, int key, CallbackInfo ci) {
        if (!Ime058Qa.enabled()) return;
        if (Ime058Qa.hoveredSlot != null) hoveredSlot = Ime058Qa.hoveredSlot;
        Ime058Qa.nativeKeys.add(key);
    }

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void ime$slotClick(InventorySlot slot, int index, int button, int mode, CallbackInfo ci) {
        if (Ime058Qa.enabled()) Ime058Qa.slotClicks.add(Map.of("index", index, "button", button, "mode", mode));
    }
}
