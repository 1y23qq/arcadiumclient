// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.compat;

import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ComposeScreen.class, remap = false)
public interface ComposeTextInputAccess {
    @Invoker(value = "isEditingText", remap = false) boolean arcadium$isEditingText();
}
