// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.ModernGlintShaderPatch;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ModernGlint072Qa {
    private static final String POSE = System.getProperty("arcadium.modernGlint072.pose", "moving");
    private static final boolean BODY = Boolean.getBoolean("arcadium.modernGlint072.body");
    private static final String CAMERA = System.getProperty("arcadium.modernGlint072.camera", "default");
    private static final String SNEAK_ANIMATION=System.getProperty("arcadium.modernGlint072.sneakAnimation","configured");
    private static final String ACTOR_NAME=System.getProperty("arcadium.modernGlint072.actorName","ModernGlintQA");
    private static final String CAPE=System.getProperty("arcadium.modernGlint072.cape","configured");
    private static final String FORCE_DINNERBONE=System.getProperty("arcadium.modernGlint072.forceDinnerbone","configured");
    private static final boolean CONTROLLED_INVERSION=!ACTOR_NAME.equals("ModernGlintQA")||!CAPE.equals("configured")||!FORCE_DINNERBONE.equals("configured");
    private static final boolean ANGLED = CAMERA.equals("angled");
    private static final String[] STYLES = {"V1_19_4", "V1_15", "V1_19_4"};
    private static final String[] CASES = {"V1_19_4", "V1_15", "V1_19_4-hurt-fallback"};
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static Field styleField;
    private static Object items, originalStyle, movement, originalSneakAnimation;
    private static Field sneakAnimationField,dinnerbonePlayersField,dinnerboneEntitiesField;
    private static boolean originalDinnerbonePlayers,originalDinnerboneEntities,skinFlagsCaptured;
    private static byte originalActorSkinFlags;
    private static Frozen actor;
    private static int ticks, wait, phase, style, warmFrames, guardedDraws, orderChecks;
    private static int guard, genericGuard, originalIndexed, originalGeneric, alignment;
    private static long originalOffset, originalSize;
    private static boolean done, normalSeen;
    private static String failure;
    private static long styleLayers, styleParts;
    private static long styleNativeLayers;
    private static final java.util.Set<Integer> verifiedPrograms = new HashSet<>();
    private static final float[] nativeTextureMatrix = new float[16];
    private static boolean nativeTextureReady;
    private static int comparisonMode, textureChecks;
    private static double maximumTextureError;
    private static java.lang.reflect.Method currentCapture;
    private static Field capturedTextureMatrix;
    private static boolean baseOnly;
    private static final IdentityHashMap<net.minecraft.client.render.model.ModelPart, float[]> nativeModelViews = new IdentityHashMap<>();
    private static final float[] cameraModelView = new float[16];
    private static final IdentityHashMap<net.minecraft.client.render.model.ModelPart,Map<String,Object>> nativeModelRoots=new IdentityHashMap<>();
    private static final IdentityHashMap<net.minecraft.client.render.model.ModelPart,Map<String,Object>> pendingNativeModelRoots=new IdentityHashMap<>();
    private static long nativeRootEntries,nativePairedRootDraws,nativeFrameToken,nativeReferenceGeneration;
    private static int modelViewChecks;
    private static double maximumModelViewError;
    private static double maximumRootModelViewError;
    private static final List<String> pixelFailures = new java.util.ArrayList<>();
    private static int glintDepth;
    private static boolean zeroBindingGuard;
    private static int zeroRangeChecks, zeroMaterialDrawChecks;
    private static Future<?> environment;
    private static boolean cameraPrepared;
    private static int nativePlayerEntries, nativeLayerAttempts, translationChecks;
    private static String nativeLayerType = "";
    private static final float[] actualEntryTranslation = new float[16], shiftedEntryTranslation = new float[16];
    private static int matrixBufferChecks;
    private static long minimumNoMaterialStore=Long.MAX_VALUE;
    private static int wholeStoreChecks;
    private static boolean auxiliaryLifecycleDone;

    private ModernGlint072Qa() {}
    public static boolean enabled() { return System.getProperty("arcadium.modernGlint072.output") != null; }

    public static void tick() {
        if (done) return;
        try {
            Minecraft mc = Minecraft.getInstance(); wait++;
            require(++ticks < 6000, "Modern glint QA timed out");
            if (failure != null) throw new IllegalStateException(failure);
            if (phase == 0 && wait > 60 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Modern glint QA requires the isolated fake account");
                require(List.of("moving", "standing", "sneaking").contains(POSE) && List.of("default", "angled").contains(CAMERA), "Unknown owned Modern fixture variant");
                require(!Boolean.getBoolean("arcadium.forge.prepared"), "Positive modern glint fixture must not fake or enable Forge");
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                require(validDummyExtent(7,7,0,0,64,64)&&validDummyExtent(7,7,0,64,64,64),"Whole-store/range64 extent policy rejected a valid binding");
                require(!validDummyExtent(7,0,0,0,64,64)&&!validDummyExtent(7,8,0,0,64,64)&&!validDummyExtent(7,7,0,0,0,64)&&!validDummyExtent(7,7,0,0,63,64)
                        &&!validDummyExtent(7,7,1,0,128,64)&&!validDummyExtent(7,7,0,65,64,64)&&!validDummyExtent(7,7,-1,64,128,64)&&!validDummyExtent(7,7,0,-1,128,64),"Invalid/unknown/undersized extent policy was accepted");
                report.put("specBufferExtentPolicyCases",10);
                Skin3DConfig.INSTANCE.chooseEnabled(false);
                BetaOptimizationsConfig.setGpuModelTransforms(false); BetaOptimizationsConfig.setGpuPlayerPipeline(false);
                setPaths(true); BetaOptimizationsConfig.setMinimumPlayers(1);
                BetaOptimizationsConfig.setGpuNameTags(false); BetaOptimizationsConfig.setGpuParticles(false);
                BetaOptimizationsConfig.setWorldRenderScale(false); BetaOptimizationsConfig.setRetainedHudBatches(false);
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.fpsLimit = 120; mc.options.viewDistance = 3;
                if (ANGLED) mc.options.fov = 82;
                mc.options.cloudRenderMode = 0; mc.options.perspective = 0; mc.options.viewBobbing = false; mc.options.hideGui = true;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                Class<?> configType = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
                Object config = configType.getMethod("instance").invoke(null);
                require(List.of("configured","VANILLA","V1_7").contains(SNEAK_ANIMATION),"Unknown native sneak setting");
                require(List.of("ModernGlintQA","Dinnerbone","Grumm").contains(ACTOR_NAME)&&List.of("configured","on","off").contains(CAPE)&&List.of("configured","on","off").contains(FORCE_DINNERBONE),"Unknown actual public inversion control");
                movement=configType.getField("movement").get(config);
                dinnerbonePlayersField=movement.getClass().getField("dinnerboneMode");dinnerboneEntitiesField=movement.getClass().getField("dinnerboneModeEntities");originalDinnerbonePlayers=dinnerbonePlayersField.getBoolean(movement);originalDinnerboneEntities=dinnerboneEntitiesField.getBoolean(movement);
                if(!FORCE_DINNERBONE.equals("configured")){dinnerbonePlayersField.setBoolean(movement,FORCE_DINNERBONE.equals("on"));dinnerboneEntitiesField.setBoolean(movement,false);}
                sneakAnimationField=movement.getClass().getField("sneakAnimation");originalSneakAnimation=sneakAnimationField.get(movement);
                if(!SNEAK_ANIMATION.equals("configured")){boolean selected=false;for(Object setting:sneakAnimationField.getType().getEnumConstants())if(((Enum<?>)setting).name().equals(SNEAK_ANIMATION)){sneakAnimationField.set(movement,setting);selected=true;}require(selected,"Native requested sneak setting is absent");}
                boolean actualOld=(boolean)Class.forName("org.polyfrost.overflowanimations.legacy.model.LegacyEyeHeight").getMethod("isOldSneakModel").invoke(null);
                if(SNEAK_ANIMATION.equals("V1_7"))require(actualOld,"Native old sneak mode did not apply");if(SNEAK_ANIMATION.equals("VANILLA"))require(!actualOld,"Native modern sneak mode did not apply");
                report.put("actualNativeSneakMode",Map.of("requested",SNEAK_ANIMATION,"setting",((Enum<?>)sneakAnimationField.get(movement)).name(),"isOldSneakModel",actualOld));
                items = configType.getField("items").get(config);
                styleField = items.getClass().getField("armorGlint"); originalStyle = styleField.get(items); selectStyle();
                report.put("purpose", "Same-frame native/modern GPU pixel and GL state correctness; not FPS data");
                report.put("experimentalModernArmorRoute", Boolean.getBoolean("arcadium.modernGlint072.experimental"));
                report.put("privateMvpMode", ModernArmorGlintPipeline.mvpMode());
                report.put("nativeLayerRootRequested",Boolean.getBoolean("arcadium.nativeLayerRoot072.replay"));
                report.put("fixtureVariant", Map.of("pose", POSE, "bodyPipeline", BODY, "camera", CAMERA, "fov", mc.options.fov));
                report.put("settings", Map.of("fpsLimit", 120, "renderDistance", 3, "minimumPlayers", 1, "gpuPlayerPipeline", BODY,
                        "gpuModelTransforms", false, "gpuNameTags", false, "gpuParticles", false, "worldRenderScale", false,
                        "retainedHudBatches", false, "renderDelta", .5f));
                mc.startGame("arcadium-modern-glint072", "Modern Glint QA", new WorldSettings(720721, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 100) {
                if(environment==null){environment=mc.getServer().executeTask(()->{
                    var world=mc.getServer().getWorld(0);world.getGameRules().set("doMobSpawning","false");world.getGameRules().set("doDaylightCycle","false");world.getGameRules().set("randomTickSpeed","0");world.setTimeOfDay(6000);
                    world.getData().setRaining(false);world.getData().setThundering(false);int removed=0;
                    for(var entity:new java.util.ArrayList<>(world.entities))if(!(entity instanceof net.minecraft.entity.living.player.PlayerEntity)){world.removeEntityNow(entity);removed++;}return removed;
                });return;}
                if(!environment.isDone()||mc.world.entities.stream().anyMatch(entity->!(entity instanceof net.minecraft.entity.living.player.PlayerEntity)))return;
                report.put("removedInitialNonPlayers",environment.get());
                if(!cameraPrepared){mc.openScreen(null);mc.player.abilities.flying=true;mc.player.updatePositionAndAngles(ANGLED?1.25:0,ANGLED?6.75:6,ANGLED?-11:-10,ANGLED?7:0,ANGLED?9:5);cameraPrepared=true;return;}
                if(!CeraAdmission072Readiness.ready(mc,Path.of(System.getProperty("arcadium.modernGlint072.output"))))return;
                actor = new Frozen(mc, new GameProfile(new UUID(0x7210000000000000L, 1), ACTOR_NAME));
                originalActorSkinFlags=actor.getSyncedData().getByte(10);skinFlagsCaptured=true;
                if(!CAPE.equals("configured")){int flag=net.minecraft.client.render.model.PlayerModelPart.CAPE.getFlag();actor.getSyncedData().update(10,(byte)(CAPE.equals("on")?originalActorSkinFlags|flag:originalActorSkinFlags&~flag));}
                actor.updatePositionAndAngles(0, 4, 3, 190, 12);
                actor.setEquipment(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                net.minecraft.item.Item[] pieces = {net.minecraft.item.Items.DIAMOND_BOOTS, net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_HELMET};
                for (int slot = 1; slot <= 4; slot++) {
                    var stack = new net.minecraft.item.ItemStack(pieces[slot - 1]); stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING, 1); actor.setEquipment(slot, stack);
                }
                mc.world.forceEntity(721072, actor); freeze(mc);
                report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("resolution", mc.width + "x" + mc.height);
                phase = 2; warmFrames = 0;
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void setPaths(boolean equipment) {
        BetaOptimizationsConfig.setGpuPlayerPipeline(BODY && equipment);
        BetaOptimizationsConfig.setGpuEquipmentPipeline(equipment);
    }

    private static void selectStyle() throws IllegalAccessException {
        boolean found = false;
        for (Object value : styleField.getType().getEnumConstants()) if (((Enum<?>)value).name().equals(STYLES[style])) { styleField.set(items, value); found = true; }
        require(found, "Requested native armor glint style is unavailable: " + STYLES[style]);
        styleLayers = ModernArmorGlintPipeline.layersSupported; styleParts = ModernArmorGlintPipeline.glintParts;
        styleNativeLayers = ModernArmorGlintPipeline.nativeLayers;
    }

    private static void freeze(Minecraft mc) {
        if (actor == null) return;
        actor.ticks = 160; actor.bodyYaw = actor.lastBodyYaw = 190; actor.headYaw = actor.lastHeadYaw = 205;
        actor.pitch = actor.lastPitch = 12; actor.yaw = actor.lastYaw = 190;
        actor.setSneaking(POSE.equals("sneaking"));
        actor.walkAnimationProgress = POSE.equals("moving")?2.4f:0; actor.walkAnimationSpeed = actor.lastWalkAnimationSpeed = POSE.equals("moving")?.6f:0;
        actor.attackAnimationProgress = actor.lastAttackAnimationProgress = POSE.equals("moving")?.35f:0;
        actor.damagedTimer = style == 2 ? 5 : 0;
        actor.lastX = actor.prevX = actor.x; actor.lastY = actor.prevY = actor.y; actor.lastZ = actor.prevZ = actor.z;
        mc.player.yaw = mc.player.lastYaw = ANGLED?7:0; mc.player.pitch = mc.player.lastPitch = ANGLED?9:5;
        mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.world.setTimeOfDay(6000);
    }

    public static void world(float delta, long deadline, Operation<Void> original) {
        nativeFrameToken++;clearNativeReference();
        if (!done && phase == 3 && !auxiliaryLifecycleDone) {
            try {
                Minecraft mc = Minecraft.getInstance(); freeze(mc); setPaths(false);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline);
                BufferedImage before = capture(mc);
                io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa.closeAndValidate();
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline);
                BufferedImage after = capture(mc); int changed = 0;
                for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) if (before.getRGB(x,y) != after.getRGB(x,y)) changed++;
                report.put("auxiliaryLifecycle", io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa.finish());
                report.put("mainNativePixelsChangedAfterAuxiliaryClose", changed);
                require(changed == 0, "Closing auxiliary resources changed surviving Main native rendering");
                io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa.armColdFailure();
                BetaOptimizationsConfig.setGpuEquipmentPipeline(true);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline);
                BufferedImage failureImage = capture(mc); int failureChanged = 0;
                for (int y = 0; y < after.getHeight(); y++) for (int x = 0; x < after.getWidth(); x++) if (after.getRGB(x,y) != failureImage.getRGB(x,y)) failureChanged++;
                report.put("auxiliaryColdFailure", io.github.arcadiumclient.runtime.client.optimizations.ModernArmorAuxiliary072Qa.finishColdFailure());
                report.put("mainNativePixelsChangedByColdFailure", failureChanged);
                require(failureChanged == 0, "Failed cold preparation changed surviving Main native rendering");
                auxiliaryLifecycleDone = true;
            } catch (Throwable error) { failure = error.toString(); }
            return;
        }
        if (done || phase != 2) { original.call(delta, deadline); return; }
        try {
            Minecraft mc = Minecraft.getInstance(); freeze(mc); normalSeen = false;
            if (++warmFrames <= (style == 0 ? 80 : 60)) {
                zeroBindingGuard = style == 0 && warmFrames <= 20;
                setPaths(!zeroBindingGuard);
                guardBegin();
                try { original.call(.5f, deadline); guardCheck(); }
                finally { guardEnd(); zeroBindingGuard = false; setPaths(true); }
                return;
            }
            String effectiveStyle = ((Enum<?>)styleField.get(items)).name();
            report.put("nativeActorRegistration",CeraAdmission072Readiness.actors(mc,List.of(actor)));
            require(Boolean.TRUE.equals(((Map<?,?>)report.get("nativeActorRegistration")).get("allRegistered")),"Modern actor is absent from its actual mature native section");
            report.put("nativePlayerRendererEntries",nativePlayerEntries);report.put("nativeLayerAttempts",nativeLayerAttempts);report.put("nativeLayerType",nativeLayerType);
            report.put("effectiveEquipmentPipeline",BetaOptimizationsConfig.getGpuEquipmentPipeline());
            report.put("nativeLegacyGlintReplacement",Class.forName("org.polyfrost.overflowanimations.legacy.tint.LegacyGlint").getMethod("replacesArmorGlint").invoke(null));
            require(effectiveStyle.equals(STYLES[style]), "Another callback changed the requested native armor style to " + effectiveStyle);
            if (style < 2) require(ModernArmorGlintPipeline.layersSupported > styleLayers && ModernArmorGlintPipeline.glintParts > styleParts,
                    "Modern adapter did not activate: " + ModernArmorGlintPipeline.status() + " shaderPins=" + ModernGlintShaderPatch.vertexPatched + "/" + ModernGlintShaderPatch.fragmentPatched);
            else require(ModernArmorGlintPipeline.layersSupported == styleLayers && ModernArmorGlintPipeline.glintParts == styleParts &&
                    ModernArmorGlintPipeline.nativeLayers > styleNativeLayers, "Unsupported hurt armor was not retained by the original scoped layer");
            if (style < 2) baseControl(mc, deadline, original);
            setPaths(false); normalSeen = false;
            comparisonMode = 1; nativeTextureReady = false; clearNativeReference();
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage nativeImage = capture(mc);
            setPaths(true); normalSeen = false;
            long bodyBefore = io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline.playersDrawn;
            comparisonMode = 2;
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage optimized = capture(mc);
            long actualBody = io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline.playersDrawn-bodyBefore;
            if (BODY && style<2) require(actualBody>0, "Requested body path did not actually execute: "+io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline.status());
            if (BODY && style==2) require(actualBody==0, "Unsupported hurt actor entered the direct body path");
            report.put(CASES[style]+"-body", Map.of("requested", BODY, "actualPlayersDrawn", actualBody, "status", io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline.status()));
            comparisonMode = 0;
            Path directory = Path.of(System.getProperty("arcadium.modernGlint072.output")).toAbsolutePath().getParent(); Files.createDirectories(directory);
            ImageIO.write(nativeImage, "png", directory.resolve(CASES[style] + "-native.png").toFile()); ImageIO.write(optimized, "png", directory.resolve(CASES[style] + "-gpu.png").toFile());
            int maximum = 0, changed = 0;
            for (int y = 0; y < nativeImage.getHeight(); y++) for (int x = 0; x < nativeImage.getWidth(); x++) {
                int a = nativeImage.getRGB(x, y), b = optimized.getRGB(x, y); if (a != b) changed++;
                for (int shift = 0; shift < 32; shift += 8) maximum = Math.max(maximum, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
            }
            report.put(CASES[style], Map.of("maximumChannelError", maximum, "changedPixels", changed,
                    "layers", ModernArmorGlintPipeline.layersSupported - styleLayers, "taggedGlintStates", ModernArmorGlintPipeline.glintParts - styleParts,
                    "originalFallbackLayers", ModernArmorGlintPipeline.nativeLayers - styleNativeLayers, "effectiveStyle", effectiveStyle,
                    "mode", style < 2 ? "captured-modern-glint" : "original-hurt-glint"));
            if (maximum > 2) pixelFailures.add(CASES[style] + " exceeds the native 8-bit instance-color tolerance2: " + maximum);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after modern glint comparison");
            if (++style == STYLES.length) phase = 3;
            else { selectStyle(); warmFrames = 0; }
        } catch (Throwable error) { failure = error.toString(); }
    }

    private static void baseControl(Minecraft mc, long deadline, Operation<Void> original) throws Exception {
        baseOnly = true;
        try {
            setPaths(false); normalSeen = false; comparisonMode = 1; clearNativeReference();
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage nativeImage = capture(mc);
            setPaths(true); normalSeen = false; comparisonMode = 2;
            guardBegin();
            try { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); original.call(.5f, deadline); guardCheck(); }
            finally { guardEnd(); }
            BufferedImage optimized = capture(mc);
            Path directory = Path.of(System.getProperty("arcadium.modernGlint072.output")).toAbsolutePath().getParent();
            ImageIO.write(nativeImage, "png", directory.resolve(CASES[style] + "-base-only-native.png").toFile());
            ImageIO.write(optimized, "png", directory.resolve(CASES[style] + "-base-only-gpu.png").toFile());
            int maximum = 0, changed = 0, aboveTwo = 0;
            for (int y = 0; y < nativeImage.getHeight(); y++) for (int x = 0; x < nativeImage.getWidth(); x++) {
                int a = nativeImage.getRGB(x, y), b = optimized.getRGB(x, y), error = 0;
                for (int shift = 0; shift < 32; shift += 8) error = Math.max(error, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
                maximum = Math.max(maximum, error); if (a != b) changed++; if (error > 2) aboveTwo++;
            }
            report.put(CASES[style] + "-base-control-diagnostic", Map.of("glintOmittedInBothModes", true, "maximumChannelError", maximum, "changedPixels", changed, "pixelsAboveTwo", aboveTwo));
        } finally { baseOnly = false; comparisonMode = 0; }
    }

    public static boolean omitGlint() { return enabled() && baseOnly; }
    public static void beginGlint() { if (enabled()) glintDepth++; }
    public static void endGlint() { if (enabled()) glintDepth--; }
    public static boolean tracingNative() { return enabled() && !done && phase == 2 && style < 2 && comparisonMode == 1; }
    public static boolean tracingGpu() { return enabled() && !done && phase == 2 && style < 2 && comparisonMode == 2; }
    public static boolean tracingGlint() { return glintDepth > 0; }
    public static String traceCase() { return CASES[style] + (baseOnly ? "-base-only" : "-full"); }
    public static boolean ownsActor(Object entity) { return actor != null && entity == actor; }
    public static String currentCase() { return CASES[style]; }
    public static boolean needsModelViewReference() { return enabled() && !done && phase == 2 && comparisonMode == 2; }
    public static void referenceFailure(Throwable error) { failure = error.toString(); }
    public static void playerEntry(net.minecraft.entity.Entity entity,double x,double y,double z){
        if(!enabled()||done||phase!=2||entity!=actor)return;
        nativePlayerEntries++;
        if(translationChecks>=16)return;
        try{
            double entityY=actor.lastY+(actor.y-actor.lastY)*.5,derivedCameraY=entityY-y,oldRelativeY=(entityY+13)-(derivedCameraY+13);
            require(Float.floatToRawIntBits((float)y)==Float.floatToRawIntBits((float)oldRelativeY),"Height-shift fixture changed the actual renderer-relative translation");
            int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glMatrixMode(GL11.GL_MODELVIEW);
            try{
                GL11.glPushMatrix();try{GL11.glTranslatef((float)x,(float)y,(float)z);GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,actualEntryTranslation);}finally{GL11.glPopMatrix();}
                GL11.glPushMatrix();try{GL11.glTranslatef((float)x,(float)oldRelativeY,(float)z);GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,shiftedEntryTranslation);}finally{GL11.glPopMatrix();}
            }finally{GL11.glMatrixMode(matrixMode);}
            for(int i=0;i<16;i++)require(Float.floatToRawIntBits(actualEntryTranslation[i])==Float.floatToRawIntBits(shiftedEntryTranslation[i]),"Native entry matrix changed under equal actor/camera height shift");
            translationChecks++;matrixBufferChecks+=16;
            report.put("nativeHeightShiftEquivalence",Map.of("actorY",entityY,"derivedCameraY",derivedCameraY,"actualRendererXYZ",List.of(x,y,z),"heightShift",13,"reconstructedOldRelativeY",oldRelativeY,"actualTranslatedModelView",actualEntryTranslation.clone(),"shiftedTranslatedModelView",shiftedEntryTranslation.clone(),"bitExact",true));
        }catch(Throwable error){failure=error.toString();}
    }
    public static void layerAttempt(Object layer,Object entity){if(enabled()&&!done&&phase==2&&entity==actor){nativeLayerAttempts++;nativeLayerType=layer==null?"null":layer.getClass().getName();}}
    public static void captureCamera() {
        if (needsModelViewReference()) GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, cameraModelView);
    }
    private static void clearNativeReference(){nativeModelViews.clear();nativeModelRoots.clear();pendingNativeModelRoots.clear();nativeReferenceGeneration++;}
    public static void nativeModelRoot(net.minecraft.client.render.model.ModelPart part){
        if(!tracingNative()||((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel().getClass()!=net.minecraft.client.render.model.entity.HumanoidModel.class)return;
        float[] root=new float[16],projection=new float[16];GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,root);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,projection);
        pendingNativeModelRoots.put(part,Map.of("modelView",root,"projection",projection,"matrixMode",GL11.glGetInteger(GL11.GL_MATRIX_MODE),"matrixDepth",GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),"beforePartTransforms",true,"frameToken",nativeFrameToken,"referenceGeneration",nativeReferenceGeneration,"partIdentity",System.identityHashCode(part)));nativeRootEntries++;
    }
    public static void nativeModelView(net.minecraft.client.render.model.ModelPart part) {
        if (!enabled() || done || phase != 2 || comparisonMode != 1 ||
                ((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel().getClass() != net.minecraft.client.render.model.entity.HumanoidModel.class) return;
        if(style<2){
            Map<String,Object> entry=pendingNativeModelRoots.remove(part);
            require(entry!=null&&((Number)entry.get("frameToken")).longValue()==nativeFrameToken&&((Number)entry.get("referenceGeneration")).longValue()==nativeReferenceGeneration&&((Number)entry.get("partIdentity")).intValue()==System.identityHashCode(part),"Actual native callList has no matching current-frame part ENTRY");
            nativeModelRoots.put(part,entry);nativePairedRootDraws++;
        }
        float[] matrix = new float[16]; GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, matrix); nativeModelViews.put(part, matrix);
    }
    public static void optimizedModelView(net.minecraft.client.render.model.ModelPart part, org.joml.Matrix4fc local) {
        float[] reference = nativeModelViews.get(part); if (reference == null) return;
        var actual = new org.joml.Matrix4f().set(cameraModelView).mul(local);
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
            maximumModelViewError = Math.max(maximumModelViewError, Math.abs(actual.get(column, row) - reference[column * 4 + row]));
        modelViewChecks++;
    }
    public static void optimizedRootModelView(org.joml.Matrix4fc local) {
        float[] nativeRoot = new float[16]; GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, nativeRoot);
        var actual = new org.joml.Matrix4f().set(cameraModelView).mul(local);
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
            maximumRootModelViewError = Math.max(maximumRootModelViewError, Math.abs(actual.get(column, row) - nativeRoot[column * 4 + row]));
    }

    private static void guardBegin() {
        originalGeneric = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING); originalIndexed = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7);
        originalOffset = originalIndexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 7);
        originalSize = originalIndexed == 0 ? 0 : GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7);
        if (zeroBindingGuard) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, 0); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0); return;
        }
        if (guard == 0) {
            alignment = Math.max(16, GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT));
            guard = GL15.glGenBuffers(); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, guard); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, alignment * 8L, GL15.GL_STATIC_DRAW);
            genericGuard = GL15.glGenBuffers(); GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, genericGuard); GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 128, GL15.GL_STATIC_DRAW);
        }
        GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 7, guard, alignment, alignment * 4L);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, genericGuard);
    }
    private static void guardCheck() {
        if (zeroBindingGuard) {
            require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7) == 0 &&
                    GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) == 0, "No-material native draw did not restore an unbound SSBO7/generic binding");
            zeroRangeChecks++; return;
        }
        require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 7) == guard &&
                GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 7) == alignment &&
                GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 7) == alignment * 4L &&
                GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) == genericGuard, "Modern glint did not restore SSBO7 ID/range/generic binding");
        guardedDraws++;
    }
    private static void guardEnd() {
        if (originalIndexed != 0 && originalSize > 0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 7, originalIndexed, originalOffset, originalSize);
        else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, originalIndexed);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, originalGeneric);
    }

    public static void draw() {
        if (!enabled() || done || phase != 2) return;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); if (program == 0) return;
        int pass = GL20.glGetUniformLocation(program, "uGlintPass"), count = GL20.glGetUniformLocation(program, "uArcadiumModernGlintCount");
        if (pass < 0) return;
        if (GL20.glGetUniformi(program, pass) < 0) {
            normalSeen = true;
            if (zeroBindingGuard && count >= 0) {
                int binding=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,7),owned=ownedDummy();
                long start=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,7),range=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,7),store=bufferStore(binding);
                int block=GL43.glGetProgramResourceIndex(program,GL43.GL_SHADER_STORAGE_BLOCK,"ArcadiumModernGlintMatrices");int[] layout={-1,-1};
                if(block!=GL43.GL_INVALID_INDEX)GL43.glGetProgramResourceiv(program,GL43.GL_SHADER_STORAGE_BLOCK,block,new int[]{GL43.GL_BUFFER_BINDING,GL43.GL_BUFFER_DATA_SIZE},(int[])null,layout);
                if (GL20.glGetUniformi(program, count) != 0 ||layout[0]!=7||!registeredVerified(program)||!validDummyExtent(owned,binding,start,range,store,layout[1])) {
                    report.put("noMaterialBindingFailure", materialProgramState(program, count));
                    failure = "Globally patched no-material native shader has no valid dummy SSBO7; exact state is recorded";
                    throw new IllegalStateException(failure);
                }
                minimumNoMaterialStore=Math.min(minimumNoMaterialStore,store);if(range==0)wholeStoreChecks++;
                zeroMaterialDrawChecks++;
            }
            return;
        }
        if (count < 0 || GL20.glGetUniformi(program, count) == 0) return;
        require(GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, "ArcadiumModernGlintMatrices") != GL43.GL_INVALID_INDEX,
                "Modern glint draw has no linked material matrix block");
        verifiedPrograms.add(program);
        if (!normalSeen || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != GL11.GL_EQUAL || GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ||
                !GL11.glIsEnabled(GL11.GL_BLEND) || GL11.glGetInteger(GL11.GL_BLEND_SRC) != GL11.GL_SRC_COLOR || GL11.glGetInteger(GL11.GL_BLEND_DST) != GL11.GL_ONE)
            failure = "Modern GLINT did not retain normal-before-glint/depth/blend state";
        orderChecks++;
    }

    private static Map<String,Object> materialProgramState(int program,int count) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("program",program);result.put("countLocation",count);result.put("countValue",GL20.glGetUniformi(program,count));
        int binding=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,7);
        result.put("indexedBinding7",binding);result.put("indexedStart7",binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,7));result.put("indexedSize7",binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,7));
        result.put("genericBinding",GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING));
        result.put("actualBufferStoreBytes",bufferStore(binding));
        int block=GL43.glGetProgramResourceIndex(program,GL43.GL_SHADER_STORAGE_BLOCK,"ArcadiumModernGlintMatrices");result.put("modernStorageBlock",block);
        if(block!=GL43.GL_INVALID_INDEX){int[] layout=new int[2];GL43.glGetProgramResourceiv(program,GL43.GL_SHADER_STORAGE_BLOCK,block,new int[]{GL43.GL_BUFFER_BINDING,GL43.GL_BUFFER_DATA_SIZE},(int[])null,layout);result.put("blockBinding",layout[0]);result.put("blockMinimumBytes",layout[1]);}
        int item=GL20.glGetUniformLocation(program,"uItemGlintPass"),alpha=GL20.glGetUniformLocation(program,"uAlphaPass");
        result.put("nativeItemGlintPass",item<0?-1:GL20.glGetUniformi(program,item));result.put("nativeAlphaPass",alpha<0?-1:GL20.glGetUniformi(program,alpha));result.put("attachedShaderCount",GL20.glGetProgrami(program,GL20.GL_ATTACHED_SHADERS));
        try{
            Class<?> type=ModernArmorGlintPipeline.class;
            for(String name:List.of("programCount","drawDepth","dummyStorage","storage","scopeDepth")){Field field=type.getDeclaredField(name);field.setAccessible(true);result.put(name,field.get(null));}
            Field handles=type.getDeclaredField("programHandles"),locations=type.getDeclaredField("countLocations"),verified=type.getDeclaredField("verifiedPrograms");handles.setAccessible(true);locations.setAccessible(true);verified.setAccessible(true);
            int[] ids=(int[])handles.get(null),uniforms=(int[])locations.get(null);boolean[] good=(boolean[])verified.get(null);int registered=((Number)result.get("programCount")).intValue();
            List<Object> records=new java.util.ArrayList<>();for(int i=0;i<registered;i++)records.add(Map.of("program",ids[i],"location",uniforms[i],"verified",good[i],"current",ids[i]==program));result.put("registeredPrograms",records);
        }catch(ReflectiveOperationException unavailable){result.put("pipelineInspection",unavailable.toString());}
        return result;
    }
    private static boolean validDummyExtent(int owned,int binding,long start,long range,long store,int block){
        if(owned<=0||binding!=owned||start<0||range<0||store<64||block!=64)return false;
        // BindBufferBase's zero indexed size denotes the whole data store (GL 4.3, 6.7.1).
        if(range==0)return start==0;
        return range>=64&&start<=store&&range<=store-start;
    }
    private static int ownedDummy(){try{Field field=ModernArmorGlintPipeline.class.getDeclaredField("dummyStorage");field.setAccessible(true);return field.getInt(null);}catch(ReflectiveOperationException unavailable){return 0;}}
    private static boolean registeredVerified(int program){try{Class<?> type=ModernArmorGlintPipeline.class;Field ids=type.getDeclaredField("programHandles"),valid=type.getDeclaredField("verifiedPrograms"),count=type.getDeclaredField("programCount");ids.setAccessible(true);valid.setAccessible(true);count.setAccessible(true);int[] values=(int[])ids.get(null);boolean[] checks=(boolean[])valid.get(null);for(int i=0;i<count.getInt(null);i++)if(values[i]==program)return checks[i];}catch(ReflectiveOperationException unavailable){}return false;}
    private static long bufferStore(int buffer){
        if(buffer<=0||!GL15.glIsBuffer(buffer))return 0;
        int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);return GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE);}
        finally{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);}
    }

    public static long clock(long original, double speed) { return enabled() ? (long)(1234567L * speed * 8.0) : original; }

    public static void textureMatrix() {
        if (!enabled() || done || phase != 2 || comparisonMode == 0 || style >= 2) return;
        try {
            if (!ModernArmorGlintPipeline.active() && comparisonMode == 1) {
                GL11.glGetFloatv(GL11.GL_TEXTURE_MATRIX, nativeTextureMatrix); nativeTextureReady = true;
            } else if (ModernArmorGlintPipeline.active() && comparisonMode == 2 && nativeTextureReady) {
                if (currentCapture == null) {
                    Class<?> capture = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
                    currentCapture = capture.getDeclaredMethod("current"); currentCapture.setAccessible(true);
                    capturedTextureMatrix = capture.getDeclaredField("textureMatrices"); capturedTextureMatrix.setAccessible(true);
                }
                org.joml.Matrix4fc matrix = (org.joml.Matrix4fc)capturedTextureMatrix.get(currentCapture.invoke(null));
                for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
                    maximumTextureError = Math.max(maximumTextureError, Math.abs(matrix.get(column, row) - nativeTextureMatrix[column * 4 + row]));
                textureChecks++;
            }
        } catch (Throwable error) { failure = error.toString(); }
    }

    public static void frame() {
        if (done) return;
        if (failure != null) finish(new IllegalStateException(failure));
        else if (phase == 3) {
            if (!auxiliaryLifecycleDone) return;
            try {
                require(translationChecks>0&&nativePlayerEntries>0&&matrixBufferChecks==translationChecks*16,"Missing actual native height-shift matrix equivalence");
                var mvpProof = io.github.arcadiumclient.runtime.client.optimizations.ModernArmorMvp072Qa.report();
                require(Boolean.TRUE.equals(mvpProof.get("balancedClientScopes")), "Private MVP client scopes are unbalanced");
                if (ModernArmorGlintPipeline.mvpMode() == 1) require(((Number)mvpProof.get("actualNativeMvpRawBitChecks")).longValue() > 0 && ((Number)mvpProof.get("actualOwnedMvpInstances")).longValue() > 0, "Private MVP vertex path never executed");
                else require(((Number)mvpProof.get("mode0ControlInstances")).longValue() > 0, "Private mode0 control vertex path never executed");
                var nativeRootProof=nativeLayerRootProof();report.put("nativeLayerRoot",nativeRootProof);
                if(Boolean.getBoolean("arcadium.nativeLayerRoot072.replay"))require(Boolean.TRUE.equals(nativeRootProof.get("enabled"))&&((Number)nativeRootProof.get("actualReplays")).longValue()>0,"Requested native physical layer-root path did not actually execute");
                var inversion=publicInversionProof();report.put("actualPublicInversionInputs",inversion);
                if(CONTROLLED_INVERSION){
                    require(Boolean.TRUE.equals(inversion.get("inputsValid"))&&Boolean.TRUE.equals(inversion.get("sessionUnaffected")),"Actual public inversion controls/session changed");
                    var sample=(Map<?,?>)mvpProof.get("firstNativeEntryComparison");require(sample!=null,"No paired native entry for public inversion control");float nativeY=((float[])sample.get("nativeRoot"))[5],gpuY=((float[])sample.get("gpuPhysicalRoot"))[5];boolean expected=(boolean)inversion.get("expectedSingleInversion");
                    require((nativeY>0)==expected&&(gpuY>0)==expected,"Actual native/GPU vertical basis did not match one requested public inversion");report.put("actualInversionBasis",Map.of("nativeY",nativeY,"gpuY",gpuY,"expectedSingleInversion",expected,"observedOddInversionParity",nativeY>0,"oneVsDoubleCheckedWithFullMvp",true));
                }
                require(pixelFailures.isEmpty(), "Modern glint full RGBA quality failed: " + pixelFailures);
                require(zeroRangeChecks >= 20 && zeroMaterialDrawChecks > 0 && ModernArmorGlintPipeline.dummyProgramBinds > 0,
                        "No proof of valid no-material draws with an originally unbound SSBO7");
                require(guardedDraws > 100 && orderChecks > 0 && textureChecks > 0 && maximumTextureError < 1e-6, "Missing range/order/texture matrix correctness evidence");
                report.put("nonzeroSsbo7RangeChecks", guardedDraws); report.put("glintOrderingChecks", orderChecks); finish(null);
            } catch (Throwable error) { finish(error); }
        }
    }

    private static Map<String,Object> publicInversionProof(){
        try{
            String name=actor.getGameProfile().getName();boolean cape=actor.isModelPartVisible(net.minecraft.client.render.model.PlayerModelPart.CAPE),players=dinnerbonePlayersField.getBoolean(movement),entities=dinnerboneEntitiesField.getBoolean(movement);
            boolean valid=name.equals(ACTOR_NAME)&&(CAPE.equals("configured")||cape==CAPE.equals("on"))&&(FORCE_DINNERBONE.equals("configured")||players==FORCE_DINNERBONE.equals("on")&&!entities);
            boolean nativeName=(name.equals("Dinnerbone")||name.equals("Grumm"))&&cape;
            var result=new java.util.LinkedHashMap<String,Object>();result.put("profileName",name);result.put("capeRequested",CAPE);result.put("capeVisible",cape);result.put("skinFlagsByte",actor.getSyncedData().getByte(10));result.put("forcedRequested",FORCE_DINNERBONE);result.put("dinnerbonePlayers",players);result.put("dinnerboneEntities",entities);result.put("nativeNameInversion",nativeName);result.put("expectedSingleInversion",nativeName||players||entities);result.put("actualPublicApiOnly",true);result.put("privatePoseFieldsSet",false);result.put("inputsValid",valid);result.put("sessionUnaffected","ArcadiumTest".equals(Minecraft.getInstance().getSession().getUsername())&&"0".equals(Minecraft.getInstance().getSession().getAccessToken()));return result;
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }

    private static Map<String,Object> nativeLayerRootProof(){
        boolean requested=Boolean.getBoolean("arcadium.nativeLayerRoot072.replay");
        if(!requested)return Map.of("requested",false,"enabled",false,"actualReplays",0);
        try{
            Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.NativePlayerLayerRoot");var enabled=type.getDeclaredMethod("enabled");enabled.setAccessible(true);var replays=type.getDeclaredField("replays");replays.setAccessible(true);
            return Map.of("requested",true,"enabled",enabled.invoke(null),"actualReplays",replays.getLong(null));
        }catch(ReflectiveOperationException error){return Map.of("requested",true,"enabled",false,"actualReplays",0,"missingApiClass",error.getClass().getName());}
    }

    private static BufferedImage capture(Minecraft mc) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING); IntBuffer pixels = BufferUtils.createIntBuffer(mc.width * mc.height);
        try { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getRenderTarget().frameBufferId); GL11.glReadPixels(0, 0, mc.width, mc.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
        BufferedImage image = new BufferedImage(mc.width, mc.height, BufferedImage.TYPE_INT_ARGB); int[] row = new int[mc.width];
        for (int y = 0; y < mc.height; y++) { pixels.position((mc.height - 1 - y) * mc.width); pixels.get(row); image.setRGB(0, y, mc.width, 1, row, 0, mc.width); }
        return image;
    }
    private static void finish(Throwable error) {
        if (done) return; done = true; report.put("passed", error == null);
        report.put("nativePlayerRendererEntries",nativePlayerEntries);report.put("nativeLayerAttempts",nativeLayerAttempts);report.put("nativeLayerType",nativeLayerType);
        report.put("nativeHeightShiftChecks",translationChecks);report.put("nativeHeightShiftMatrixWords",matrixBufferChecks);
        report.put("nonzeroSsbo7RangeChecks", guardedDraws); report.put("glintOrderingChecks", orderChecks);
        report.put("verifiedProgramHandles", verifiedPrograms); report.put("vertexSourcePinMatched", ModernGlintShaderPatch.vertexPatched);
        report.put("fragmentSourcePinMatched", ModernGlintShaderPatch.fragmentPatched); report.put("nativeTextureMatrixChecks", textureChecks);
        report.put("maximumNativeTextureMatrixError", maximumTextureError); report.put("finalOpenGlError", GL11.glGetError());
        report.put("actualNativeModelRootEntries",nativeRootEntries);report.put("actualPairedNativeRootDraws",nativePairedRootDraws);report.put("nativeEntryPairing","HEAD pending committed only by same-part actual callList, scoped to frame and reference generation");
        report.put("actualFixedFunctionModelViewChecks", modelViewChecks); report.put("maximumFixedFunctionModelViewError", maximumModelViewError);
        report.put("maximumCurrentDriverRootModelViewError", maximumRootModelViewError);
        report.put("fullRgbaPixelFailures", pixelFailures);
        report.put("fixedFunctionAndInstancedGlintPixelTrace", ModernGlintPixelTrace072Qa.report());
        report.put("privateMvpState", io.github.arcadiumclient.runtime.client.optimizations.ModernArmorMvp072Qa.report());
        report.put("nativeLayerRoot",nativeLayerRootProof());
        if(actor!=null)report.put("actualPublicInversionInputs",publicInversionProof());
        report.put("originallyUnboundSsbo7WorldChecks", zeroRangeChecks); report.put("zeroMaterialNativeDrawChecks", zeroMaterialDrawChecks);
        report.put("baseWholeStoreBindingChecks",wholeStoreChecks);report.put("minimumNoMaterialDatastoreBytes",minimumNoMaterialStore==Long.MAX_VALUE?0:minimumNoMaterialStore);
        report.put("dummyProgramBinds", ModernArmorGlintPipeline.dummyProgramBinds); report.put("materialProgramBinds", ModernArmorGlintPipeline.materialProgramBinds);
        report.put("originalVertexArmorParts", io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline.originalArmorPartsQueued);
        if (error != null) { report.put("error", error.toString()); report.put("diagnostic", ModernArmorGlintPipeline.status()); error.printStackTrace(); }
        try {
            if (styleField != null) styleField.set(items, originalStyle);
            if(sneakAnimationField!=null)sneakAnimationField.set(movement,originalSneakAnimation);
            if(dinnerbonePlayersField!=null){dinnerbonePlayersField.setBoolean(movement,originalDinnerbonePlayers);dinnerboneEntitiesField.setBoolean(movement,originalDinnerboneEntities);}
            if(actor!=null&&skinFlagsCaptured)actor.getSyncedData().update(10,originalActorSkinFlags);
            boolean controlsRestored=(dinnerbonePlayersField==null||dinnerbonePlayersField.getBoolean(movement)==originalDinnerbonePlayers&&dinnerboneEntitiesField.getBoolean(movement)==originalDinnerboneEntities)&&(actor==null||!skinFlagsCaptured||actor.getSyncedData().getByte(10)==originalActorSkinFlags);report.put("publicInversionControlsRestored",controlsRestored);if(!controlsRestored){report.put("passed",false);report.put("restoreError","Actual public config or SyncedData flags were not restored");}
            Path output = Path.of(System.getProperty("arcadium.modernGlint072.output")); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (guard != 0) GL15.glDeleteBuffers(guard); if (genericGuard != 0) GL15.glDeleteBuffers(genericGuard);
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(Minecraft minecraft, GameProfile profile) { super(minecraft.world, profile); ticks = 160; }
        @Override public void tick() { ticks = 160; }
    }
}
