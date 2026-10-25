// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FramerateLimitTracker.class)
public interface FramerateLimitQaAccess {
    @Accessor("latestInputTime") long arcadium$latestInputTime();
}
