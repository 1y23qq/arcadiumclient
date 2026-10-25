// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseQaAccess {
    @Invoker("onMove")
    void arcadium$nativeMove(long window, double x, double y);

    @Invoker("onScroll")
    void arcadium$nativeScroll(long window, double horizontal, double vertical);

    @Invoker("onButton")
    void arcadium$nativeButton(long window, MouseButtonInfo button, int action);
}
