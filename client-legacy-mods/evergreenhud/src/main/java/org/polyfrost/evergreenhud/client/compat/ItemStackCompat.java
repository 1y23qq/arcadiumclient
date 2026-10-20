package org.polyfrost.evergreenhud.client.compat;

//? if <= 1.8.9 {
import org.polyfrost.oneconfig.internal.legacy.chat.Component;
import net.minecraft.world.item.ItemStack;

public interface ItemStackCompat {
    default Component getHoverName() {
        return Component.fromLegacy(((ItemStack) (Object) this).getLegacyHoverName());
    }
}
//?}
