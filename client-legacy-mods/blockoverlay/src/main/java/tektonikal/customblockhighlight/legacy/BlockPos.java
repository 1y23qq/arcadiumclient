// SPDX-License-Identifier: GPL-3.0-only
package tektonikal.customblockhighlight.legacy;

//? if = 1.7.10 {
public record BlockPos(int x, int y, int z) {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos relative(Direction direction) {
        return new BlockPos(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
    }
    public BlockPos above() { return new BlockPos(x, y + 1, z); }
    public BlockPos below() { return new BlockPos(x, y - 1, z); }
    public BlockPos north() { return new BlockPos(x, y, z - 1); }
    public BlockPos south() { return new BlockPos(x, y, z + 1); }
    public BlockPos west() { return new BlockPos(x - 1, y, z); }
    public BlockPos east() { return new BlockPos(x + 1, y, z); }
}
//? }
