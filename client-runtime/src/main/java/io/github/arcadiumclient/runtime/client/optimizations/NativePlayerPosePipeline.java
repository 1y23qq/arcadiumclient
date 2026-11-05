// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class NativePlayerPosePipeline {
    private static final int MAX_PROFILES = 128;
    private static final int MAGIC_1 = Float.floatToRawIntBits(-9876543f), MAGIC_2 = Float.floatToRawIntBits(-8765432f);
    private static final IdentityHashMap<Model, Profile> profiles = new IdentityHashMap<>();
    private static final IdentityHashMap<Model, Profile> originalVertexProfiles = new IdentityHashMap<>();
    private static final Set<Model> unsupportedOriginalVertices = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static PlayerBatchAccess api;
    private static Object backend;
    private static boolean failed, templateBudgetExhausted;
    private static long batchEpoch;
    private static volatile String state = "Waiting for native player callbacks";
    public static long playersQueued, bodyPartsQueued, armorPartsQueued, poseGroups, glintDuplicates, fallbacks;
    public static long stateFallbacks, modelFallbacks, geometryFallbacks, budgetFallbacks;
    public static long originalArmorPartsQueued;

    public static final class CaptureState {
        private final NativePoseGroups groups = new NativePoseGroups();
        private long epoch;
        private boolean counted;
        public void begin() { beginEpoch(); ModernArmorGlintPipeline.beginCapture(); }
        private void beginEpoch() { groups.clear(); counted = false; epoch = batchEpoch; }
    }

    private static final class TemplateBudgetException extends Exception {
        TemplateBudgetException(IllegalArgumentException cause) { super(cause); }
    }

    private static final class Part {
        final int index, uv, dimensions, size;
        final boolean mirrored;
        final Object originalBox;
        final Object originalGeometry;
        Part(int index, ModelPart part, Object box, Object originalGeometry) throws Throwable {
            this.index = index; originalBox = part.boxes.get(0); mirrored = api.mirrored(box);
            this.originalGeometry = originalGeometry;
            uv = ((int)api.number(box, 7) & 65535) | (((int)api.number(box, 8) & 65535) << 16);
            dimensions = ((int)api.number(box, 9) & 65535) | (((int)api.number(box, 10) & 65535) << 16);
            size = ((int)api.number(box, 11) & 255) | (((int)api.number(box, 12) & 255) << 8) | (((int)api.number(box, 13) & 255) << 16);
        }
    }

    private static final class Profile {
        final IdentityHashMap<ModelPart, Part> parts = new IdentityHashMap<>();
        final Object[] geometry = new Object[2];
        final int template;
        final boolean originalVertices;
        Profile(HumanoidModel model, Object device, boolean originalVertices) throws Throwable {
            this.originalVertices = originalVertices;
            ModelPart[] source = model instanceof PlayerModel player ? new ModelPart[]{player.head, player.body, player.rightArm, player.leftArm,
                    player.rightLeg, player.leftLeg, player.hat, player.leftPants, player.rightPants, player.leftSleeve, player.rightSleeve, player.jacket} :
                    new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, model.hat};
            float[] data = new float[12 * 16];
            for (int i = 0; i < source.length; i++) {
                ModelPart part = source[i];
                if (!standardPart(part)) continue;
                Object[] boxes = (Object[])api.boxTemplates.invoke(device, part);
                if (boxes == null || boxes.length != 1) continue;
                Object box = boxes[0]; boolean finite = true;
                for (int axis = 0; axis < 6; axis++) finite &= Float.isFinite(api.number(box, axis));
                for (int axis = 11; axis < 14; axis++) finite &= api.number(box, axis) >= 0 && api.number(box, axis) <= 255;
                if (!finite) continue;
                NativePartBounds.write(data, i, originalVertices, api.number(box, 0), api.number(box, 1), api.number(box, 2),
                        api.number(box, 3), api.number(box, 4), api.number(box, 5));
                Object original = originalVertices ? api.partGeometry.invoke(api.modelGeometry.invoke(device, model), part, .0625f) : null;
                if (originalVertices && original == null) throw new IllegalArgumentException("Original armor geometry is unavailable");
                parts.put(part, new Part(i, part, box, original));
            }
            if (originalVertices && parts.size() != source.length) throw new IllegalArgumentException("Original armor geometry is incomplete");
            if (!originalVertices) { geometry[0] = api.boxGeometry.invoke(device, false); geometry[1] = api.boxGeometry.invoke(device, true); }
            if (parts.isEmpty()) template = -1;
            else {
                try { template = GpuPlayerPackets.registerTemplate(data); }
                catch (IllegalArgumentException error) { throw new TemplateBudgetException(error); }
            }
        }
    }

    private NativePlayerPosePipeline() {}
    public static void beginBatch() { batchEpoch++; }
    public static boolean usesBackend(Object value) { return backend == value; }

    private static void backend(Object device) {
        if (backend == device) return;
        profiles.clear(); originalVertexProfiles.clear(); unsupportedOriginalVertices.clear(); backend = device; templateBudgetExhausted = false;
    }

    public static boolean prepareOriginalArmor(HumanoidModel first, HumanoidModel second, Object device) {
        if (failed) return false;
        try {
            if (api == null) api = new PlayerBatchAccess();
            backend(device);
            return prepareOriginalArmorModel(first, device) && prepareOriginalArmorModel(second, device);
        } catch (TemplateBudgetException exhausted) {
            templateBudgetExhausted = true; budgetFallbacks++; return false;
        } catch (Throwable unsupported) {
            unsupportedOriginalVertices.add(first); unsupportedOriginalVertices.add(second); geometryFallbacks++;
            state = "Original armor mesh unavailable; retaining the original layer"; return false;
        }
    }

    private static boolean prepareOriginalArmorModel(HumanoidModel model, Object device) throws Throwable {
        if (model.getClass() != HumanoidModel.class || unsupportedOriginalVertices.contains(model)) return false;
        if (originalVertexProfiles.containsKey(model)) return true;
        if (profiles.size() + originalVertexProfiles.size() >= MAX_PROFILES || templateBudgetExhausted) return false;
        originalVertexProfiles.put(model, new Profile(model, device, true)); return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean capture(CaptureState context, Object capture, ModelPart part, float scale) {
        if (failed || !BetaOptimizationsConfig.getGpuPlayerPipeline() && !BetaOptimizationsConfig.getGpuEquipmentPipeline()) return false;
        if (part.invisible || !part.visible || part.boxes.isEmpty()) return false;
        if (!GpuModelTransforms.ready()) return false;
        Object instances = null, batch = null, device = null;
        NativePoseGroups.Entry group = null;
        Set<ModelPart> glint = null;
        boolean markedGlint = false, created = false, checkpointed = false;
        int oldSize = 0, oldCount = 0, oldBatchCount = 0, oldDeviceCount = 0, job = -1;
        boolean oldRecorded = false;
        try {
            if (api == null) api = new PlayerBatchAccess();
            boolean armor = (boolean)api.captureArmor.get(capture);
            if (armor ? !BetaOptimizationsConfig.getGpuEquipmentPipeline() : !BetaOptimizationsConfig.getGpuPlayerPipeline()) return false;
            if (!(boolean)api.capturePlayer.get(capture) || !(boolean)api.captureActive.get(capture) ||
                    (boolean)api.captureFinished.get(capture) || (int)api.captureMode.get(capture) != 5888 || scale != .0625f) {
                stateFallbacks++; return fallback("Unsupported native pass state");
            }
            Model model = ((BetaModelPartAccess)part).arcadium$baseModel();
            if (model == null || model.getClass() != PlayerModel.class && model.getClass() != HumanoidModel.class) {
                modelFallbacks++; return fallback("Custom native model");
            }
            if (!standardPart(part)) { geometryFallbacks++; return fallback("Custom part geometry"); }
            Object pass = api.capturePass.get(capture);
            String kind = ((Enum<?>)pass).name();
            if (!kind.equals("NORMAL") && !kind.equals("TRANSLUCENT") && !kind.equals("GLINT")) {
                stateFallbacks++; return fallback("Unsupported native render pass");
            }
            Identifier texture = (Identifier)api.captureTexture.get(capture);
            if (texture == null) { stateFallbacks++; return fallback("Native texture not bound"); }
            if (CeraEmissiveAdmission.nativeSubmitRequired(capture, model, texture, kind)) {
                stateFallbacks++; return fallback("Material retains native submission callbacks");
            }
            Matrix4fc matrix = (Matrix4fc)api.captureMatrices.get(capture);
            if (!finiteAffine(matrix) || !finitePose(part)) { stateFallbacks++; return fallback("Unsupported native transform"); }
            Object actor = api.current.invoke();
            if (actor == null || !(boolean)api.active.invoke(actor)) return false;
            device = api.backend.get(actor);
            backend(device);
            boolean originalVertices = armor && ModernArmorGlintPipeline.originalVertices(model, capture);
            IdentityHashMap<Model, Profile> selectedProfiles = originalVertices ? originalVertexProfiles : profiles;
            Profile profile = selectedProfiles.get(model);
            if (profile == null) {
                if (profiles.size() + originalVertexProfiles.size() >= MAX_PROFILES || templateBudgetExhausted) { budgetFallbacks++; return fallback("Native profile/template budget reached"); }
                try { profile = new Profile((HumanoidModel)model, device, originalVertices); }
                catch (TemplateBudgetException exhausted) {
                    templateBudgetExhausted = true; budgetFallbacks++; return fallback("Native template budget reached");
                }
                selectedProfiles.put(model, profile);
            }
            Part shape = profile.parts.get(part);
            if (shape == null || part.boxes.get(0) != shape.originalBox) {
                geometryFallbacks++; return fallback("Changed native geometry");
            }
            if (kind.equals("GLINT")) {
                glint = (Set<ModelPart>)api.captureGlint.get(capture);
                if (!glint.add(part)) { glintDuplicates++; return true; }
                markedGlint = true;
            }
            if (context.epoch != batchEpoch) context.beginEpoch();
            group = context.groups.find(profile, pass, matrix, shape.index);
            if (group == null) {
                if (context.groups.full()) {
                    if (markedGlint) glint.remove(part);
                    budgetFallbacks++; return fallback("Native capture group budget reached");
                }
                job = GpuPlayerPackets.addPose(matrix, profile.template);
                if (job < 0) {
                    if (markedGlint) glint.remove(part);
                    budgetFallbacks++; return fallback("Native pose budget reached");
                }
                group = context.groups.add(profile, pass, matrix, job); created = true;
            } else job = group.job;
            batch = api.select.invoke(device, texture, pass);
            int layer = (int)api.selectedLayer.get(device), light = (int)api.captureLight.get(capture);
            int lightWord = (light & 255) | (((light >>> 16) & 255) << 8) | ((layer & 255) << 16);
            instances = api.instances.invoke(profile.originalVertices ? shape.originalGeometry : profile.geometry[shape.mirrored ? 1 : 0], batch);
            oldSize = (int)api.size.get(instances); oldCount = (int)api.count.get(instances);
            oldBatchCount = (int)api.batchCount.get(batch); oldDeviceCount = (int)api.backendCount.get(device);
            oldRecorded = (boolean)api.captureRecorded.get(capture);
            checkpointed = true;
            int[] data = (int[])api.data.get(instances);
            if (oldSize + 19 > data.length) {
                data = Arrays.copyOf(data, Math.max(oldSize + 19, data.length * 2)); api.data.set(instances, data);
            }
            GpuPlayerPackets.writePose(job, shape.index, part.x * scale + part.translateX, part.y * scale + part.translateY,
                    part.z * scale + part.translateZ, part.rotationX, part.rotationY, part.rotationZ);
            Arrays.fill(data, oldSize, oldSize + 19, 0);
            data[oldSize] = Float.floatToRawIntBits(-(job * 16 + shape.index + 1)); data[oldSize + 1] = MAGIC_1; data[oldSize + 2] = MAGIC_2;
            data[oldSize + 12] = Float.floatToRawIntBits(ModernArmorGlintPipeline.effectTime(capture,(float)api.captureTime.get(capture))); data[oldSize + 13] = lightWord;
            data[oldSize + 14] = pack((Vector4fc)api.captureColor.get(capture)); data[oldSize + 15] = pack((Vector4fc)api.captureOverlay.get(capture));
            data[oldSize + 16] = shape.uv; data[oldSize + 17] = shape.dimensions; data[oldSize + 18] = shape.size;
            api.size.set(instances, oldSize + 19); api.count.set(instances, oldCount + 1);
            api.batchCount.set(batch, oldBatchCount + 1); api.backendCount.set(device, oldDeviceCount + 1); api.captureRecorded.set(capture, true);
            group.mark(shape.index);
            if (created) poseGroups++;
            if (armor) { armorPartsQueued++; if (profile.originalVertices) originalArmorPartsQueued++; } else {
                bodyPartsQueued++;
                if (!context.counted) { playersQueued++; context.counted = true; }
            }
            state = "Native callbacks preserved; compact poses active"; return true;
        } catch (Throwable error) {
            if (checkpointed) {
                api.size.set(instances, oldSize); api.count.set(instances, oldCount);
                api.batchCount.set(batch, oldBatchCount); api.backendCount.set(device, oldDeviceCount); api.captureRecorded.set(capture, oldRecorded);
            }
            if (created) { context.groups.removeLast(group); GpuPlayerPackets.rollback(job); }
            if (markedGlint) glint.remove(part);
            failed = true; state = "Disabled after error; using native capture";
            LogManager.getLogger("Arcadium/Native GPU Poses").warn("Native compact poses disabled", error);
            return false;
        }
    }

    private static boolean fallback(String reason) { fallbacks++; state = reason; return false; }
    private static boolean standardPart(ModelPart part) {
        return part.getClass() == ModelPart.class && part.boxes.size() == 1 && (part.children == null || part.children.isEmpty());
    }
    private static boolean finitePose(ModelPart part) {
        return Float.isFinite(part.x) && Float.isFinite(part.y) && Float.isFinite(part.z) && Float.isFinite(part.translateX) &&
                Float.isFinite(part.translateY) && Float.isFinite(part.translateZ) && Float.isFinite(part.rotationX) && Float.isFinite(part.rotationY) && Float.isFinite(part.rotationZ);
    }
    private static boolean finiteAffine(Matrix4fc matrix) {
        if (matrix.m03() != 0 || matrix.m13() != 0 || matrix.m23() != 0 || matrix.m33() != 1) return false;
        for (int column = 0; column < 4; column++) for (int row = 0; row < 3; row++) if (!Float.isFinite(matrix.get(column, row))) return false;
        return true;
    }
    private static int pack(Vector4fc color) { return channel(color.x()) | channel(color.y()) << 8 | channel(color.z()) << 16 | channel(color.w()) << 24; }
    private static int channel(float value) { return Math.clamp((int)(value * 255 + .5f), 0, 255); }
    public static void reset() { profiles.clear(); originalVertexProfiles.clear(); unsupportedOriginalVertices.clear(); backend = null; templateBudgetExhausted = false; batchEpoch++; }
    public static String status() {
        if (!BetaOptimizationsConfig.getGpuPlayerPipeline() && !BetaOptimizationsConfig.getGpuEquipmentPipeline()) return "Native GPU player poses: Off";
        return "Native GPU player poses: " + state + " | Players: " + playersQueued + " | Body: " + bodyPartsQueued +
                " | Armor: " + armorPartsQueued + " | Fallback: " + fallbacks;
    }
}
