// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ModernArmorGlintPipeline {
    private static final boolean QUALIFIED = false;
    private static final boolean EXPERIMENTAL = Boolean.getBoolean("arcadium.modernGlint072.experimental");
    private static final ModernGlintMatrices matrices = new ModernGlintMatrices();
    private static final int[] programHandles = new int[256], countLocations = new int[256];
    private static final boolean[] verifiedPrograms = new boolean[256];
    private static int programCount;
    private static final Scope[] scopes = new Scope[16];
    private static final DrawState[] draws = new DrawState[16];
    private static final Identifier ITEM = new Identifier("overflowanimations", "textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR = new Identifier("overflowanimations", "textures/misc/enchanted_glint_armor.png");
    private static PlayerBatchAccess api;
    private static MethodHandle getProgram, animationInstance, customEffects, customArmor, ceraGetter;
    private static VarHandle textureMatrices;
    private static Object animationItems, ceraItems;
    private static java.lang.reflect.Field armorGlint, animationItemsField;
    private static Object glintPass;
    private static final ModernArmorLayerSelection selection = new ModernArmorLayerSelection();
    private static long uploadedFrame = -1;
    private static int scopeDepth, drawDepth, storage, dummyStorage, storageCapacity, uploadedMatrices;
    private static boolean failed, ceraAbsent;
    private static FloatBuffer upload = BufferUtils.createFloatBuffer(128 * 16);
    private static volatile String state = "Waiting for a supported modern armor layer";
    public static long layersSupported, glintParts, nativeLayers, forgeLayers, ceraLayers;
    public static long dummyProgramBinds, materialProgramBinds;

    private static final class Scope {
        Object capture, oldPass, model;
        Identifier texture;
    }
    private static final class DrawState {
        int indexed, generic, program, location, uniform;
        long offset, size;
    }
    static { for (int i = 0; i < scopes.length; i++) scopes[i] = new Scope(); for (int i = 0; i < draws.length; i++) draws[i] = new DrawState(); }
    private ModernArmorGlintPipeline() {}
    public static boolean shaderAdapterEnabled() { return ModernGlintShaderPatch.qualificationEnabled(QUALIFIED, EXPERIMENTAL); }

    private static void initialize() throws Throwable {
        if (api != null) return;
        PlayerBatchAccess access = new PlayerBatchAccess();
        Class<?> capture = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
        textureMatrices = MethodHandles.privateLookupIn(capture, MethodHandles.lookup()).unreflectVarHandle(capture.getDeclaredField("textureMatrices"));
        Class<?> backend = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer");
        getProgram = MethodHandles.privateLookupIn(backend, MethodHandles.lookup()).unreflect(backend.getDeclaredMethod("getProgram"));
        Class<?> animation = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
        animationInstance = MethodHandles.lookup().unreflect(animation.getMethod("instance"));
        Object config = animationInstance.invoke(); animationItemsField = animation.getField("items"); animationItems = animationItemsField.get(config);
        armorGlint = animationItems.getClass().getField("armorGlint");
        Class<?> passes = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass");
        for (Object value : passes.getEnumConstants()) if (((Enum<?>)value).name().equals("GLINT")) glintPass = value;
        api = access;
    }

    public static void beginFrame() {
        matrices.beginFrame(); selection.clear();
    }
    public static void beginCapture() { selection.clear(); }

    public static boolean canCapture(Object layer, Object candidate) {
        selection.clear();
        if (!BetaOptimizationsConfig.getGpuEquipmentPipeline() || failed) return false;
        if (!shaderAdapterEnabled()) return nativeLayer("Original modern armor retained during renderer qualification");
        try {
            if (!GL.getCapabilities().OpenGL43 || !ModernGlintShaderPatch.vertexPatched || !ModernGlintShaderPatch.fragmentPatched || !GpuModelTransforms.ready()) return nativeLayer("Modern shader or player budget is not ready");
            if (GpuPlayerPipeline.hasForgeHooks()) { forgeLayers++; return nativeLayer("Forge armor state retains the original modern glint"); }
            if (layer == null || layer.getClass() != ArmorLayer.class || !(candidate instanceof LivingEntity entity)) return nativeLayer("Custom armor layer retains original glint");
            if (entity.damagedTimer > 0 || entity.deathTicks > 0) return nativeLayer("Hurt/death armor retains original modern glint");
            ArmorLayer armor = (ArmorLayer)layer;
            if (!standard(armor.getModel(1)) || !standard(armor.getModel(2))) return nativeLayer("Custom armor model retains original glint");
            if (matrices.remaining() < 64) return nativeLayer("Modern glint matrix budget reached");
            initialize();
            animationItems = animationItemsField.get(animationInstance.invoke());
            String style = ((Enum<?>)armorGlint.get(animationItems)).name();
            if (!style.equals("V1_15") && !style.equals("V1_19_4")) return false;
            if (hasCustomEffects(entity)) { ceraLayers++; return nativeLayer("Cera armor effects retain original glint"); }
            Object actor = api.current.invoke(); if (actor == null || !(boolean)api.active.invoke(actor)) return false;
            Object capture = api.activeCapture.get(actor);
            if (capture == null || !(boolean)api.capturePlayer.get(capture) || (boolean)api.captureFinished.get(capture)) return false;
            Object device = api.backend.get(actor);
            GlProgram<?> program = (GlProgram<?>)getProgram.invoke(device);
            if (verifiedProgramLocation(program.handle()) < 0) return nativeLayer("Linked modern shader block was not verified");
            if (!NativePlayerPosePipeline.preflightModernLayer(capture)) return nativeLayer("Modern armor pose budget reached");
            List<Identifier> textures = armorTextures(entity, style);
            if (textures == null) return nativeLayer("Custom or emissive armor material retains the original modern layer");
            boolean geometryReady = NativePlayerPosePipeline.prepareOriginalArmor((HumanoidModel)armor.getModel(1), (HumanoidModel)armor.getModel(2), device);
            boolean texturesReady = ModernArmorAuxiliary.prepareTextures(device, textures);
            if (!geometryReady || !texturesReady) return nativeLayer("Preparing owned modern armor stores; retaining the original layer");
            selection.select(capture, layer, candidate, armor.getModel(1), armor.getModel(2), matrices.generation());
            layersSupported++; state = "Captured modern texture matrices with native GLINT ordering"; return true;
        } catch (Throwable error) { disable(error); return false; }
    }

    private static boolean standard(Model input) {
        if (input == null || input.getClass() != HumanoidModel.class) return false;
        HumanoidModel model = (HumanoidModel)input;
        return standardPart(model.head) && standardPart(model.hat) && standardPart(model.body) && standardPart(model.rightArm) &&
                standardPart(model.leftArm) && standardPart(model.rightLeg) && standardPart(model.leftLeg);
    }

    private static List<Identifier> armorTextures(LivingEntity entity, String style) throws Throwable {
        java.util.ArrayList<Identifier> textures = new java.util.ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = entity.getArmor(slot); if (stack == null) continue;
            if (stack.getItem().getClass() != ArmorItem.class) return null;
            ArmorItem armor = (ArmorItem)stack.getItem(); String material = armor.getTier().getKey();
            if (!Set.of("leather", "chainmail", "iron", "gold", "diamond").contains(material)) return null;
            int layer = armor.slot == 2 ? 2 : 1;
            for (boolean overlay : armor.getTier() == ArmorItem.Tier.CLOTH ? new boolean[]{false, true} : new boolean[]{false}) {
                Identifier identifier = new Identifier("textures/models/armor/" + material + "_layer_" + layer + (overlay ? "_overlay" : "") + ".png");
                if (!ceraAbsent) {
                    if (customArmor == null) customArmor = MethodHandles.publicLookup().unreflect(ceraItems.getClass().getMethod("resolveArmor", ItemStack.class, String.class, int.class, boolean.class, Identifier.class));
                    if (!identifier.equals(customArmor.invoke(ceraItems, stack, material, layer, overlay, identifier))) return null;
                }
                if (CeraEmissiveAdmission.nativeSubmitRequired(identifier)) return null;
                textures.add(identifier);
            }
        }
        textures.add(style.equals("V1_19_4") ? ARMOR : ITEM); return textures;
    }
    private static boolean standardPart(ModelPart part) { return part.getClass() == ModelPart.class && part.boxes.size() == 1 && (part.children == null || part.children.isEmpty()); }

    private static boolean hasCustomEffects(LivingEntity entity) throws Throwable {
        if (ceraAbsent) return false;
        if (ceraGetter == null) {
            Object minecraft = Minecraft.getInstance();
            try { ceraGetter = MethodHandles.publicLookup().unreflect(minecraft.getClass().getMethod("cera$getCustomItems")); }
            catch (NoSuchMethodException absent) { ceraAbsent = true; return false; }
        }
        Object current = ceraGetter.invoke(Minecraft.getInstance());
        if (current == null) return true;
        if (customEffects == null || ceraItems.getClass() != current.getClass()) {
            customEffects = MethodHandles.publicLookup().unreflect(current.getClass().getMethod("effects", net.minecraft.item.ItemStack.class));
        }
        ceraItems = current;
        for (int slot = 0; slot < 4; slot++) {
            var stack = entity.getArmor(slot);
            if (stack != null && !((List<?>)customEffects.invoke(ceraItems, stack)).isEmpty()) return true;
        }
        return false;
    }

    public static int beginGlint(Object layer, LivingEntity entity, Model model) {
        if (failed || !selection.layer(layer, model, entity, matrices.generation()) || !standard(model) || scopeDepth == scopes.length) return 0;
        try {
            Object capture = selection.capture();
            Object actor = api.current.invoke(); if (actor == null || api.activeCapture.get(actor) != capture) return 0;
            if (!(boolean)api.captureArmor.get(capture) || !(boolean)api.captureActive.get(capture)) return 0;
            Object oldPass = api.capturePass.get(capture);
            String style = ((Enum<?>)armorGlint.get(animationItems)).name();
            ((Set<?>)api.captureGlint.get(capture)).clear();
            Scope scope = scopes[scopeDepth]; scope.capture = capture; scope.oldPass = oldPass; scope.model = model;
            scope.texture = style.equals("V1_19_4") ? ARMOR : ITEM; scopeDepth++;
            return scopeDepth;
        } catch (Throwable error) { disable(error); return 0; }
    }

    public static void endGlint(int token) {
        if (token == 0) return;
        Scope scope = scopes[token - 1];
        if (token != scopeDepth) { disable(new IllegalStateException("Modern glint scope order changed")); return; }
        try { api.capturePass.set(scope.capture, scope.oldPass); }
        catch (Throwable error) { disable(error); }
        finally { scope.capture = scope.oldPass = scope.model = null; scope.texture = null; scopeDepth--; }
    }

    public static void textureBound(Object capture, Identifier texture) {
        if (scopeDepth == 0) return;
        Scope scope = scopes[scopeDepth - 1];
        if (scope.capture == capture && scope.texture.equals(texture)) api.capturePass.set(capture, glintPass);
    }

    public static boolean active() { return scopeDepth != 0; }

    public static boolean originalVertices(Model model, Object capture) {
        if (failed) return false;
        if (scopeDepth != 0) {
            Scope scope = scopes[scopeDepth - 1];
            if (scope.capture == capture && scope.model == model) return true;
        }
        return selection.captureModel(capture, model, matrices.generation());
    }

    public static boolean nativeModelLayer(Model model, Object entity) {
        if (failed || !BetaOptimizationsConfig.getGpuEquipmentPipeline() || !selection.model(model, entity, matrices.generation())) return false;
        try {
            Object capture = selection.capture(), actor = api.current.invoke();
            return actor != null && api.activeCapture.get(actor) == capture && (boolean)api.capturePlayer.get(capture) &&
                    (boolean)api.captureArmor.get(capture) && (boolean)api.captureActive.get(capture) && !(boolean)api.captureFinished.get(capture);
        } catch (Throwable error) { disable(error); return false; }
    }

    public static float effectTime(Object capture, float original) {
        if (scopeDepth == 0) return original;
        Scope scope = scopes[scopeDepth - 1];
        if (scope.capture != capture || api.capturePass.get(capture) != glintPass) return original;
        int index = matrices.intern((Matrix4fc)textureMatrices.get(capture));
        if (index < 0) throw new IllegalStateException("Supported modern glint produced an invalid texture matrix");
        glintParts++; return ModernGlintMatrices.marker(index);
    }

    public static void programCreated(int program) {
        try {
            if (!shaderAdapterEnabled()) return;
            if (!GL.getCapabilities().OpenGL43 || GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) return;
            int block = GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, "ArcadiumModernGlintMatrices");
            int countLocation = GL20.glGetUniformLocation(program, "uArcadiumModernGlintCount");
            if (block == GL43.GL_INVALID_INDEX || countLocation < 0) return;
            int[] layout = new int[2];
            GL43.glGetProgramResourceiv(program, GL43.GL_SHADER_STORAGE_BLOCK, block,
                    new int[]{GL43.GL_BUFFER_BINDING, GL43.GL_BUFFER_DATA_SIZE}, (int[])null, layout);
            if (layout[0] != 7 || layout[1] < 0 || layout[1] > 64) return;
            int record = programIndex(program);
            if (record < 0) {
                if (programCount == programHandles.length) return;
                record = programCount++; programHandles[record] = program; countLocations[record] = countLocation; verifiedPrograms[record] = false;
            }
            int count = GL20.glGetProgrami(program, GL20.GL_ATTACHED_SHADERS); if (count < 2) return;
            IntBuffer shaders = BufferUtils.createIntBuffer(count); GL20.glGetAttachedShaders(program, (IntBuffer)null, shaders);
            boolean vertex = false, fragment = false;
            for (int i = 0; i < count; i++) {
                int shader = shaders.get(i); String source = GL20.glGetShaderSource(shader);
                if (!source.contains("ARC_DEFAULT_MODERN_ARMOR_GLINT")) continue;
                int type = GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE); vertex |= type == GL20.GL_VERTEX_SHADER; fragment |= type == GL20.GL_FRAGMENT_SHADER;
            }
            verifiedPrograms[record] = vertex && fragment;
        } catch (Throwable error) { state = "Modern shader verification unavailable; native armor retained"; }
    }
    private static int programIndex(int program) {
        for (int i = 0; i < programCount; i++) if (programHandles[i] == program) return i;
        return -1;
    }
    private static int verifiedProgramLocation(int program) {
        int record = programIndex(program); return record >= 0 && verifiedPrograms[record] ? countLocations[record] : -1;
    }
    static boolean verifiedProgram(int program) { return verifiedProgramLocation(program) >= 0; }
    public static void programDeleted(int program) {
        for (int i = 0; i < programCount; i++) if (programHandles[i] == program) {
            programCount--; programHandles[i] = programHandles[programCount]; countLocations[i] = countLocations[programCount]; verifiedPrograms[i] = verifiedPrograms[programCount]; return;
        }
    }

    public static int beginDraw() {
        return beginProgram(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }

    public static int beginProgram(int program) {
        if (!shaderAdapterEnabled()) return 0;
        int record = programIndex(program);
        if (record < 0 || drawDepth == draws.length || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program) return 0;
        int location = countLocations[record];
        DrawState draw = draws[drawDepth++];
        draw.program = program; draw.location = location; draw.uniform = GL20.glGetUniformi(program, location);
        draw.generic = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        draw.indexed = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7);
        draw.offset = draw.indexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 7);
        draw.size = draw.indexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7);
        if (matrices.count() == 0 || !verifiedPrograms[record]) {
            if (dummyStorage == 0) {
                dummyStorage = GL15.glGenBuffers(); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, dummyStorage);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, BufferUtils.createFloatBuffer(16), GL15.GL_STATIC_DRAW);
            }
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, dummyStorage);
            GL41.glProgramUniform1i(program, location, 0); dummyProgramBinds++; return drawDepth;
        }
        if (storage == 0) storage = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, storage);
        int words = matrices.count() * 16;
        if (uploadedFrame != matrices.generation() || words > storageCapacity) {
            if (words > storageCapacity) storageCapacity = Math.max(words, Math.max(128 * 16, storageCapacity * 2));
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long)storageCapacity * 4, GL15.GL_STREAM_DRAW);
            uploadedFrame = matrices.generation(); uploadedMatrices = 0;
        }
        if (uploadedMatrices != matrices.count()) {
            int start = uploadedMatrices * 16, length = words - start;
            if (upload.capacity() < length) upload = BufferUtils.createFloatBuffer(Integer.highestOneBit(length - 1) * 2);
            upload.clear(); upload.put(matrices.values(), start, length).flip();
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, (long)start * 4, upload); uploadedMatrices = matrices.count();
        }
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, storage);
        GL41.glProgramUniform1i(program, location, matrices.count());
        materialProgramBinds++;
        return drawDepth;
    }

    public static void endDraw(int token) {
        if (token == 0) return;
        DrawState draw = draws[token - 1];
        if (draw.indexed != 0 && draw.size > 0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 7, draw.indexed, draw.offset, draw.size);
        else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, draw.indexed);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, draw.generic); GL41.glProgramUniform1i(draw.program, draw.location, draw.uniform); drawDepth--;
    }

    private static boolean nativeLayer(String reason) { nativeLayers++; state = reason; return false; }
    private static void disable(Throwable error) {
        failed = true; state = "Modern armor adapter disabled; original layers retained";
        LogManager.getLogger("Arcadium/Modern Armor Glint").warn("Modern armor glint adapter disabled", error);
    }
    public static void close() {
        ModernArmorAuxiliary.close();
        matrices.beginFrame(); selection.clear();
        if (storage != 0) GL15.glDeleteBuffers(storage); storage = storageCapacity = uploadedMatrices = 0; uploadedFrame = -1;
        if (dummyStorage != 0) GL15.glDeleteBuffers(dummyStorage); dummyStorage = 0;
    }
    public static String status() {
        return BetaOptimizationsConfig.getGpuEquipmentPipeline() ? "Modern armor glint: " + state + " | Layers: " + layersSupported + " | Parts: " + glintParts : "Modern armor glint: Off";
    }
}
