// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern;

import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import io.github.arcadiumclient.modern.client.IdleSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class ModernPreLaunch implements PreLaunchEntrypoint {
    public void onPreLaunch() {
        try {
            var loader = FabricLoader.getInstance();
            io.github.arcadiumclient.modern.client.profile.LegacyOslProfileAliases.beforeLaunch(loader.getGameDir(), loader.getConfigDir());
            IdleSettings.prepare(loader.getConfigDir());
            var profiles = loader.getGameDir().resolve("arcadium/profiles.json");
            boolean named = false;
            if (java.nio.file.Files.isRegularFile(profiles)) {
                var settings = ModernSettingsMigration.read(profiles);
                var saved = settings.get("activeProfile");
                var separate = settings.get("profileSpecificControls");
                named = saved != null && saved.isJsonPrimitive() && !saved.getAsString().isBlank() && (separate == null || separate.getAsBoolean());
            }
            ModernSettingsMigration.migrate(loader.getGameDir(), loader.getConfigDir(), named);
        } catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium/Migration").warn("Legacy settings migration did not complete", failure); }
    }
}
