package org.polyfrost.overflowanimations.legacy.platform;

//? if = 1.7.10 {
import net.minecraft.entity.Entity;

public record BlockPos(int x, int y, int z) {
    public BlockPos(Entity entity) { this(floor(entity.x), floor(entity.y), floor(entity.z)); }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos up() { return new BlockPos(x, y + 1, z); }
    public BlockPos down() { return new BlockPos(x, y - 1, z); }
    private static int floor(double value) { int integer = (int) value; return value < integer ? integer - 1 : integer; }
}
//?}
