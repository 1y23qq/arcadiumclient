// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.gui.widget.ListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ListWidget.class)
public interface LegacyListWidgetAccessor {
    @Accessor("minY") int arcadium$top();
    @Accessor("maxY") int arcadium$bottom();
    @Accessor("minX") int arcadium$left();
    @Accessor("width") int arcadium$width();
    @Accessor("headerHeight") int arcadium$headerHeight();
}
