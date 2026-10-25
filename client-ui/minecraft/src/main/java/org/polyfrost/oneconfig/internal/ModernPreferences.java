// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

//? if >= 1.21.11 && < 26.3 {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Tree;

public final class ModernPreferences {
    private static Tree attached;
    private static boolean syncing;
    private static boolean installed;
    private ModernPreferences() { }

    public static void initialize() {
        attach();
        if (installed) return;
        installed = true;
        ConfigManager.addProfileChangeListener(profile -> attach());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (syncing || attached == null || OneConfigConfig.INSTANCE == null) return;
            boolean value = client.options.rawMouseInput().get();
            if (value == OneConfigConfig.rawMouseInput) return;
            syncing = true;
            try {
                attached.getProp("rawMouseInput").setAs(value);
                OneConfigConfig.INSTANCE.save();
            } finally { syncing = false; }
        });
    }

    private static void attach() {
        OneConfigConfig config = OneConfigConfig.INSTANCE;
        Tree tree = config == null ? null : config.getTree();
        if (tree == null || tree == attached) return;
        attached = tree;
        Minecraft client = Minecraft.getInstance();
        if (client.options.rawMouseInput().get() != OneConfigConfig.rawMouseInput) {
            client.options.rawMouseInput().set(OneConfigConfig.rawMouseInput);
        }
        tree.getProp("rawMouseInput").addCallback(value -> {
            if (!syncing && value instanceof Boolean raw) {
                client.options.rawMouseInput().set(raw);
                client.options.save();
            }
            return false;
        });
    }
}
*///?}
