package io.github.arcadiumclient.performance;

import java.util.Random;
import net.minecraft.client.render.FrustumData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FrustumIntersectionTest {
    @Test void matchesActualVanillaFrustumForRandomBoxesAndPlanes() {
        Random random = new Random(1710);
        FrustumData vanilla = new FrustumData();
        for (int trial = 0; trial < 100_000; trial++) {
            for (int plane = 0; plane < 6; plane++) {
                for (int axis = 0; axis < 3; axis++) vanilla.frustum[plane][axis] = random.nextFloat() * 2 - 1;
                vanilla.frustum[plane][3] = random.nextFloat() * 256 - 128;
            }
            double x = random.nextDouble() * 1024 - 512;
            double y = random.nextDouble() * 512 - 256;
            double z = random.nextDouble() * 1024 - 512;
            double maxX = x + random.nextDouble() * 32;
            double maxY = y + random.nextDouble() * 32;
            double maxZ = z + random.nextDouble() * 32;
            assertEquals(vanilla.contains(x, y, z, maxX, maxY, maxZ),
                    FrustumIntersection.contains(vanilla.frustum, x, y, z, maxX, maxY, maxZ), "trial " + trial);
        }
    }

    @Test void preservesTouchingPlanesReversedBoxesAndNonfiniteBehavior() {
        FrustumData vanilla = new FrustumData();
        for (int plane = 0; plane < 6; plane++) vanilla.frustum[plane][3] = 1;
        vanilla.frustum[0] = new float[]{1, 0, 0, 0};
        double[] values = {-Double.MAX_VALUE, -2, -0.0, 0.0, Double.MIN_VALUE, 2, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN};
        for (double min : values) for (double max : values) {
            assertEquals(vanilla.contains(min, -1, -1, max, 1, 1),
                    FrustumIntersection.contains(vanilla.frustum, min, -1, -1, max, 1, 1));
        }
        float[] coefficients = {0, -0f, 1, -1, Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN};
        for (float coefficient : coefficients) {
            vanilla.frustum[0][0] = coefficient;
            for (double min : values) for (double max : values) {
                assertEquals(vanilla.contains(min, -1, -1, max, 1, 1),
                        FrustumIntersection.contains(vanilla.frustum, min, -1, -1, max, 1, 1));
            }
        }
    }
}
