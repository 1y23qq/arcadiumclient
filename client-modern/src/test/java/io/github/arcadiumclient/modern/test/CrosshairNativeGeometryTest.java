// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.features.CrosshairDesign;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CrosshairNativeGeometryTest {
    @Test void allSavedShapesUseTheNativeGuiFrontFace() throws Exception {
        var pose = new Matrix3x2f().translate(320, 240).scale(3);
        var nativeRectangle = new ColoredRectangleRenderState(null, null, pose, -2, -2, 2, 2, -1, -1, null);
        double nativeSign = Math.signum(area(vertices(nativeRectangle)));
        assertNotEquals(0, nativeSign);
        List<CrosshairDesign> designs = new ArrayList<>();
        for (int shape = 0; shape <= 4; shape++) {
            var design = new CrosshairDesign(); design.shape = shape; designs.add(design);
        }
        var canvas = new CrosshairDesign(); canvas.mode = 1; canvas.pixels[15 * 32 + 15] = 0xFF03F31B; designs.add(canvas);
        var state = Class.forName("io.github.arcadiumclient.modern.client.features.CustomCrosshairRenderer$QuadState");
        var constructor = state.getDeclaredConstructor(CrosshairDesign.Quad.class, Matrix3x2f.class, int.class, ScreenRectangle.class);
        constructor.setAccessible(true);
        for (var design : designs) for (var quad : design.mesh().quads()) {
            var rendered = (GuiElementRenderState) constructor.newInstance(quad, pose, quad.color(), null);
            assertEquals(nativeSign, Math.signum(area(vertices(rendered))), "Culled crosshair shape " + design.shape + ", mode " + design.mode);
        }
    }

    private static List<double[]> vertices(GuiElementRenderState state) {
        List<double[]> points = new ArrayList<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            if (method.getName().equals("addVertexWith2DPose")) {
                var pose = (Matrix3x2fc) args[0];
                float x = (float) args[1], y = (float) args[2];
                points.add(new double[]{pose.m00() * x + pose.m10() * y + pose.m20(), pose.m01() * x + pose.m11() * y + pose.m21()});
            }
            return method.getReturnType() == VertexConsumer.class ? proxy : null;
        });
        state.buildVertices(consumer);
        assertEquals(4, points.size());
        return points;
    }

    private static double area(List<double[]> vertices) {
        double area = 0;
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i); var b = vertices.get((i + 1) % vertices.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        return area;
    }
}
