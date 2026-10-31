// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.resourcepack;

import io.github.arcadiumclient.shared.packs.PackConversion;
import java.io.File;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;

public final class ConvertedResourcePacks {
    private ConvertedResourcePacks() { }
    public static File prepare(File source, ResourcePacks loader, boolean full) {
        try {
            var directory = loader.getDirectory().toPath().toAbsolutePath().getParent().resolve("arcadium/cache/resource-packs");
            String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("legacy");
            directory=directory.resolve(version.equals("1.7.10") || version.equals("1.8.9") ? version : "legacy");
            if (!full) return PackConversion.preview(source.toPath(), directory, new PackConversion.Target(1, 0)).toFile();
            return PackConversion.prepare(source.toPath(), directory, new PackConversion.Target(1, 0), path -> {
                if (!path.startsWith("assets/minecraft/")) return null;
                try (var stream = loader.defaultPack.getResource(new Identifier("minecraft", path.substring("assets/minecraft/".length())))) {
                    return stream.readNBytes(64 * 1024 * 1024);
                } catch (Exception failure) { return null; }
            }).toFile();
        } catch (Exception failure) {
            LogManager.getLogger("Arcadium/ResourcePacks").warn("Could not adapt resource pack {}. The original pack is unchanged.", source.getName(), failure);
            return source;
        }
    }
}
