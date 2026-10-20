package io.github.arcadiumclient.performance;

import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlyphVerticesTest {
    @Test void reproducesBothOriginalTriangleStripTrianglesExactly() {
        GlyphVertices glyph = new GlyphVertices();
        glyph.begin();
        glyph.color(58 / 255f, 160 / 255f, 1, 0.625f);
        float[][] input = {{1.125f, 2.5f, -0.5f, 0, 0}, {1.125f, 10.5f, -0.5f, 0, 1}, {9.125f, 2.5f, -0.5f, 1, 0}, {9.125f, 10.5f, -0.5f, 1, 1}};
        for (float[] vertex : input) {
            glyph.texture(vertex[3], vertex[4]);
            glyph.vertex(vertex[0], vertex[1], vertex[2]);
        }
        FloatBuffer output = FloatBuffer.allocate(54);
        glyph.appendTo(output);
        output.flip();
        for (int index : new int[]{0, 1, 2, 2, 1, 3}) {
            float[] expected = input[index];
            for (float value : expected) assertEquals(Float.floatToRawIntBits(value), Float.floatToRawIntBits(output.get()));
            for (float value : new float[]{58 / 255f, 160 / 255f, 1, 0.625f})
                assertEquals(Float.floatToRawIntBits(value), Float.floatToRawIntBits(output.get()));
        }
        assertFalse(output.hasRemaining());
    }

    @Test void retainsPerVertexFormattingColorsWithoutQuantization() {
        GlyphVertices glyph = new GlyphVertices();
        glyph.begin();
        for (int index = 0; index < 4; index++) {
            glyph.color(index * 0.125f, index * 0.25f, index * 0.0625f, index * 0.3125f);
            glyph.texture(index, -index);
            glyph.vertex(index, index, index);
        }
        FloatBuffer output = FloatBuffer.allocate(54);
        glyph.appendTo(output);
        output.flip();
        for (int index : new int[]{0, 1, 2, 2, 1, 3}) {
            output.position(output.position() + 5);
            assertEquals(index * 0.125f, output.get());
            assertEquals(index * 0.25f, output.get());
            assertEquals(index * 0.0625f, output.get());
            assertEquals(index * 0.3125f, output.get());
        }
    }

    @Test void rejectsMalformedGlyphsAndResetsBetweenCharacters() {
        GlyphVertices glyph = new GlyphVertices();
        FloatBuffer output = FloatBuffer.allocate(108);
        glyph.begin();
        glyph.vertex(1, 2, 0);
        assertThrows(IllegalStateException.class, () -> glyph.appendTo(output));
        glyph.begin();
        for (int index = 0; index < 4; index++) glyph.vertex(index, index, 0);
        assertThrows(IllegalStateException.class, () -> glyph.vertex(4, 4, 0));
        glyph.appendTo(output);
        glyph.begin();
        for (int index = 0; index < 4; index++) glyph.vertex(index + 4, index + 4, 0);
        glyph.appendTo(output);
        assertEquals(108, output.position());
        assertEquals(0, output.get(0));
        assertEquals(4, output.get(54));
    }
}
