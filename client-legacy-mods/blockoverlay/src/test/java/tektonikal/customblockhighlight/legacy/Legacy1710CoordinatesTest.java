// SPDX-License-Identifier: GPL-3.0-only
package tektonikal.customblockhighlight.legacy;

//? if = 1.7.10 {
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import tektonikal.customblockhighlight.legacy.phys.BlockHitResult;
import tektonikal.customblockhighlight.legacy.phys.HitResult;
import static org.junit.jupiter.api.Assertions.*;

final class Legacy1710CoordinatesTest {
    @Test void matchesAllNativeProtocolFaceDirections() {
        BlockPos origin = new BlockPos(-29_999_999, 255, 29_999_999);
        for (int id = 0; id < 6; id++) {
            net.minecraft.util.math.Direction nativeFace = net.minecraft.util.math.Direction.byId(id);
            Direction face = Direction.byId(id);
            assertEquals(nativeFace.getStepX(), face.getStepX());
            assertEquals(nativeFace.getStepY(), face.getStepY());
            assertEquals(nativeFace.getStepZ(), face.getStepZ());
            BlockPos adjacent = origin.relative(face);
            assertEquals(origin, adjacent.relative(face.getOpposite()));
        }
    }

    @Test void retainsNativeHitCoordinatesAndFaceWithoutBlockStateApi() {
        for (int face = 0; face < 6; face++) {
            net.minecraft.world.HitResult nativeHit = new net.minecraft.world.HitResult(-17, 64, 31, face, Vec3d.of(-16.5, 64.5, 31.5));
            BlockHitResult adapted = (BlockHitResult) HitResult.of(nativeHit);
            assertEquals(HitResult.Type.BLOCK, adapted.getType());
            assertEquals(new BlockPos(-17, 64, 31), adapted.getBlockPos());
            assertEquals(Direction.byId(face), adapted.getDirection());
        }
        assertEquals(HitResult.Type.MISS, HitResult.of(null).getType());
    }
}
//? }
