// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import io.github.arcadiumclient.runtime.qa.ForgePose072Qa;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.resource.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4fc;

public final class NativePoseReference072Qa {
    private static PlayerBatchAccess api;
    private static MethodHandle record, pack;
    private static Object referenceCapture;

    private NativePoseReference072Qa() {}

    @SuppressWarnings("unchecked")
    public static void reference(ModelPart part, float scale) {
        if (referenceCapture != null || !ForgePose072Qa.sameFrameReferenceNeeded() || scale != .0625f || part.getClass() != ModelPart.class ||
                !part.visible || part.invisible || part.boxes.size() != 1 || part.children != null && !part.children.isEmpty()) return;
        Model model = ((BetaModelPartAccess)part).arcadium$baseModel();
        if (model == null || model.getClass() != PlayerModel.class && model.getClass() != HumanoidModel.class || !GpuModelTransforms.ready()) return;
        boolean players = BetaOptimizationsConfig.getGpuPlayerPipeline(), equipment = BetaOptimizationsConfig.getGpuEquipmentPipeline(), general = BetaOptimizationsConfig.getGpuModelTransforms();
        Set<ModelPart> glint = null;
        boolean restoreGlint = false;
        try {
            if (api == null) {
                api = new PlayerBatchAccess();
                Class<?> capture = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
                record = MethodHandles.privateLookupIn(capture, MethodHandles.lookup()).unreflect(capture.getDeclaredMethod("recordPart", ModelPart.class, float.class));
                Class<?> instances = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.Instances");
                pack = MethodHandles.privateLookupIn(instances, MethodHandles.lookup()).unreflect(instances.getDeclaredMethod("pack", Vector4fc.class));
            }
            Object actor = api.current.invoke(); if (actor == null) return;
            Object capture = api.activeCapture.get(actor);
            if (capture == null || !(boolean)api.capturePlayer.get(capture) || !(boolean)api.captureActive.get(capture) ||
                    (boolean)api.captureFinished.get(capture) || (int)api.captureMode.get(capture) != 5888) return;
            String pass = ((Enum<?>)api.capturePass.get(capture)).name();
            if (!pass.equals("NORMAL") && !pass.equals("TRANSLUCENT") && !pass.equals("GLINT")) return;
            if (pass.equals("GLINT")) {
                glint = (Set<ModelPart>)api.captureGlint.get(capture);
                if (glint.contains(part)) return;
                restoreGlint = true;
            }
            referenceCapture = capture;
            BetaOptimizationsConfig.setGpuPlayerPipeline(false); BetaOptimizationsConfig.setGpuEquipmentPipeline(false); BetaOptimizationsConfig.setGpuModelTransforms(false);
            record.invoke(capture, part, scale);
        } catch (Throwable error) { ForgePose072Qa.referenceFailure(error); }
        finally {
            referenceCapture = null;
            if (restoreGlint) glint.remove(part);
            BetaOptimizationsConfig.setGpuPlayerPipeline(players); BetaOptimizationsConfig.setGpuEquipmentPipeline(equipment); BetaOptimizationsConfig.setGpuModelTransforms(general);
        }
    }

    public static boolean active(Object capture) { return referenceCapture == capture; }

    public static boolean submit(Object capture, Matrix4f matrix, Object box) {
        try {
            if (box == null) throw new IllegalStateException("Native reference did not submit cuboid geometry");
            Object actor = api.current.invoke(), backend = api.backend.get(actor);
            api.select.invoke(backend, (Identifier)api.captureTexture.get(capture), api.capturePass.get(capture));
            int light = (int)api.captureLight.get(capture), layer = (int)api.selectedLayer.get(backend);
            int[] row = new int[19];
            for (int column = 0; column < 4; column++) for (int value = 0; value < 3; value++) row[column * 3 + value] = Float.floatToRawIntBits(matrix.get(column, value));
            row[12] = Float.floatToRawIntBits((float)api.captureTime.get(capture));
            row[13] = (light & 255) | ((light >>> 16 & 255) << 8) | ((layer & 255) << 16);
            row[14] = (int)pack.invoke((Vector4fc)api.captureColor.get(capture)); row[15] = (int)pack.invoke((Vector4fc)api.captureOverlay.get(capture));
            row[16] = ((int)api.number(box, 7) & 65535) | (((int)api.number(box, 8) & 65535) << 16);
            row[17] = ((int)api.number(box, 9) & 65535) | (((int)api.number(box, 10) & 65535) << 16);
            row[18] = ((int)api.number(box, 11) & 255) | (((int)api.number(box, 12) & 255) << 8) | (((int)api.number(box, 13) & 255) << 16);
            ForgePose072Qa.nativeReference(row);
        } catch (Throwable error) { ForgePose072Qa.referenceFailure(error); }
        return true;
    }
}
