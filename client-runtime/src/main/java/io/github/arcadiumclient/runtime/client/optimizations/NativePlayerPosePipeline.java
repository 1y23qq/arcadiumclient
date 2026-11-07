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
        private boolean counted;private long sequence;
        public void begin() { if(PreparedPlayerMode073.ENABLED)sequence++;beginEpoch(); ModernArmorGlintPipeline.beginCapture(); }
        private void beginEpoch() { groups.clear(); counted = false; epoch = batchEpoch; }
    }
    public interface CaptureAccess { CaptureState arcadium$nativePoseState(); }

    public static boolean preflightModernLayer(Object capture) {
        if (!(capture instanceof CaptureAccess access)) return false;
        CaptureState state = access.arcadium$nativePoseState();
        if (state.epoch != batchEpoch) state.beginEpoch();
        return state.groups.remaining() >= 42 && GpuPlayerPackets.MAX_PLAYERS - GpuPlayerPackets.count() >= 42;
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
        final Object[] originalSources;
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
            originalSources = originalVertices ? parts.values().stream().map(part -> part.originalGeometry).toArray() : null;
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
            if (!prepareOriginalArmorModel(first, device) || !prepareOriginalArmorModel(second, device)) return false;
            boolean a = ModernArmorAuxiliary.prepareModel(device, first, originalVertexProfiles.get(first).originalSources);
            boolean b = ModernArmorAuxiliary.prepareModel(device, second, originalVertexProfiles.get(second).originalSources);
            return a && b && ModernArmorAuxiliary.layer(device, first, second);
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
            Object selectedGeometry = profile.originalVertices ? ModernArmorAuxiliary.geometry(device, shape.originalGeometry) : profile.geometry[shape.mirrored ? 1 : 0];
            if (profile.originalVertices) {
                Object target = ModernArmorAuxiliary.destination(device);
                if (target == null || selectedGeometry == null) { stateFallbacks++; return fallback("Original armor auxiliary was not preflighted"); }
                device = target;
                batch = api.select.invoke(device, texture, pass);
                instances = ModernArmorAuxiliary.instances(selectedGeometry, batch);
                if (instances == null || !ModernArmorAuxiliary.reserve(selectedGeometry, instances, (int)api.size.get(instances) + 19)) {
                    budgetFallbacks++; return fallback("Owned modern armor retained-capacity budget reached");
                }
            }
            int geometryMvp = -1;
            if (profile.originalVertices && ModernArmorGlintPipeline.useGeometryMvp()) {
                geometryMvp = ModernArmorGlintPipeline.geometryMvp(capture, part);
                if (geometryMvp < 0) { stateFallbacks++; return fallback("Native armor MVP was not preflighted"); }
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
            if (!profile.originalVertices) batch = api.select.invoke(device, texture, pass);
            int layer = (int)api.selectedLayer.get(device), light = (int)api.captureLight.get(capture);
            int lightWord = (light & 255) | (((light >>> 16) & 255) << 8) | ((layer & 255) << 16);
            if (!profile.originalVertices) instances = api.instances.invoke(selectedGeometry, batch);
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
            data[oldSize + 16] = geometryMvp >= 0 ? geometryMvp + 1 : shape.uv; data[oldSize + 17] = shape.dimensions; data[oldSize + 18] = shape.size;
            api.size.set(instances, oldSize + 19); api.count.set(instances, oldCount + 1);
            api.batchCount.set(batch, oldBatchCount + 1); api.backendCount.set(device, oldDeviceCount + 1); api.captureRecorded.set(capture, true);
            if (profile.originalVertices) ModernArmorAuxiliary.queued(selectedGeometry, instances, oldCount + 1);
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
    private static final PreparedPlayerEmission073.Adapter PREPARED=new PreparedAdapter();
    private static boolean preparedFailed;private static GpuVoxelSkinSelector preparedSelector;
    public static long preparedActors,preparedBodyBlocks,preparedOuterBlocks,preparedParts,preparedRefusals;
    static PreparedPlayerEmission073.Adapter preparedAdapter(){return PREPARED;}
    static boolean preparedSkinAllowed(){return !io.github.arcadiumclient.runtime.client.features.Skin3DConfig.getEnabled()||GpuVoxelSkinRenderer.preparedProjectionQualified();}
    static boolean preparedEnabled(){return PreparedPlayerMode073.ENABLED&&!failed&&!preparedFailed&&BetaOptimizationsConfig.getGpuPlayerPipeline();}
    private static final ClassValue<Boolean> PREPARED_EVIDENCE=new ClassValue<>(){protected Boolean computeValue(Class<?> type){if(type!=PlayerModel.class)return false;try{Object outer=type.getDeclaredField("arcadium$preparedOuter073").get(null),body=HumanoidModel.class.getDeclaredField("arcadium$preparedBody073").get(null);return outer instanceof String stamp&&stamp.length()==64&&stamp.equals(body);}catch(ReflectiveOperationException unknown){return false;}}};
    static boolean preparedEvidence(Object value){return value!=null&&PREPARED_EVIDENCE.get(value.getClass());}
    private static ModelPart preparedPart(PlayerModel m,int i){return switch(i){case 0->m.head;case 1->m.body;case 2->m.rightArm;case 3->m.leftArm;case 4->m.rightLeg;case 5->m.leftLeg;case 6->m.hat;case 7->m.leftPants;case 8->m.rightPants;case 9->m.leftSleeve;case 10->m.rightSleeve;case 11->m.jacket;default->throw new IllegalArgumentException("Unknown player part");};}
    private static final PreparedBlockState073.Parts PREPARED_PARTS=new PreparedBlockState073.Parts(){
        public Object part(Object model,int i){return preparedPart((PlayerModel)model,i);}public boolean standard(Object part){return standardPart((ModelPart)part);}public boolean finite(Object part){return finitePose((ModelPart)part);}public boolean visible(Object part){ModelPart p=(ModelPart)part;return p.visible&&!p.invisible;}public Object box(Object part){return ((ModelPart)part).boxes.get(0);}public Object mesh(Object part){return GpuVoxelSkinBridge.mesh((ModelPart)part);}
    };
    private static final PreparedBlockState073.Selection PREPARED_SELECTION=new PreparedBlockState073.Selection(){
        public Object current()throws Throwable{return api.current.invoke();}public boolean active(Object actor)throws Throwable{return(boolean)api.active.invoke(actor);}public Object capture(Object actor)throws Throwable{return api.activeCapture.get(actor);}public Object backend(Object actor)throws Throwable{return api.backend.get(actor);}public Object pass(Object capture)throws Throwable{return api.capturePass.get(capture);}public Object texture(Object capture)throws Throwable{return api.captureTexture.get(capture);}
        public boolean material(Object capture,Object model,Object texture,Object pass)throws Throwable{String kind=((Enum<?>)pass).name();return (boolean)api.capturePlayer.get(capture)&&!(boolean)api.captureArmor.get(capture)&&(boolean)api.captureActive.get(capture)&&!(boolean)api.captureFinished.get(capture)&&(int)api.captureMode.get(capture)==5888&&(kind.equals("NORMAL")||kind.equals("TRANSLUCENT"))&&!CeraEmissiveAdmission.nativeSubmitRequired(capture,model,(Identifier)texture,kind);}
        public Object select(Object backend,Object texture,Object pass)throws Throwable{return preparedSelector.select(backend,texture,pass);}public int layer(Object backend)throws Throwable{return(int)api.selectedLayer.get(backend);}
    };
    private static final class PreparedLease implements PreparedBlockState073.Counters {
        Object capture,actor,device,batch,pass;Identifier texture;PlayerModel model;Profile profile;CaptureState context;
        final PreparedBlockState073.Counts snapshot=new PreparedBlockState073.Counts();final ModelPart[] parts=new ModelPart[12];final Object[] boxes=new Object[12];final PreparedBlockState073.Material material=new PreparedBlockState073.Material();final GpuPlayerPackets.PreparedAppend append=new GpuPlayerPackets.PreparedAppend();final Object[] instances=new Object[2];final int[] sizes=new int[2],counts=new int[2];
        long epoch,sequence;int visible,skin;boolean playerCounted;
        public int size(int i){return(int)api.size.get(instances[i]);}public int count(int i){return(int)api.count.get(instances[i]);}public int batch(){return(int)api.batchCount.get(batch);}public int device(){return(int)api.backendCount.get(device);}public boolean recorded(){return(boolean)api.captureRecorded.get(capture);}
        public void size(int i,int value){api.size.set(instances[i],value);}public void count(int i,int value){api.count.set(instances[i],value);}public void batch(int value){api.batchCount.set(batch,value);}public void device(int value){api.backendCount.set(device,value);}public void recorded(boolean value){api.captureRecorded.set(capture,value);}
        void clear(){capture=actor=device=batch=pass=null;model=null;profile=null;context=null;texture=null;java.util.Arrays.fill(parts,null);java.util.Arrays.fill(boxes,null);material.actor=material.capture=material.backend=material.pass=material.texture=material.batch=material.model=null;java.util.Arrays.fill(instances,null);visible=skin=0;}
    }
    private static final class PreparedAdapter implements PreparedPlayerEmission073.Adapter {
        public Object createLease(){return new PreparedLease();}
        public Object preflight(Object raw,Object entity,float scale,Object reuse){
            if(io.github.arcadiumclient.runtime.client.features.Skin3DConfig.getEnabled()&&!GpuVoxelSkinRenderer.preparedProjectionQualified()){preparedRefusals++;return null;}
            PreparedLease lease=reuse instanceof PreparedLease old?old:new PreparedLease();lease.clear();
            try{
                if(!preparedEnabled()||!preparedEvidence(raw)||scale!=.0625f||!(raw instanceof PlayerModel model)||model.isBaby||!(entity instanceof net.minecraft.client.entity.living.player.ClientPlayerEntity))return refuse(lease);
                if(!GpuModelTransforms.ready())return refuse(lease);if(api==null)api=new PlayerBatchAccess();if(preparedSelector==null)preparedSelector=new GpuVoxelSkinSelector(api.select);Object actor=api.current.invoke();if(actor==null||!(boolean)api.active.invoke(actor))return refuse(lease);Object capture=api.activeCapture.get(actor);
                if(!(capture instanceof CaptureAccess access)||!(boolean)api.capturePlayer.get(capture)||(boolean)api.captureArmor.get(capture)||!(boolean)api.captureActive.get(capture)||(boolean)api.captureFinished.get(capture)||(int)api.captureMode.get(capture)!=5888)return refuse(lease);
                Object pass=api.capturePass.get(capture);String kind=((Enum<?>)pass).name();Identifier texture=(Identifier)api.captureTexture.get(capture);if(texture==null||!kind.equals("NORMAL")&&!kind.equals("TRANSLUCENT")||CeraEmissiveAdmission.nativeSubmitRequired(capture,model,texture,kind))return refuse(lease);
                Object device=api.backend.get(actor);backend(device);Profile profile=profiles.get(model);if(profile==null){if(profiles.size()+originalVertexProfiles.size()>=MAX_PROFILES||templateBudgetExhausted)return refuse(lease);profile=new Profile(model,device,false);profiles.put(model,profile);}if(profile.template<0||profile.parts.size()!=12)return refuse(lease);
                ModelPart[] parts=lease.parts;for(int i=0;i<12;i++)parts[i]=preparedPart(model,i);int visible=0;
                for(int i=0;i<12;i++){ModelPart part=parts[i];Part shape=profile.parts.get(part);if(shape==null||!standardPart(part)||!finitePose(part)||part.boxes.get(0)!=shape.originalBox)return refuse(lease);if(part.visible&&!part.invisible)visible|=1<<i;lease.parts[i]=part;lease.boxes[i]=shape.originalBox;}
                int skin=GpuSkinLayers.mask(parts);if(io.github.arcadiumclient.runtime.client.features.Skin3DConfig.getEnabled()){
                    if(skin==0||!GpuVoxelSkinRenderer.preparedProjectionQualified())return refuse(lease);
                    PreparedVoxelLease073 voxel=GpuVoxelSkinRenderer.preflightPrepared(device,capture,lease.parts,skin,texture,profile.template);
                    if(voxel==null||!voxel.projectionQualified())return refuse(lease);
                    return refuse(lease); // Whole-skin admission is deliberately absent from this first body cut.
                }
                CaptureState context=access.arcadium$nativePoseState();if(context.epoch!=batchEpoch)context.beginEpoch();if(context.groups.remaining()<2||GpuPlayerPackets.MAX_PLAYERS-GpuPlayerPackets.count()<3)return refuse(lease);
                Object batch=preparedSelector.select(device,texture,pass);for(int mirror=0;mirror<2;mirror++){
                    Object instances=api.instances.invoke(profile.geometry[mirror],batch);int n=0;for(int i=0;i<12;i++)if((visible&1<<i)!=0&&(skin&1<<i)==0&&profile.parts.get(parts[i]).mirrored==(mirror==1))n++;
                    int size=(int)api.size.get(instances);int[] data=(int[])api.data.get(instances);if(size<0||size%19!=0||size+n*19>65536*19)return refuse(lease);if(size+n*19>data.length)api.data.set(instances,java.util.Arrays.copyOf(data,Math.max(size+n*19,data.length*2)));lease.instances[mirror]=instances;
                }
                lease.capture=capture;lease.actor=actor;lease.device=device;lease.batch=batch;lease.pass=pass;lease.texture=texture;lease.model=model;lease.profile=profile;lease.context=context;lease.epoch=batchEpoch;lease.sequence=context.sequence;lease.material.actor=actor;lease.material.capture=capture;lease.material.backend=device;lease.material.pass=pass;lease.material.texture=texture;lease.material.batch=batch;lease.material.model=model;lease.visible=visible;lease.skin=skin;preparedActors++;return lease;
            }catch(Throwable unsupported){return refuse(lease);}
        }
        private Object refuse(PreparedLease lease){lease.clear();preparedRefusals++;return null;}
        public boolean emit(Object value,int block){
            PreparedLease l=(PreparedLease)value;int oldBatch=0,oldDevice=0,groupCheckpoint=-1;boolean bytesCheckpoint=false,recorded=false;
            try{
                if(l.epoch!=batchEpoch||l.sequence!=l.context.sequence||l.model.isBaby||io.github.arcadiumclient.runtime.client.features.Skin3DConfig.getEnabled())return false;
                int mask=PreparedBlockState073.liveMask(PREPARED_PARTS,l.model,l.parts,l.boxes,l.visible,block);if(mask<0)return false;
                int layer=PreparedBlockState073.selectOwned(PREPARED_SELECTION,l.material);if(layer==Integer.MIN_VALUE)return false;
                for(int mirror=0;mirror<2;mirror++)if(api.instances.invoke(l.profile.geometry[mirror],l.batch)!=l.instances[mirror])return false;
                if(l.epoch!=batchEpoch||l.sequence!=l.context.sequence||api.current.invoke()!=l.actor||!(boolean)api.active.invoke(l.actor)||(int)api.selectedLayer.get(l.device)!=layer)return false;
                mask=PreparedBlockState073.liveMask(PREPARED_PARTS,l.model,l.parts,l.boxes,l.visible,block);if(mask<0)return false;
                Matrix4fc matrix=(Matrix4fc)api.captureMatrices.get(l.capture);if(!finiteAffine(matrix))return false;if(mask==0)return true;
                if(l.context.groups.full())return false;
                l.snapshot.save(l);oldBatch=l.snapshot.batch();oldDevice=l.snapshot.device();
                for(int mirror=0;mirror<2;mirror++){Object instances=l.instances[mirror];l.sizes[mirror]=(int)api.size.get(instances);l.counts[mirror]=(int)api.count.get(instances);int n=0;for(int i=0;i<12;i++)if((mask&1<<i)!=0&&l.profile.parts.get(l.parts[i]).mirrored==(mirror==1))n++;if(l.sizes[mirror]+n*19>((int[])api.data.get(instances)).length)return false;}
                bytesCheckpoint=true;groupCheckpoint=l.context.groups.preparedCheckpoint();GpuPlayerPackets.beginPreparedAppend(l.append);
                int job=GpuPlayerPackets.addPreparedPose(matrix,l.profile.template,l.append);if(job<0){GpuPlayerPackets.rollbackPreparedAppend(l.append);return false;}
                NativePoseGroups.Entry group=l.context.groups.addPrepared(l.profile,l.pass,matrix,job,l.append);if(group==null)throw new IllegalStateException("Prepared group allocation failed");
                int light=(int)api.captureLight.get(l.capture),lightWord=(light&255)|((light>>>16&255)<<8)|((layer&255)<<16),color=pack((Vector4fc)api.captureColor.get(l.capture)),overlay=pack((Vector4fc)api.captureOverlay.get(l.capture));float time=(float)api.captureTime.get(l.capture);int added=0;
                for(int mirror=0;mirror<2;mirror++){Object instances=l.instances[mirror];int[] data=(int[])api.data.get(instances);int size=l.sizes[mirror],count=l.counts[mirror];
                    for(int i=0;i<12;i++){if((mask&1<<i)==0)continue;ModelPart part=l.parts[i];Part shape=l.profile.parts.get(part);if(shape.mirrored!=(mirror==1))continue;GpuPlayerPackets.writePose(job,i,part.x*.0625f+part.translateX,part.y*.0625f+part.translateY,part.z*.0625f+part.translateZ,part.rotationX,part.rotationY,part.rotationZ);java.util.Arrays.fill(data,size,size+19,0);data[size]=Float.floatToRawIntBits(-(job*16+i+1));data[size+1]=MAGIC_1;data[size+2]=MAGIC_2;data[size+12]=Float.floatToRawIntBits(time);data[size+13]=lightWord;data[size+14]=color;data[size+15]=overlay;data[size+16]=shape.uv;data[size+17]=shape.dimensions;data[size+18]=shape.size;size+=19;count++;added++;group.mark(i);}
                    api.size.set(instances,size);api.count.set(instances,count);l.append.point(7+mirror);
                }
                api.batchCount.set(l.batch,oldBatch+added);api.backendCount.set(l.device,oldDevice+added);l.append.point(9);api.captureRecorded.set(l.capture,true);l.append.point(10);GpuPlayerPackets.commitPreparedAppend(l.append);
                poseGroups++;bodyPartsQueued+=added;preparedParts+=added;if(!l.context.counted){playersQueued++;l.context.counted=true;}if(block==0)preparedBodyBlocks++;else preparedOuterBlocks++;return true;
            }catch(Throwable failedAppend){
                try{if(bytesCheckpoint){l.snapshot.restore(l);}if(groupCheckpoint>=0)l.context.groups.rollbackPrepared(groupCheckpoint);if(l.append.open())GpuPlayerPackets.rollbackPreparedAppend(l.append);}catch(Throwable rollbackFailed){preparedFailed=true;}
                return false;
            }
        }
        public void release(Object value){((PreparedLease)value).clear();}
    }

}
