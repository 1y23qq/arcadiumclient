// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ModernGlintPixelTrace072Qa {
    private static final List<Map<String, Object>> draws = new ArrayList<>();
    private static final IdentityHashMap<Model, Integer> modelIds = new IdentityHashMap<>();
    private ModernGlintPixelTrace072Qa() {}

    public static int beforeNative(ModelPart part) {
        if (!ModernGlint072Qa.tracingNative()) return 0;
        Model base = ((BetaModelPartAccess)part).arcadium$baseModel();
        if (!(base instanceof HumanoidModel model)) return 0;
        String name = part == model.head ? "head" : part == model.body ? "body" : part == model.rightArm ? "rightArm" :
                part == model.leftArm ? "leftArm" : part == model.rightLeg ? "rightLeg" : part == model.leftLeg ? "leftLeg" : "other";
        int id = modelIds.computeIfAbsent(base, key -> modelIds.size() + 1);
        return before("native-fixed-function", name + "-model" + id);
    }

    public static int beforeGpu(int vertices, int instances) {
        if (!ModernGlint072Qa.tracingGpu()) return 0;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), pass = GL20.glGetUniformLocation(program, "uGlintPass");
        if (pass < 0 || GL20.glGetUniformi(program, pass) < 0) return 0;
        return before("instanced-shader", vertices + " vertices / " + instances + " instances");
    }

    private static int before(String route, String geometry) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("style", ModernGlint072Qa.currentCase()); entry.put("route", route); entry.put("geometry", geometry);
        entry.put("screenPixels", "647..649,395 (fixed1280x720 diagnostic coordinates)");
        entry.put("before", sample());
        float[] projection = new float[16], modelView = new float[16]; int[] viewport = new int[4]; double[] range = new double[2];
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, projection); GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, range);
        entry.put("projection", projection); entry.put("modelView", modelView); entry.put("viewport", viewport); entry.put("depthRange", range);
        entry.put("depthFunction", GL11.glGetInteger(GL11.GL_DEPTH_FUNC)); entry.put("depthWrite", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        entry.put("polygonOffsetEnabled", GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL));
        entry.put("polygonOffsetFactor", GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR)); entry.put("polygonOffsetUnits", GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS));
        draws.add(entry); return draws.size();
    }

    public static void after(int token) { if (token != 0) draws.get(token - 1).put("after", sample()); }
    public static List<Map<String, Object>> report() { return draws; }

    private static Map<String, Object> sample() {
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        var colors = BufferUtils.createByteBuffer(12); var depths = BufferUtils.createFloatBuffer(3);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            GL11.glReadPixels(647, Minecraft.getInstance().height - 1 - 395, 3, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, colors);
            GL11.glReadPixels(647, Minecraft.getInstance().height - 1 - 395, 3, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
        } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead); }
        int[] rgba = new int[12]; float[] depth = new float[3];
        for (int i = 0; i < rgba.length; i++) rgba[i] = colors.get(i) & 255;
        for (int i = 0; i < depth.length; i++) depth[i] = depths.get(i);
        return Map.of("rgba", rgba, "depth", depth);
    }
}
