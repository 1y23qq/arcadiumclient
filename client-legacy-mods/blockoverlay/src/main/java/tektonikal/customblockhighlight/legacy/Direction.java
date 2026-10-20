// SPDX-License-Identifier: GPL-3.0-only
package tektonikal.customblockhighlight.legacy;

//? if = 1.7.10 {
public enum Direction {
    DOWN(0, 0, -1, 0), UP(1, 0, 1, 0), NORTH(2, 0, 0, -1),
    SOUTH(3, 0, 0, 1), WEST(4, -1, 0, 0), EAST(5, 1, 0, 0);

    private static final Direction[] BY_ID = values();
    private final int id;
    private final BlockPos normal;

    Direction(int id, int x, int y, int z) {
        this.id = id;
        this.normal = new BlockPos(x, y, z);
    }

    public int getStepX() { return normal.x(); }
    public int getStepY() { return normal.y(); }
    public int getStepZ() { return normal.z(); }
    public BlockPos getNormal() { return normal; }
    public Direction getOpposite() { return BY_ID[id ^ 1]; }
    public AxisDirection getAxisDirection() {
        return getStepX() + getStepY() + getStepZ() < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE;
    }
    public static Direction byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : UP; }
    public enum AxisDirection { NEGATIVE, POSITIVE }
}
//? }
