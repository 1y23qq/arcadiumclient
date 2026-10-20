// SPDX-License-Identifier: GPL-3.0-only
package tektonikal.customblockhighlight.legacy;

//? if = 1.7.10 {
import net.minecraft.block.Block;
import tektonikal.customblockhighlight.legacy.phys.shapes.VoxelShape;

public final class BlockState {
    public final BlockPos position;
    public final Block block;
    public final int metadata;
    VoxelShape shape;
    int[] modelVertices;

    BlockState(BlockPos position, Block block, int metadata) {
        this.position = position;
        this.block = block;
        this.metadata = metadata;
    }

    public Block getBlock() { return block; }
}
//? }
