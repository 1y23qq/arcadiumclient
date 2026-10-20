// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.block.Block;

public record BlockState(Block block, int metadata) {
    public Block getBlock() { return block; }
}
