// SPDX-License-Identifier: MIT
// Copyright (c) 2022 uku
package io.github.arcadiumclient.modern.client.features;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class BetterHurtCamConfig extends Config {
    public static final BetterHurtCamConfig INSTANCE = new BetterHurtCamConfig();
    @Switch(title = "Enable HurtCam") public boolean enabled = true;
    @Number(title = "HurtCam multiplier", description = "Scale damage camera rotation. Zero removes it.", min = -3.4028235E38f, max = 3.4028235E38f) public double multiplier = 0.3;
    @Switch(title = "Health bar blinking") public boolean heartBlink = true;
    @Dropdown(title = "HurtCam type", options = {"OLD", "YAW_BASED"}) public String type = "YAW_BASED";
    @Include public boolean oldTypeIn18 = false;

    private BetterHurtCamConfig() { super("betterhurtcam.toml", "BetterHurtCam", Category.VISUALS); }
    public static boolean oldMode() { return "OLD".equalsIgnoreCase(INSTANCE.type == null ? "" : INSTANCE.type.replace('"', ' ').trim()); }
    public static void initialize() {
        INSTANCE.preload();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("arcadium", "hurt_camera"));
        var toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.betterhurtcam.toggle", GLFW.GLFW_KEY_F8, category));
        var plus = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.betterhurtcam.plus", GLFW.GLFW_KEY_F7, category));
        var minus = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.betterhurtcam.minus", GLFW.GLFW_KEY_F6, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) {
                if (mc.player == null || mc.screen != null) continue;
                INSTANCE.enabled = !INSTANCE.enabled; INSTANCE.save();
                mc.gui.setOverlayMessage(Component.literal("HurtCam " + (INSTANCE.enabled ? "enabled" : "disabled")), false);
            }
            int steps = 0;
            while (plus.consumeClick()) steps++;
            while (minus.consumeClick()) steps--;
            if (steps != 0 && mc.player != null && mc.screen == null) {
                INSTANCE.multiplier = Math.rint((INSTANCE.multiplier + steps * 0.1) * 100) / 100;
                INSTANCE.save();
                mc.gui.setOverlayMessage(Component.literal("HurtCam multiplier: " + String.format(java.util.Locale.ROOT, "%.2f", INSTANCE.multiplier)), false);
            }
        });
    }
}
