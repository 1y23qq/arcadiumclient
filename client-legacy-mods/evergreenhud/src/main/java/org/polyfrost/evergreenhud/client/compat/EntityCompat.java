package org.polyfrost.evergreenhud.client.compat;

//? if <= 1.8.9 {
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public interface EntityCompat {
    //? if = 1.7.10 {
    default LegacyBlockPos blockPosition() {
        return new LegacyBlockPos((Entity) (Object) this);
    }
    //?} else {
    /*default BlockPos blockPosition() {
        return new BlockPos((Entity) (Object) this);
    }
    *///?}
}
//?}
