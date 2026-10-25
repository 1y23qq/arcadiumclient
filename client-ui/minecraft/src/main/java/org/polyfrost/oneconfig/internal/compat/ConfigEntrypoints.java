// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.compat;

//? if fabric {
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.ConfigEntrypoint;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.toggle.v1.ModToggle;
import org.polyfrost.oneconfig.api.ui.v1.ModToggles;

public final class ConfigEntrypoints {
    private static final Logger LOGGER = LogManager.getLogger("Arcadium Options/Entrypoints");
    private static final Map<String, Registration> REGISTRATIONS = new LinkedHashMap<>();
    private static boolean registered;

    private ConfigEntrypoints() { }

    public static void register() {
        if (registered) return;
        registered = true;
        var loader = FabricLoader.getInstance();
        for (var container : loader.getEntrypointContainers(ConfigEntrypoint.ENTRYPOINT, ConfigEntrypoint.class)) {
            String id = container.getProvider().getMetadata().getId();
            Registration registration = new Registration(id);
            try {
                CompatLoader.INSTANCE.withForcedModId(id, () -> {
                    container.getEntrypoint().registerConfigs(registration);
                    return kotlin.Unit.INSTANCE;
                });
                REGISTRATIONS.put(id, registration);
                CompatLoader.INSTANCE.getNativeLoadedConfigs().add(id);
            } catch (Throwable failure) {
                LOGGER.error("Failed to register configs for {}", id, failure);
            }
        }
        for (var container : loader.getEntrypointContainers(ModToggle.ENTRYPOINT, ModToggle.class)) {
            String id = container.getProvider().getMetadata().getId();
            try {
                ModToggles.register(id, container.getEntrypoint());
            } catch (Throwable failure) {
                LOGGER.error("Failed to register module toggle for {}", id, failure);
            }
        }
    }

    public static void applyMetadata() {
        for (Registration registration : REGISTRATIONS.values()) {
            for (Tree tree : ConfigManager.active().trees()) {
                if (!registration.ids.contains(tree.getID())) continue;
                tree.addMetadata("mod_id", registration.modId());
                if (registration.opener != null) tree.addMetadata("open_original_screen", registration.opener);
            }
        }
    }

    private static final class Registration implements ConfigEntrypoint.Context {
        private final String id;
        private final List<String> ids = new ArrayList<>();
        private Runnable opener;

        private Registration(String id) { this.id = id; }
        @Override public String modId() { return id; }
        @Override public <T extends Config> T register(T config) {
            ids.add(config.id);
            return config;
        }
        @Override public void originalScreen(Runnable opener) { this.opener = opener; }
    }
}
//?}
