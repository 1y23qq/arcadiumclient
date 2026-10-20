package org.polyfrost.vanillahud.mixin;

//? if = 1.7.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GameGui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.item.Items;
import org.polyfrost.vanillahud.hud.Huds;
import org.polyfrost.vanillahud.hud.VanillaHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class LegacyGuiEditorMixin {
    @Shadow private String overlayMessage;
    @Shadow private int overlayMessageCooldown;
    @Shadow private boolean overlayMessageTinted;
    @Shadow private ItemStack selectedItem;
    @Shadow private int itemSelectedTimer;
    @Unique private String vanillahud$message;
    @Unique private int vanillahud$messageTime;
    @Unique private boolean vanillahud$messageTinted;
    @Unique private ItemStack vanillahud$item;
    @Unique private int vanillahud$itemTime;
    @Unique private boolean vanillahud$restoreMessage;
    @Unique private boolean vanillahud$restoreItem;

    @Inject(method = "render", at = @At("HEAD"))
    private void vanillahud$preview(CallbackInfo callback) {
        vanillahud$restoreMessage = VanillaHud.previewing(Huds.INSTANCE.getActionBar());
        vanillahud$restoreItem = VanillaHud.previewing(Huds.INSTANCE.getHeldItemTooltip());
        if (vanillahud$restoreMessage) {
            vanillahud$message = overlayMessage;
            vanillahud$messageTime = overlayMessageCooldown;
            vanillahud$messageTinted = overlayMessageTinted;
            overlayMessage = "Action Bar";
            overlayMessageCooldown = 60;
            overlayMessageTinted = false;
        }
        if (vanillahud$restoreItem) {
            vanillahud$item = selectedItem;
            vanillahud$itemTime = itemSelectedTimer;
            selectedItem = new ItemStack(Items.DIAMOND_SWORD);
            itemSelectedTimer = 100;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanillahud$restore(CallbackInfo callback) {
        if (vanillahud$restoreMessage) {
            overlayMessage = vanillahud$message;
            overlayMessageCooldown = vanillahud$messageTime;
            overlayMessageTinted = vanillahud$messageTinted;
        }
        if (vanillahud$restoreItem) {
            selectedItem = vanillahud$item;
            itemSelectedTimer = vanillahud$itemTime;
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ClientPlayerInteractionManager;hasStatusBars()Z"))
    private boolean vanillahud$previewStatus(boolean visible) {
        return visible || VanillaHud.previewing(Huds.INSTANCE.getHotbar());
    }
}
//?}
