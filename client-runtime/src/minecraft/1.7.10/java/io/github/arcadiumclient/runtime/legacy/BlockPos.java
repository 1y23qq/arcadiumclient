// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.entity.Entity;

public final class BlockPos extends net.minecraft.util.math.BlockPos {
    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public BlockPos(Entity entity) { this((int) Math.floor(entity.x), (int) Math.floor(entity.shape.minY), (int) Math.floor(entity.z)); }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos up() { return new BlockPos(x, y + 1, z); }
    public BlockPos down() { return new BlockPos(x, y - 1, z); }
}
