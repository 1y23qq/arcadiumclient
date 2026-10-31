// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.resourcepack;

import io.github.arcadiumclient.shared.packs.PackConversion;
import java.nio.file.Path;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.LoggerFactory;

public final class ConvertedPackSource implements Pack.ResourcesSupplier {
    private final Path original, cache;
    private final PackConversion.Target target;
    private final Pack.ResourcesSupplier fallback;
    public ConvertedPackSource(Path original, Path cache, PackConversion.Target target, Pack.ResourcesSupplier fallback) {
        this.original = original; this.cache = cache; this.target = target; this.fallback = fallback;
    }
    private Pack.ResourcesSupplier supplier(boolean full) {
        try {
            Path converted = full ? PackConversion.prepare(original,cache,target,path -> {
                if (!path.startsWith("assets/minecraft/")) return null;
                var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",path.substring("assets/minecraft/".length()));
                var resource = net.minecraft.client.Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES,id);
                if (resource == null) return null;
                try (var stream = resource.get()) { return stream.readNBytes(64 * 1024 * 1024); } catch (java.io.IOException failure) { return null; }
            }) : PackConversion.preview(original,cache,target);
            return converted.equals(original.toAbsolutePath().normalize()) ? fallback : new FilePackResources.FileResourcesSupplier(converted);
        } catch (Exception failure) {
            LoggerFactory.getLogger("Arcadium Resource Packs").warn("Could not adapt resource pack {}. The original pack is unchanged.",original.getFileName(),failure);
            return fallback;
        }
    }
    @Override public PackResources openPrimary(PackLocationInfo location) { return supplier(false).openPrimary(location); }
    @Override public PackResources openFull(PackLocationInfo location,Pack.Metadata metadata) { return supplier(true).openFull(location,metadata); }
}
