// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.features.NativeLines;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NativeLinesTest {
    @Test void everyLineVertexSatisfiesTheNativePipelineFormat() {
        List<Map<String, Object>> vertices = new ArrayList<>();
        NativeLines.segment(recording(vertices), new PoseStack().last(), 1, 2, 3, 4, 6, 3, 0xB4FF5555, 1.5f);
        assertEquals(2, vertices.size());
        var required = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getElements().stream().map(com.mojang.blaze3d.vertex.VertexFormatElement::name).toList();
        assertTrue(required.contains("LineWidth"));
        for (var vertex : vertices) {
            assertEquals(required.size(), vertex.size());
            assertTrue(vertex.keySet().containsAll(required), "Missing native line attributes: " + required);
            assertEquals(0xB4FF5555, vertex.get("Color"));
            assertEquals(1.5f, vertex.get("LineWidth"));
            float[] normal = (float[]) vertex.get("Normal");
            assertArrayEquals(new float[]{0.6f, 0.8f, 0}, normal, 0.0001f);
        }
        assertArrayEquals(new float[]{1, 2, 3}, (float[]) vertices.get(0).get("Position"));
        assertArrayEquals(new float[]{4, 6, 3}, (float[]) vertices.get(1).get("Position"));
    }

    @Test void degenerateSegmentsNeverSubmitAnInvalidNormal() {
        List<Map<String, Object>> vertices = new ArrayList<>();
        VertexConsumer consumer = recording(vertices);
        var pose = new PoseStack().last();
        NativeLines.segment(consumer, pose, 1, 2, 3, 1, 2, 3, -1, 2);
        NativeLines.segment(consumer, pose, 0, 0, 0, Float.NaN, 0, 0, -1, 2);
        NativeLines.segment(consumer, pose, 0, 0, 0, Float.POSITIVE_INFINITY, 0, 0, -1, 2);
        assertTrue(vertices.isEmpty());
    }

    private static VertexConsumer recording(List<Map<String, Object>> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "addVertex" -> {
                    var vertex = new HashMap<String, Object>();
                    vertex.put("Position", new float[]{(float) args[1], (float) args[2], (float) args[3]});
                    vertices.add(vertex);
                }
                case "setColor" -> vertices.getLast().put("Color", args[0]);
                case "setNormal" -> vertices.getLast().put("Normal", new float[]{(float) args[1], (float) args[2], (float) args[3]});
                case "setLineWidth" -> vertices.getLast().put("LineWidth", args[0]);
                default -> throw new AssertionError("Unexpected native vertex operation: " + method);
            }
            return proxy;
        });
    }
}
