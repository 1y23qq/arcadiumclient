package dev.microcontrollers.betterscreens.hook;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import net.minecraft.client.render.Window;

public class ContainerScaleHook {
    public static boolean shouldScale(Screen screen) {
        int scale = BetterScreensConfig.containerScale;
        if (screen == null || scale == -1) return false;
        if (!(screen instanceof InventoryMenuScreen)) return false;
        return scale != Minecraft.getInstance().options.guiScale;
    }

    private static int newScale = -1;

    public static int getNewScale() {
        return newScale;
    }

    public static void setNewScale(int scale) {
        newScale = scale;
    }

    public static void scale(Screen screen, Minecraft minecraft, int width, int height, Operation<Void> original) {
        if (shouldScale(screen)) {
            int previous = getNewScale();
            setNewScale(BetterScreensConfig.containerScale);
            try {
                Window scaled = new Window(minecraft, minecraft.width, minecraft.height);
                original.call(screen, minecraft, scaled.getWidth(), scaled.getHeight());
            } finally {
                setNewScale(previous);
            }
        } else {
            original.call(screen, minecraft, width, height);
        }
    }
}
