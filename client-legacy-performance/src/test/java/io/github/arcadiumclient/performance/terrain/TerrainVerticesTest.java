package io.github.arcadiumclient.performance.terrain;

import java.nio.IntBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TerrainVerticesTest {
    @Test void retainsNativeVertexOrderAndEveryTextureColorNormalAndLightmapBit() {
        Random random = new Random(1710);
        int[] input = new int[8 * 256];
        for (int i = 0; i < input.length; i++) input[i] = random.nextInt();
        for (int i = 0; i < input.length; i += 8) {
            input[i] = Float.floatToRawIntBits(random.nextFloat() * 16);
            input[i + 1] = Float.floatToRawIntBits(random.nextFloat() * 16);
            input[i + 2] = Float.floatToRawIntBits(random.nextFloat() * 16);
        }
        int[] original = input.clone();
        IntBuffer result = IntBuffer.allocate(input.length);
        TerrainVertices.write(input, 256, 1008, 240, 512, result);
        assertArrayEquals(original, input);
        assertEquals(input.length, result.position());
        for (int i = 0; i < input.length; i += 8) {
            for (int attribute = 3; attribute < 8; attribute++) assertEquals(input[i + attribute], result.get(i + attribute));
            for (int axis = 0; axis < 3; axis++) {
                int offset = new int[]{1008, 240, 512}[axis];
                double position = Float.intBitsToFloat(input[i + axis]);
                double reference = offset - 8.0 + (position + 8.0) * TerrainVertices.SECTION_SCALE;
                float actual = Float.intBitsToFloat(result.get(i + axis));
                assertEquals(reference, actual, Math.ulp(actual));
            }
        }
    }

    @Test void preservesNativeAntiCrackTransformOnBothSidesOfRegionBoundaries() {
        int[] origins = {-29_999_999, -1040, -1024, -16, 0, 16, 1008, 1024, 29_999_984};
        float[] coordinates = {-0.25f, 0, 0.5f, 8, 15.5f, 16, 16.25f};
        assertTrue(TerrainVertices.transform(0, 0) > 0);
        for (int origin : origins) {
            int clip = origin & 1023;
            assertEquals(origin, (origin & ~1023) + clip);
            for (float coordinate : coordinates) {
                double expected = clip - 8.0 + (coordinate + 8.0) * TerrainVertices.SECTION_SCALE;
                float actual = TerrainVertices.transform(coordinate, clip);
                assertEquals(expected, actual, Math.ulp(actual));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> TerrainVertices.write(new int[7], 1, 0, 0, 0, IntBuffer.allocate(8)));
    }
}
