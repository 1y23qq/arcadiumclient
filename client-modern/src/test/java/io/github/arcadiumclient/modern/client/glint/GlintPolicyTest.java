// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class GlintPolicyTest {
    @Test void strokesUseIndependentColorAndPotionColorOverridesBoth() {
        var settings = new GlintEffectOptions();
        settings.getGlintColor().setArgb(0xFF112233);
        settings.getStrokeOneColor().setArgb(0xFFEE1100);
        settings.getStrokeTwoColor().setArgb(0xFF0088EE);
        var potion = new GlintItemData.Data(true, 0x33CC66);
        assertEquals(0xFF112233, GlintMath.color(settings, 0, false, potion));
        settings.setIndividualStrokes(true);
        assertEquals(0xFFEE1100, GlintMath.color(settings, 0, false, potion));
        assertEquals(0xFF0088EE, GlintMath.color(settings, 1, false, potion));
        assertEquals(0xFF33CC66, GlintMath.color(settings, 0, true, potion));
        assertEquals(0xFF33CC66, GlintMath.color(settings, 1, true, potion));
    }
    @Test void rotationScaleAndSpeedReachActualTextureMatrix() {
        var settings = new GlintEffectOptions();
        var point = new Vector4f(.3f, .7f, 0, 1);
        var first = GlintMath.texture(settings, 0, 1300, .5).transform(new Vector4f(point));
        var second = GlintMath.texture(settings, 1, 1300, .5).transform(new Vector4f(point));
        assertNotEquals(first, second);
        settings.setSpeed(2);
        assertEquals(GlintMath.texture(settings, 0, 1000, .5), GlintMath.texture(new GlintEffectOptions(), 0, 2000, .5));
        settings.setScale(0);
        assertEquals(0, GlintMath.texture(settings, 0, 1300, .5).transform(new Vector4f(point)).x, .0001);
        settings.setScale(Float.NaN); settings.setSpeed(Float.POSITIVE_INFINITY); settings.setStrokeRotOne(Float.NaN);
        assertTrue(GlintMath.texture(settings, 0, 1300, .5).isFinite());
    }
    @Test void resetRestoresEveryNestedOption() {
        var settings = new GlintEffectOptions();
        settings.setIndividualStrokes(true); settings.setSpeed(8); settings.setScale(3); settings.setStrokeRotOne(90); settings.setStrokeRotTwo(40);
        settings.getStrokeOneColor().setArgb(0xFF000000); settings.getStrokeOneColor().setChroma(true);
        settings.reset();
        assertFalse(settings.getIndividualStrokes()); assertEquals(1, settings.getSpeed()); assertEquals(1, settings.getScale());
        assertEquals(-50, settings.getStrokeRotOne()); assertEquals(10, settings.getStrokeRotTwo());
        assertEquals(GlintConfig.DEFAULT_COLOR, settings.getStrokeOneColor().getRawArgb()); assertFalse(settings.getStrokeOneColor().getChroma());
    }
    @Test void legacyConfigIdAndNestedSchemaRemainReadable() throws Exception {
        var node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream("io/github/arcadiumclient/modern/client/glint/GlintConfig.class")) { new ClassReader(stream).accept(node, 0); }
        var fields = node.fields.stream().map(field -> field.name).toList();
        assertTrue(fields.containsAll(List.of("heldItem", "guiItem", "droppedItem", "framedItem", "shinyPots", "armorGlintToggle", "armorColor", "potionGlint", "potionGlintSize", "potionGlintForeground", "potionGlintBackground", "potionBasedColor")));
        boolean id = node.methods.stream().flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)).anyMatch(instruction -> instruction instanceof LdcInsnNode literal && "glintcolorizer.json".equals(literal.cst));
        assertTrue(id);
    }
    @Test void vertexTintSurvivesNativeModelColors() {
        var recorded = new Recorder();
        var glint = new GlintVertices(recorded, 0xFF00CCFF);
        glint.addVertex(1, 2, 3).setColor(10, 20, 30, 255).setUv(.2f, .5f);
        glint.addVertex(2, 3, 4).setColor(0xFFFF0000);
        assertEquals(List.of(0xFF00CCFF, 0xFF00CCFF), recorded.colors);
        assertEquals(2, recorded.vertices);
    }
    private static class Recorder implements VertexConsumer {
        final List<Integer> colors = new ArrayList<>(); int vertices;
        public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        public VertexConsumer setColor(int color) { colors.add(color); return this; }
        public VertexConsumer setUv(float x, float y) { return this; }
        public VertexConsumer setUv1(int x, int y) { return this; }
        public VertexConsumer setUv2(int x, int y) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }
}
