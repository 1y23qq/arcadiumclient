// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.access;

import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.resource.pack.ResourcePacks__UnopenedPack;
import net.minecraft.client.resource.metadata.ResourcePackMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePacks__UnopenedPack.class)
public interface ResourcePackEntryAccessor {
    @Accessor("file") File arcadium$file();
    @Accessor("icon") BufferedImage arcadium$icon();
    @Accessor("metadata") ResourcePackMetadata arcadium$metadata();
}
