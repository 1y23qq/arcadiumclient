// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.Box;

public final class LegacyWorld {
    private LegacyWorld() {}
    public static BlockState state(WorldView world, BlockPos pos) {
        return new BlockState(world.getBlock(pos.x, pos.y, pos.z), world.getBlockMetadata(pos.x, pos.y, pos.z));
    }
    public static BlockState state(WorldChunk chunk, BlockPos pos) {
        return new BlockState(chunk.getBlockAt(pos.x & 15, pos.y, pos.z & 15), chunk.getBlockMetadataAt(pos.x & 15, pos.y, pos.z & 15));
    }
    public static Box collision(Block block, World world, BlockPos pos) { return block.getCollisionShape(world, pos.x, pos.y, pos.z); }
    public static void shape(Block block, WorldView world, BlockPos pos) { block.updateShape(world, pos.x, pos.y, pos.z); }
}
