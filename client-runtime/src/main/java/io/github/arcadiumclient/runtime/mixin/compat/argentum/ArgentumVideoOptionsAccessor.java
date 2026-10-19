// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.gui.screen.Screen;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.gui.VideoOptionsScreen", remap = false)
public interface ArgentumVideoOptionsAccessor {
    @Accessor(value = "parent", remap = false)
    Screen arcadium$parent();

    @Accessor(value = "controller", remap = false)
    CeleritasVideoOptionsController arcadium$controller();
}
