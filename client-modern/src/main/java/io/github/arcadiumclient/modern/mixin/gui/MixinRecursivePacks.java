// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(FolderRepositorySource.class)
public abstract class MixinRecursivePacks {
    @Shadow @Final private Path folder;
    @Shadow @Final private PackType packType;
    @Shadow @Final private PackSource packSource;
    @Shadow @Final private DirectoryValidator validator;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void arcadium$nested(Consumer<Pack> output, CallbackInfo callback) {
        if (packType != PackType.CLIENT_RESOURCES) return;
        scan(folder, output, 0);
    }

    private void scan(Path parent, Consumer<Pack> output, int depth) {
        if (depth >= 16) return;
        try (var children = Files.newDirectoryStream(parent)) {
            for (Path child : children) {
                if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        || Files.isRegularFile(child.resolve("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)) continue;
                FolderRepositorySource.discoverPacks(child, validator, (path, supplier) -> {
                    String relative = folder.relativize(path).toString().replace('\\', '/');
                    var location = new PackLocationInfo("file/" + relative, Component.literal(path.getFileName().toString()), packSource, Optional.empty());
                    var pack = Pack.readMetaAndCreate(location, supplier, packType,
                            new PackSelectionConfig(false, Pack.Position.TOP, false));
                    if (pack != null) output.accept(pack);
                });
                scan(child, output, depth + 1);
            }
        } catch (IOException failure) {
            LoggerFactory.getLogger("Arcadium Resource Packs").warn("Could not inspect resource pack folder {}", parent, failure);
        }
    }
}
