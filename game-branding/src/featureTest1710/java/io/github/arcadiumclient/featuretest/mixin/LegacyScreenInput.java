// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface LegacyScreenInput {
    @Invoker("mouseClicked") void arcadium$press(int x, int y, int button);
    @Invoker("mouseDragged") void arcadium$drag(int x, int y, int button, long elapsed);
    @Invoker("mouseReleased") void arcadium$release(int x, int y, int button);
}
