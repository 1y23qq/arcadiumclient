package org.polyfrost.evergreenhud.client.compat;

//? if = 1.7.10 {
import net.minecraft.world.entity.Entity;

public record LegacyBlockPos(int x, int y, int z) {
    public static final LegacyBlockPos ZERO = new LegacyBlockPos(0, 0, 0);

    public LegacyBlockPos(Entity entity) {
        this((int) Math.floor(entity.x), (int) Math.floor(entity.boundingBox.minY), (int) Math.floor(entity.z));
    }

    public LegacyBlockPos above() {
        return new LegacyBlockPos(x, y + 1, z);
    }

    public LegacyBlockPos above(int distance) {
        return new LegacyBlockPos(x, y + distance, z);
    }
}
//?}
