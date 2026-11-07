// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunksAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072FogAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072WorldAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkStateAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkMapAccess;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class PlayerPipeline072Qa {
    private static final String SCENE = System.getProperty("arcadium.playerPipeline072.scene", "default");
    private static final boolean VISUAL = System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual");
    private static final boolean BASELINE = Boolean.getBoolean("arcadium.playerPipeline072.baseline");
    private static final boolean PROFILING = Boolean.getBoolean("arcadium.playerPipeline072.profiling");
    private static final boolean ACTOR_SCENE_PERFORMANCE=Boolean.getBoolean("arcadium.actorScene073.performance");
    private static final boolean QUIET_WINDOW=Boolean.getBoolean("arcadium.performance073.quietWindow");
    private static QuietPerformanceWindow073 quietWindow;
    private static final String RESIDENT_BODY_MODE=System.getProperty("arcadium.residentBodies072.mode","off");
    private static final boolean RESIDENT_BODY_PERFORMANCE=Boolean.getBoolean("arcadium.residentBodies072.performanceQualified");
    private static final boolean RESIDENT_BODY_SHARED=Boolean.getBoolean("arcadium.residentBodies072.sharedPalette");
    private static final boolean ALL_ON_BETA=Boolean.getBoolean("arcadium.playerPipeline072.allOnBetaProfile");
    private static final boolean NATIVE_BODY_CONTROL = Boolean.getBoolean("arcadium.playerPipeline072.nativeBodyControl");
    private static final boolean NATIVE_VOXEL_ROOT_CONTROL=Boolean.getBoolean("arcadium.playerPipeline072.nativeVoxelRootControl");
    private static final int PLAYERS = Integer.getInteger("arcadium.playerPipeline072.players", 20);
    private static final int WARMUP = Integer.getInteger("arcadium.playerPipeline072.warmup", 25);
    private static final int SECONDS = Integer.getInteger("arcadium.playerPipeline072.seconds", 30);
    private static final boolean SKIN = SCENE.equals("3d-skin") || SCENE.equals("skin+wavy");
    private static final boolean WAVY = SCENE.equals("wavy") || SCENE.equals("skin+wavy");
    private static final String VOXEL_ADMISSION = System.getProperty("arcadium.voxelQuality072.expectedAdmission", "gpu");
    private static final Set<String> VOXEL_ADMISSIONS = Set.of("gpu", "binary-fallback", "fractional-fallback", "dimension-fallback", "pending-fallback");
    private static final long[] frames = new long[524288];
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Object> requested = new TreeMap<>();
    private static final Map<String, Object> requestedPresence = new TreeMap<>();
    private static final List<Object> images = new ArrayList<>();
    private static final List<Actor> actors = new ArrayList<>();
    private static int ticks, phase, phaseTicks, sampleCount, visualStage;
    private static long settled, started, last;
    private static long phaseHybridStart;
    private static long phaseCompactStart, phaseNativeStart, phaseNativeSkinStart,phaseResidentActors,phaseResidentDraws,phaseResidentDummy;
    private static int phaseVoxelStart;
    private static long phaseAnalyticVoxelStart, phaseNativeVoxelStart, phaseVoxelDrawStart, phaseVoxelVerticesStart;
    private static long phaseAdmissionStart;
    private static Object world;
    private static boolean done, pendingImage, effectiveSkin;
    private static Map<String, Object> measuredSettings;
    private static Future<?> environmentSetup;
    private static long worldReadySince, stageStarted, lastPixelSample, stablePixelsSince;
    private static long stablePixelHash = -1;
    private static int stablePixelSamples;
    private static ByteBuffer visualPixels;
    private static final Map<Actor, RenderTrace> renderTraces = new IdentityHashMap<>();
    private static Map<String, Object> canonicalDataset;
    private static String inventorySignature;
    private static long inventoryStableSince;
    private static long lastReadinessLog;
    private static Future<?> serverDiagnostics;
    private static long lastServerDiagnostic;
    private static Map<String, Object> serverTracking;
    private static int viewWarmupStage;
    private static final List<Map<String, Object>> viewWarmupTransitions = new ArrayList<>();

    private PlayerPipeline072Qa() {}
    public static boolean enabled() { return System.getProperty("arcadium.playerPipeline072.output") != null; }
    public static boolean timingActive(){return enabled()&&!done&&phase==3;}
    public static float delta(float original) { return enabled() && VISUAL ? .5f : original; }
    public static boolean sceneReady() { return enabled() && !done && phase >= 2 && world != null && Minecraft.getInstance().world == world; }

    public static void tick() {
        if (!enabled() || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ActorPacket072Qa.check();
            require(Set.of("off","control","active").contains(RESIDENT_BODY_MODE)&&(RESIDENT_BODY_MODE.equals("off")||!BASELINE&&(VISUAL||RESIDENT_BODY_PERFORMANCE)),"Private resident bodies require an explicit optimized visual or certified fixture");
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use only the owned offline fixture");
            require(List.of("default", "3d-skin", "wavy", "skin+wavy").contains(SCENE), "Unknown scene");
            require(Set.of(3,20,50).contains(PLAYERS)||ACTOR_SCENE_PERFORMANCE&&!VISUAL&&!BASELINE&&!PROFILING&&SCENE.equals("default")&&PLAYERS==64,"Unsupported actor count or uncertified64-player fixture");
            require(VOXEL_ADMISSIONS.contains(VOXEL_ADMISSION) && (VOXEL_ADMISSION.equals("gpu") || VISUAL && SKIN), "Admission fallback checks require an explicit visual skin fixture");
            require(!NATIVE_BODY_CONTROL || VISUAL && !BASELINE && SKIN, "Native body control requires an explicit optimized visual skin fixture");
            require(!NATIVE_VOXEL_ROOT_CONTROL||VISUAL&&!BASELINE&&SKIN&&!NATIVE_BODY_CONTROL&&!PROFILING&&VOXEL_ADMISSION.equals("gpu")
                    &&!Boolean.getBoolean("arcadium.voxelQuality072.captureOnly")&&!Boolean.getBoolean("arcadium.actorPacket072.proof")
                    &&!Boolean.getBoolean("arcadium.nameReset072.proof")&&!Boolean.getBoolean("arcadium.playerPipeline072.reloadProbe")
                    &&!Boolean.getBoolean("arcadium.voxelQuality072.pendingPart"),"Native voxel root control requires its exclusive optimized visual GPU skin fixture");
            require(SECONDS >= 5 && SECONDS <= 120 && WARMUP >= 5, "Invalid capture duration");
            require(++ticks < 12000, "Player fixture timed out at phase " + phase); phaseTicks++;
            if (phase == 0 && ticks > 65 && mc.screen != null) {
                configure(mc);
                mc.startGame("arcadium-player-pipeline072", "Player pipeline QA", new WorldSettings(72072, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; phaseTicks = 0;
                settled = System.nanoTime();
            } else if (phase == 1 && mc.world != null && mc.player != null && phaseTicks > 60 && prepareWorld(mc)) {
                mc.openScreen(null); mc.player.abilities.flying = true;
                mc.player.updatePositionAndAngles(0, 6, -10, 0, 8);
                mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
                mc.player.lastYaw = mc.player.yaw; mc.player.lastPitch = mc.player.pitch;
                mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0;
                Features062Qa.seedSkin(mc); configureCase(SKIN, WAVY);
                if (!BASELINE) {
                    Class<?> helper = Class.forName("io.github.arcadiumclient.runtime.qa.AnimationPose072Qa");
                    report.put("nativePoseValidation", helper.getMethod("verify", Minecraft.class).invoke(null, mc));
                } else report.put("nativePoseValidation", Map.of("run", false, "reason", "Frozen 0.7.1 baseline"));
                int columns = PLAYERS == 3 ? 3 : PLAYERS == 20 ? 5 : PLAYERS==64&&ACTOR_SCENE_PERFORMANCE ? 8 : 10;
                for (int i = 0; i < PLAYERS; i++) {
                    Actor actor = new Actor(mc.world, new GameProfile(new UUID(0x7207200000004000L + i, 0x8000000000000072L), "PipelineQA" + i), i);
                    actor.updatePositionAndAngles((i % columns - (columns - 1) * .5) * 1.45, 4, 2 + i / columns * 1.7, 180 + i % 13, 10 + i % 9);
                    actor.lastX = actor.prevX = actor.x; actor.lastY = actor.prevY = actor.y; actor.lastZ = actor.prevZ = actor.z;
                    actor.lastYaw = actor.yaw; actor.lastPitch = actor.pitch;
                    actor.bodyYaw = actor.lastBodyYaw = actor.yaw; actor.headYaw = actor.lastHeadYaw = actor.yaw + 10;
                    actor.walkAnimationProgress = 2 + i * .1f; actor.walkAnimationSpeed = actor.lastWalkAnimationSpeed = .6f;
                    actor.ticks = 100 + i % 20;
                    actor.getSyncedData().update(10, (byte) 0x7f);
                    for (PlayerModelPart part : PlayerModelPart.values()) require(actor.isModelPartVisible(part), "An outer skin part is hidden: " + part);
                    mc.world.forceEntity(72000 + i, actor); actors.add(actor);
                }
                Features062Qa.voxelDraws = Features062Qa.meshCompiles = 0;
                world = mc.world; settled = System.nanoTime(); phase = 2; phaseTicks = 0;
                report.put("fixture", Map.of("scene", SCENE, "players", PLAYERS, "mode", VISUAL ? "fixed-delta-visual" : "dynamic-performance", "baseline", BASELINE,
                        "requestedSeconds", SECONDS, "warmupSeconds", WARMUP, "fixedVisualDelta", .5, "performanceUsesDynamicInterpolation", !VISUAL,
                        "outerSkinPartMask", 127, "forgeEnvironment", "not-covered"));
                status("warming");
            }
            if (phase >= 2) {
                require(mc.world == world && mc.screen == null, "The player scene changed");
                mc.world.setTimeOfDay(6000);
                mc.player.yaw = mc.player.lastYaw = 0; mc.player.pitch = mc.player.lastPitch = 8;
                if (VISUAL) mc.player.ticks = 100;
                validatePresence(mc);
                require(mc.width == 1920 && mc.height == 1080 && Display.getWidth() == 1920 && Display.getHeight() == 1080, "The fixture requires a real 1920x1080 framebuffer/window");
                Map<String, Object> warmTerrain = phase == 2 ? terrainState(mc) : null;
                if (phase == 2 && System.nanoTime() - settled >= WARMUP * 1_000_000_000L && Boolean.TRUE.equals(warmTerrain.get("ready")) && stableInventory(warmTerrain)) {
                    if (!Display.isActive()) { status("waiting_for_focus"); return; }
                    if(QUIET_WINDOW){
                        require(ACTOR_SCENE_PERFORMANCE&&!VISUAL&&!PROFILING,"Quiet windows require the owned unprofiled actor performance fixture");
                        if(quietWindow==null)quietWindow=new QuietPerformanceWindow073(output().toAbsolutePath().getParent(),System.getProperty("arcadium.performance073.caseId",""),System.getProperty("arcadium.performance073.nonce",""),ProcessHandle.current().pid());
                        if(!quietWindow.awaitRelease()){status("awaiting_root_quiet_release");return;}
                    }
                    report.put("environment", Map.of("gpu", GL11.glGetString(GL11.GL_RENDERER), "openGl", GL11.glGetString(GL11.GL_VERSION),
                            "java", System.getProperty("java.runtime.version"), "cpuThreads", Runtime.getRuntime().availableProcessors(), "width", mc.width, "height", mc.height));
                    measuredSettings = settings(mc); report.put("settingsBefore", measuredSettings); report.put("countersBefore", counters());
                    if (!VISUAL) matchDataset(warmTerrain);
                    report.put("requestedBeta", requested); report.put("requestedBetaPresence", requestedPresence);
                    if(ACTOR_SCENE_PERFORMANCE){
                        armCounters();status("recording");started=QUIET_WINDOW?quietWindow.start():System.nanoTime();last=0;phase=3;phaseTicks=0;pendingImage=false;
                    }else{
                        started = System.nanoTime(); last = 0; phase = VISUAL ? 4 : 3; phaseTicks = 0; pendingImage = VISUAL;
                        armCounters();status(VISUAL ? "visual" : "recording");
                    }
                }
                if (phase >= 3) require(Display.isActive(), "The player fixture lost foreground focus");
                if (phase == 4 && !pendingImage && phaseTicks > 12) {
                    if (visualStage == 1) {
                        mc.reloadResources(); Features062Qa.seedSkin(mc); report.put("resourceReload", true); pendingImage = true;
                    } else if (visualStage == 2) {
                        configureCase(!SKIN, !WAVY); report.put("settingsChanged", true); pendingImage = true;
                    } else if (visualStage == 3) {
                        configureCase(SKIN, WAVY); report.put("settingsRestored", true); pendingImage = true;
                    } else if (visualStage == 4) {
                        require(measuredSettings.equals(settings(mc)), "Restoring settings changed the original scene configuration");
                        report.put("images", images); report.put("countersAfter", counters()); finish(null);
                    }
                    phaseTicks = 0;
                    armCounters();
                }
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void configure(Minecraft mc) throws Exception {
        ClientPreferences.setSetupComplete(true); OneConfigConfig.limitUnfocusedFps = false;
        Skin3DConfig.INSTANCE.chooseEnabled(false);
        Skin3DConfig.setHead(true); Skin3DConfig.setBody(true); Skin3DConfig.setLeftArm(true); Skin3DConfig.setRightArm(true);
        Skin3DConfig.setLeftLeg(true); Skin3DConfig.setRightLeg(true); Skin3DConfig.setDistance(24);
        Skin3DConfig.setAdaptiveDetail(true); Skin3DConfig.setDetailedPlayers(12);
        OneConfigConfig.useVanillaFontInGame = true; OneConfigConfig.useVanillaFontForModText = true;
        OneConfigConfig.useInterInGame = false; OneConfigConfig.useInterForModText = false;
        mc.options.vsync = false; Display.setVSyncEnabled(false); mc.options.fpsLimit = 260;
        mc.options.viewDistance = 8; mc.options.fov = 75; mc.options.pauseOnUnfocus = false; mc.options.cloudRenderMode = 0; mc.options.perspective = 0;
        mc.options.difficulty = Difficulty.PEACEFUL;
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (String key : List.of("gpuModelTransforms", "gpuPlayerPipeline", "gpuEquipmentPipeline", "cachedNameMasking", "cachedFontMetrics", "batchedVanillaFont")) defaults.put(key, true);
        for (String key : List.of("gpuNameTags", "retainedNameTagScenes", "compactNameTagPreparation", "gpuParticles", "retainedParticleRecipes", "fastInvisibleHolograms",
                "retainedHudBatches", "retainedScoreboardPreparation", "directFramebufferPresentation", "efficientMotionBlur", "reduceRenderHookAllocations", "worldRenderScale", "gpuTerrainCommands", "gpuVoxelSkinLayers")) defaults.put(key, false);
        defaults.put("minimumPlayers", 1); defaults.put("minimumGpuLabels", 1); defaults.put("minimumGpuParticles", 1); defaults.put("worldResolution", 100);
        for (var entry : BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet()) {
            if (entry.getValue() instanceof Property<?> property && property.get() instanceof Boolean && !defaults.containsKey(entry.getKey())) {
                defaults.put(entry.getKey(), false);
            }
        }
        String file = System.getProperty("arcadium.playerPipeline072.settings");
        if (file != null) {
            Map<?, ?> supplied = new Gson().fromJson(Files.readString(Path.of(file)), Map.class);
            Object beta = supplied.containsKey("beta") ? supplied.get("beta") : supplied;
            require(beta instanceof Map<?, ?>, "Settings must contain a Beta object");
            for (var entry : ((Map<?, ?>) beta).entrySet()) defaults.put(entry.getKey().toString(), entry.getValue());
            if (supplied.get("skin") instanceof Map<?, ?> skin) setTree(Skin3DConfig.INSTANCE.getTree(), skin);
        }
        if(ALL_ON_BETA){
            require(!BASELINE&&file!=null,"The all-on profile requires explicit optimized settings");int booleanCount=0;
            for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property&&property.get() instanceof Boolean){
                booleanCount++;require(Boolean.valueOf(!entry.getKey().equals("worldRenderScale")).equals(defaults.get(entry.getKey())),"The combined profile must use actual boolean defaults: "+entry.getKey());
            }
            require(booleanCount==21,"The all-on profile schema changed; audit its settings");
            for(String key:List.of("minimumPlayers","minimumGpuLabels","minimumGpuParticles","worldResolution"))require(defaults.get(key) instanceof Number number&&number.doubleValue()==(key.equals("worldResolution")?75:1),"The combined profile changed a numeric default: "+key);
        }else require(Boolean.FALSE.equals(defaults.get("gpuTerrainCommands")), "Terrain commands require a separate terrain comparison");
        require(Boolean.FALSE.equals(defaults.get("worldRenderScale")), "Actor comparisons must use full world resolution");
        String dataset = System.getProperty("arcadium.playerPipeline072.dataset");
        if (dataset != null) canonicalDataset = new Gson().fromJson(Files.readString(Path.of(dataset)), Map.class);
        Set<String> optional = new HashSet<>(Arrays.asList(System.getProperty("arcadium.playerPipeline072.optionalBeta", "gpuTerrainCommands,gpuVoxelSkinLayers").split(",")));
        optional.add("gpuTerrainCommands"); optional.add("gpuVoxelSkinLayers");
        for (var entry : defaults.entrySet()) {
            Property<?> property = BetaOptimizationsConfig.INSTANCE.getTree().getProp(entry.getKey());
            requested.put(entry.getKey(), entry.getValue());
            if (property == null) {
                require(optional.contains(entry.getKey()), "Requested Beta setting is absent: " + entry.getKey());
                requestedPresence.put(entry.getKey(), Map.of("present", false)); continue;
            }
            property.setAs(convert(entry.getValue(), property.get()));
            requestedPresence.put(entry.getKey(), Map.of("present", true, "actual", normalize(property.get())));
        }
    }

    private static void configureCase(boolean skin, boolean wavy) throws Exception {
        effectiveSkin = skin;
        Skin3DConfig.INSTANCE.chooseEnabled(skin);
        Class<?> animations = Class.forName("io.github.arcadiumclient.runtime.client.features.AnimationsConfig");
        animations.getMethod("setEnabled", boolean.class).invoke(null, true); animations.getMethod("setMode", int.class).invoke(null, 0);
        Class<?> type = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
        Object config = type.getMethod("instance").invoke(null), movement = type.getField("movement").get(config);
        movement.getClass().getField("wavyArms").setBoolean(movement, wavy);
        Class.forName("io.github.arcadiumclient.runtime.client.optimizations.PlayerPoseOptions").getMethod("refresh").invoke(null);
    }

    public static void worldRendered() {
        if (!enabled() || done || phase != 4 || !pendingImage || phaseTicks < 8) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(Display.isActive(), "The visual fixture lost foreground focus");
            validatePresence(mc);
            Map<String, Object> terrain = terrainState(mc);
            if (System.nanoTime() - stageStarted > 60_000_000_000L) {
                report.put("unstableTerrain", terrain); report.put("unstableWorld", worldState(mc));
                report.put("unstableFogAndSky", fogState(mc));
                throw new IllegalStateException("The fixed-delta world did not reach stable complete terrain");
            }
            if (!Boolean.TRUE.equals(terrain.get("ready")) || !stableInventory(terrain) || System.nanoTime() - stageStarted < 2_000_000_000L) {
                stablePixelsSince = 0; stablePixelSamples = 0; return;
            }
            long now = System.nanoTime();
            if (now - lastPixelSample < 250_000_000L) return;
            lastPixelSample = now;
            readPixels(mc);
            CRC32 checksum = new CRC32(); checksum.update(visualPixels.duplicate()); long hash = checksum.getValue();
            if (hash != stablePixelHash || stablePixelsSince == 0) {
                stablePixelHash = hash; stablePixelsSince = now; stablePixelSamples = 1; return;
            }
            stablePixelSamples++;
            if (now - stablePixelsSince < 1_000_000_000L || stablePixelSamples < 5) return;
            matchDataset(terrain);
            requireVoxelPath();
            requireBodyPath();
            String name = List.of("base", "resource-reloaded", "settings-changed", "settings-restored").get(visualStage);
            String filename = SCENE + "-" + PLAYERS + "p-" + name + ".png";
            capture(mc, filename);
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("file", filename); image.put("settings", settings(mc)); image.put("counters", counters());
            image.put("nativeVoxelDraws", Features062Qa.voxelDraws - phaseVoxelStart); image.put("compiledVoxelMeshes", Features062Qa.meshCompiles);
            image.put("fixedDelta", .5); image.put("world", worldState(mc)); image.put("actorsRendered", actorRenderState());
            image.put("terrain", terrain); image.put("stableFullFrameCrc32", hash); image.put("stableSamples", stablePixelSamples);
            image.put("fogAndSky", fogState(mc));
            image.put("voxelProof", voxelEvidence());
            if(ActorPacket072Qa.enabled())image.put("residentActorPacketProof",ActorPacket072Qa.requestSample());
            image.put("stableSeconds", (now - stablePixelsSince) / 1e9); images.add(image);
            report.put("images", images);
            for (Actor actor : actors) require(renderTraces.get(actor) != null && renderTraces.get(actor).calls > 0,
                    "An expected actor never reached PlayerRenderer in the stable frame: " + actor.getName());
            visualStage++; pendingImage = false; phaseTicks = 0;
        } catch (Throwable failure) { finish(failure); }
    }

    public static void frame() {
        if (!enabled() || done || phase != 3) return;
        try {
            require(Display.isActive(), "Focus was lost during performance capture");
            long now = System.nanoTime();
            if (last != 0) { require(sampleCount < frames.length, "Frame sample buffer is full"); frames[sampleCount++] = now - last; }
            last = now;
            if (now - started < SECONDS * 1_000_000_000L) return;
            if(ACTOR_SCENE_PERFORMANCE)phase=5;
            if(QUIET_WINDOW){quietWindow.end(now);report.put("quietWindow",quietWindow.report());}
            require(sampleCount > 100, "Too few performance frames");
            long[] sorted = Arrays.copyOf(frames, sampleCount); Arrays.sort(sorted);
            long total = 0, slow = 0; for (long time : sorted) total += time;
            int low = Math.max(1, (sampleCount + 99) / 100); for (int i = sampleCount - low; i < sampleCount; i++) slow += sorted[i];
            report.put(PROFILING ? "profilingTiming" : "performance", Map.of("frames", sampleCount, "includedSeconds", total / 1e9, "fps", 1e9 * sampleCount / total,
                    "onePercentLowFps", 1e9 * low / slow, "p99FrameMs", sorted[(int) Math.ceil(sampleCount * .99) - 1] / 1e6, "maximumFrameMs", sorted[sampleCount - 1] / 1e6));
            report.put("settingsAfter", settings(Minecraft.getInstance())); report.put("countersAfter", counters());
            Map<String, Object> finalTerrain = terrainState(Minecraft.getInstance()); matchDataset(finalTerrain); report.put("terrainAfterTiming", finalTerrain);
            report.put("timedNativeVoxelDraws", Features062Qa.voxelDraws - phaseVoxelStart);
            report.put("timedVoxelProof", voxelEvidence());
            require(measuredSettings.equals(report.get("settingsAfter")), "Settings changed during performance capture");
            requireVoxelPath();
            requireBodyPath();
            finish(null);
        } catch (Throwable failure) { finish(failure); }
    }

    private static Map<String, Object> settings(Minecraft mc) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beta", tree(BetaOptimizationsConfig.INSTANCE.getTree()));
        result.put("skin", tree(Skin3DConfig.INSTANCE.getTree()));
        Class<?> animations = Class.forName("io.github.arcadiumclient.runtime.client.features.AnimationsConfig");
        result.put("animations", tree(animations.getMethod("getTree").invoke(animations.getField("INSTANCE").get(null))));
        Class<?> overflow = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
        Object config = overflow.getMethod("instance").invoke(null);
        result.put("overflow", fields(config, 0));
        result.put("game", Map.of("renderDistance", mc.options.viewDistance, "fpsLimitRaw", mc.options.fpsLimit, "unlimitedFps", mc.options.fpsLimit == 260,
                "vsync", mc.options.vsync, "fov", mc.options.fov, "cloudRenderMode", mc.options.cloudRenderMode, "perspective", mc.options.perspective, "pauseOnUnfocus", mc.options.pauseOnUnfocus));
        result.put("fonts", Map.of("vanillaInGame", OneConfigConfig.useVanillaFontInGame, "vanillaForMods", OneConfigConfig.useVanillaFontForModText,
                "interInGame", OneConfigConfig.useInterInGame, "interForMods", OneConfigConfig.useInterForModText));
        result.put("unfocusedFpsEnabled", OneConfigConfig.limitUnfocusedFps);
        result.put("camera", Map.of("x", mc.player.x, "y", mc.player.y, "z", mc.player.z, "yaw", mc.player.yaw, "pitch", mc.player.pitch));
        result.put("gpuTimers", false); result.put("gpuReadbacksDuringTiming", false); result.put("fixedDeltaDuringTiming", false);
        result.put("profiling", PROFILING); result.put("notFinalPerformance", PROFILING||NATIVE_VOXEL_ROOT_CONTROL||!RESIDENT_BODY_MODE.equals("off")||RESIDENT_BODY_PERFORMANCE);
        result.put("nativeBodyControl", NATIVE_BODY_CONTROL);
        result.put("residentBodyMode",RESIDENT_BODY_MODE);
        result.put("residentBodySharedPalette",RESIDENT_BODY_SHARED);
        result.put("residentBodySelectedMaterial",Boolean.getBoolean("arcadium.residentBodies072.selectedMaterial"));
        result.put("allOnBetaProfile",ALL_ON_BETA);
        result.put("nativeVoxelRootControl",NATIVE_VOXEL_ROOT_CONTROL);
        return result;
    }

    private static Map<String, Object> tree(Object tree) throws Exception {
        Map<String, Object> values = new TreeMap<>();
        if (tree == null) { values.put("present", false); return values; }
        for (var entry : ((Map<?, ?>) tree.getClass().getField("map").get(tree)).entrySet()) {
            if (entry.getValue() instanceof Property<?> property) values.put(entry.getKey().toString(), normalize(property.get()));
        }
        return values;
    }
    private static void setTree(Object tree, Map<?, ?> values) throws Exception {
        require(tree != null, "Module tree is not initialized");
        for (var entry : values.entrySet()) {
            Object node = tree.getClass().getMethod("getProp", String.class).invoke(tree, entry.getKey().toString());
            require(node instanceof Property<?>, "Module property is absent: " + entry.getKey());
            Property<?> property = (Property<?>) node; property.setAs(convert(entry.getValue(), property.get()));
        }
    }
    private static Object convert(Object requested, Object current) {
        if (requested instanceof Number value) {
            if (current instanceof Integer) return value.intValue(); if (current instanceof Long) return value.longValue();
            if (current instanceof Float) return value.floatValue(); if (current instanceof Double) return value.doubleValue();
        }
        return requested;
    }
    private static Object normalize(Object value) {
        if (value == null) return "null";
        return value instanceof String || value instanceof Number || value instanceof Boolean ? value : value instanceof Enum<?> kind ? kind.name() : value.toString();
    }
    private static Map<String, Object> fields(Object object, int depth) throws Exception {
        Map<String, Object> values = new TreeMap<>();
        for (Field field : object.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value = field.get(object);
            values.put(field.getName(), depth < 2 && value != null && value.getClass().getName().startsWith("org.polyfrost.overflowanimations.config.") ? fields(value, depth + 1) : normalize(value));
        }
        return values;
    }
    private static Map<String,Object> nameScopeProof;
    private static Map<String,Object> nameScopeEvidence() {
        if(!Boolean.getBoolean("arcadium.nameScope072.proof"))return Map.of("enabled",false);
        if(nameScopeProof!=null)return nameScopeProof;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.world==null)return Map.of("enabled",true,"ready",false);
        net.minecraft.entity.Entity first=mc.player,second=null;
        for(var candidate:mc.world.entities)if(candidate!=first){second=candidate;break;}
        if(second==null)return Map.of("enabled",true,"ready",false);
        var type=io.github.arcadiumclient.runtime.client.branding.NametagLayout.class;
        boolean oldOptimized=io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized;
        Object savedFirst=null,savedSecond=null;java.lang.reflect.Method setter=null;
        try{
            var scope=io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope.class;
            var getter=first.getClass().getMethod("arcadium$nameScope");
            setter=first.getClass().getMethod("arcadium$nameScope",scope);
            savedFirst=getter.invoke(first);savedSecond=getter.invoke(second);
            var retain=type.getDeclaredMethod("retain",net.minecraft.entity.Entity.class,String.class,String.class,String.class,boolean.class);retain.setAccessible(true);
            boolean reuse=Boolean.parseBoolean(System.getProperty("arcadium.nameScope072.reuse","true"));
            var a=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,first,"Raw","Display","Visible",false);
            var stable=retain.invoke(null,first,new String("Raw"),new String("Display"),new String("Visible"),false);
            require(reuse?a==stable:a!=stable,"Name-scope reuse control did not take effect");
            var label=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,first,"Changed","Display","Visible",false);
            require(label!=a&&label.label().equals("Changed"),"Name-scope raw label was stale");
            var display=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,first,"Changed","Masked","Visible",false);
            require(display!=label&&display.displayLabel().equals("Masked"),"Name-scope mask was stale");
            var visible=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,first,"Changed","Masked","Plain",false);
            require(visible!=display&&visible.visibleLabel().equals("Plain"),"Name-scope visible label was stale");
            var badge=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,first,"Changed","Masked","Plain",true);
            require(badge!=visible&&badge.badge(),"Name-scope badge was stale");
            setter.invoke(second,badge);
            var owner=(io.github.arcadiumclient.runtime.client.branding.NametagLayout.Scope)retain.invoke(null,second,"Changed","Masked","Plain",true);
            require(owner!=badge&&owner.owner()==second,"Name-scope reused another entity's record");
            var nullable=retain.invoke(null,first,null,null,null,false);
            var nullableAgain=retain.invoke(null,first,null,null,null,false);
            require(reuse?nullable==nullableAgain:nullable!=nullableAgain,"Name-scope null-field reuse changed");
            io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=true;
            var previous=io.github.arcadiumclient.runtime.client.branding.NametagLayout.begin(first,"Arcadium QA first");
            try{
                var nested=io.github.arcadiumclient.runtime.client.branding.NametagLayout.begin(second,"Arcadium QA second");
                require(nested!=null&&nested.owner()==first,"Nested name-scope parent changed");
                io.github.arcadiumclient.runtime.client.branding.NametagLayout.end(nested);
                var same=io.github.arcadiumclient.runtime.client.branding.NametagLayout.begin(first,"Arcadium QA first");
                require(same==nested,"Same-owner nested name-scope shortcut changed");
                io.github.arcadiumclient.runtime.client.branding.NametagLayout.end(same);
            }finally{io.github.arcadiumclient.runtime.client.branding.NametagLayout.end(previous);}
            Object beforeDisabled=getter.invoke(first);
            io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=false;
            previous=io.github.arcadiumclient.runtime.client.branding.NametagLayout.begin(first,"Arcadium QA disabled");
            try{require(getter.invoke(first)==beforeDisabled,"Disabled name masking mutated the retained record");}
            finally{io.github.arcadiumclient.runtime.client.branding.NametagLayout.end(previous);}
            nameScopeProof=Map.of("enabled",true,"ready",true,"passed",true,"semanticCases",9,"reuse",reuse,"actualEntityMixin",true);
            return nameScopeProof;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native name-scope semantic proof failed",error);}
        finally{
            io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=oldOptimized;
            if(setter!=null)try{setter.invoke(first,savedFirst);setter.invoke(second,savedSecond);}catch(ReflectiveOperationException error){throw new IllegalStateException("Name-scope proof did not restore owned records",error);}
        }
    }
    private static Map<String, Object> counters() {
        Map<String, Object> values = new TreeMap<>();
        for (String name : List.of("GpuPlayerPipeline", "GpuSkinLayers", "GpuVoxelSkinPipeline", "NativePlayerPosePipeline", "GpuModelTransforms", "GpuPlayerPackets", "GpuResidentPlayerState", "GpuResolvedInstanceCache", "EntityNameFormatting", "RenderHookAllocationControl","GpuResidentBodies","GpuResidentBodyShaderState","GpuParticles","NametagLayout")) {
            Map<String, Object> data = new TreeMap<>();
            try {
                Class<?> type = Class.forName("io.github.arcadiumclient.runtime.client."+(name.equals("NametagLayout")?"branding.":"optimizations.")+name); data.put("present", true);
                for (Field field : type.getFields()) if (Modifier.isStatic(field.getModifiers()) && (field.getType() == long.class || field.getType() == int.class || field.getType() == boolean.class)) data.put(field.getName(), field.get(null));
                try { data.put("status", type.getMethod("status").invoke(null)); } catch (NoSuchMethodException ignored) {}
            } catch (ReflectiveOperationException absent) { data.put("present", false); }
            values.put(name, data);
        }
        values.put("nameScopeEvidence",nameScopeEvidence());
        values.put("nativeVoxelDraws", Features062Qa.voxelDraws); values.put("nativeVoxelCompiles", Features062Qa.meshCompiles);
        return values;
    }
    private static long counter(String type, String name) {
        try { return ((Number) Class.forName("io.github.arcadiumclient.runtime.client.optimizations." + type).getField(name).get(null)).longValue(); }
        catch (ReflectiveOperationException absent) { return 0; }
    }
    private static void armCounters() {
        phaseHybridStart = counter("GpuSkinLayers", "hybridPlayers");
        phaseCompactStart = counter("GpuPlayerPipeline", "playersDrawn");
        phaseNativeStart = counter("NativePlayerPosePipeline", "bodyPartsQueued");
        phaseNativeSkinStart = counter("GpuPlayerPipeline", "nativeSkinCallbacks");
        phaseResidentActors=counter("GpuResidentBodies","actorsQueued");phaseResidentDraws=counter("GpuResidentBodies","actorsDrawn");phaseResidentDummy=counter("GpuResidentBodyShaderState","dummyBinds");
        phaseVoxelStart = Features062Qa.voxelDraws;
        phaseAnalyticVoxelStart = counter("GpuVoxelSkinPipeline", "analyticParts"); phaseNativeVoxelStart = counter("GpuVoxelSkinPipeline", "nativeParts");
        phaseVoxelDrawStart = counter("GpuVoxelSkinPipeline", "drawCalls"); phaseVoxelVerticesStart = counter("GpuVoxelSkinPipeline", "verticesDrawn");
        phaseAdmissionStart = counter("GpuVoxelSkinPipeline", admissionCounter());
        stageStarted = System.nanoTime(); stablePixelHash = -1; stablePixelSamples = 0; stablePixelsSince = 0; lastPixelSample = 0;
    }
    private static void requireBodyPath() {
        if(!RESIDENT_BODY_MODE.equals("off")) {
            require(counter("GpuResidentBodyShaderState","dummyBinds")>phaseResidentDummy,"Native resident shader control did not bind valid ordinary-mode storage");
            require(counter("GpuResidentBodies","errors")==0,"Resident body rendering reported an error");
            if(RESIDENT_BODY_MODE.equals("control"))require(counter("GpuResidentBodies","actorsQueued")==phaseResidentActors,"Shader control unexpectedly replaced native body records");
            else if(!effectiveSkin||gpuVoxels()) {
                require(counter("GpuResidentBodies","actorsQueued")>phaseResidentActors&&counter("GpuResidentBodies","actorsDrawn")>phaseResidentDraws,"Whole-body records did not reach actual GPU drawing");
                if(RESIDENT_BODY_SHARED)require(counter("GpuResidentBodies","sharedPoseDispatches")>0&&counter("GpuResidentBodies","sharedPoseReuses")>0&&counter("GpuResidentBodies","sharedPoseFallbacks")==0&&counter("GpuResidentBodies","lateBodyCaptures")==0,"The shared body palette did not run or fell back unexpectedly");
                if(VISUAL)for(Actor actor:actors){RenderTrace trace=renderTraces.get(actor);require(trace!=null&&trace.resident==1,"An expected actor did not queue exactly one whole-body record: "+actor.getName());}
            }
        }
        if(NATIVE_VOXEL_ROOT_CONTROL){
            require(BetaOptimizationsConfig.getGpuPlayerPipeline()&&gpuVoxels(),"Native voxel root research requires GPU bodies and GPU outer layers");
            require(counter("GpuPlayerPipeline","playersDrawn")==phaseCompactStart&&counter("NativePlayerPosePipeline","bodyPartsQueued")>phaseNativeStart,
                    "Native voxel root research lost original callbacks or native GPU body capture");
            for(Actor actor:actors){RenderTrace trace=renderTraces.get(actor);
                require(trace!=null&&trace.compact==0&&trace.nativeParts>0,"An actor lost original root/native GPU body capture: "+actor.getName());
                if(effectiveSkin)require(trace.nativeGpuVoxels==6&&trace.analyticVoxels==0&&trace.voxels==6,"An actor lost one of six native mesh callbacks or GPU outer parts: "+actor.getName());
            }return;
        }
        if (NATIVE_BODY_CONTROL) {
            require(BetaOptimizationsConfig.getGpuPlayerPipeline() && !gpuVoxels(), "Native body control requires GPU bodies and original voxel layers");
            require(counter("GpuPlayerPipeline", "playersDrawn") == phaseCompactStart && counter("NativePlayerPosePipeline", "bodyPartsQueued") > phaseNativeStart,
                    "Native body control did not retain original player callbacks and GPU body poses");
            for (Actor actor : actors) {
                RenderTrace trace = renderTraces.get(actor);
                require(trace != null && trace.compact == 0 && trace.nativeParts > 0, "An actor did not use the native GPU body control: " + actor.getName());
                if (effectiveSkin) require(trace.voxels == 6 && trace.analyticVoxels == 0 && trace.nativeGpuVoxels == 0,
                        "Native body control did not draw all six original voxel parts: " + actor.getName());
            }
            return;
        }
        if (!BASELINE && BetaOptimizationsConfig.getGpuPlayerPipeline()) require(counter("GpuPlayerPipeline", "playersDrawn") > phaseCompactStart || counter("NativePlayerPosePipeline", "bodyPartsQueued") > phaseNativeStart,
                "The optimized scene never submitted compact/native GPU body parts");
    }
    private static boolean gpuVoxels() {
        Property<?> property = BetaOptimizationsConfig.INSTANCE.getTree().getProp("gpuVoxelSkinLayers");
        return property != null && Boolean.TRUE.equals(property.get());
    }
    private static void requireVoxelPath() {
        if (!effectiveSkin) return;
        if (!BASELINE && gpuVoxels()) {
            if (!VOXEL_ADMISSION.equals("gpu")) {
                require(counter("GpuVoxelSkinPipeline", "analyticParts") == phaseAnalyticVoxelStart
                        && counter("GpuVoxelSkinPipeline", "nativeParts") == phaseNativeVoxelStart
                        && counter("GpuVoxelSkinPipeline", "drawCalls") == phaseVoxelDrawStart,
                        "A guarded actor still submitted GPU voxel parts");
                require(counter("GpuVoxelSkinPipeline", admissionCounter()) > phaseAdmissionStart, "The expected admission fallback did not run: " + VOXEL_ADMISSION);
                require(Features062Qa.voxelDraws > phaseVoxelStart, "Guarded native voxel meshes did not draw");
                for (Actor actor : actors) {
                    RenderTrace trace = renderTraces.get(actor);
                    require(trace != null && trace.voxels > 0 && trace.analyticVoxels == 0 && trace.nativeGpuVoxels == 0,
                            "An actor did not retain its original voxel rendering: " + actor.getName());
                    require(trace.compact > 0 || trace.nativeParts > 0, "A guarded skin also lost the compact body path: " + actor.getName());
                }
                return;
            }
            require(counter("GpuVoxelSkinPipeline", "analyticParts") - phaseAnalyticVoxelStart
                    + counter("GpuVoxelSkinPipeline", "nativeParts") - phaseNativeVoxelStart > 0, "GPU voxel parts were never submitted");
            require(counter("GpuVoxelSkinPipeline", "drawCalls") > phaseVoxelDrawStart && counter("GpuVoxelSkinPipeline", "verticesDrawn") > phaseVoxelVerticesStart,
                    "Queued voxel parts never reached actual GPU MDI draws");
            long mask = counter("GpuVoxelSkinPipeline", "analyticPartMask") | counter("GpuVoxelSkinPipeline", "nativePartMask");
            require((mask & 0xFC0) == 0xFC0, "GPU voxel rendering omitted an outer skin part: " + Long.toHexString(mask));
        } else {
            require(Features062Qa.voxelDraws > phaseVoxelStart, "Native compiled voxel meshes did not draw during the measured phase");
            if (!BASELINE && BetaOptimizationsConfig.getGpuPlayerPipeline() && !NATIVE_BODY_CONTROL) {
                boolean nativeRoute=counter("GpuPlayerPipeline", "nativeSkinCallbacks")>phaseNativeSkinStart
                        && counter("NativePlayerPosePipeline", "bodyPartsQueued")>phaseNativeStart;
                require(counter("GpuSkinLayers", "hybridPlayers")>phaseHybridStart || nativeRoute,
                        "Detailed skins never used compact bodies or the verified native-callback GPU body path");
                if(nativeRoute)for(Actor actor:actors){
                    RenderTrace trace=renderTraces.get(actor);
                    require(trace!=null&&(trace.compact>0||trace.nativeParts>0),"The native skin route lost an actor body: "+actor.getName());
                    if(trace.voxels>0)require(trace.voxels==6&&trace.compact==0&&trace.nativeParts>0&&trace.analyticVoxels==0&&trace.nativeGpuVoxels==0,
                            "The native skin route did not preserve all original outer parts and GPU body poses: "+actor.getName());
                }
            }
        }
    }
    private static String admissionCounter() {
        return switch (VOXEL_ADMISSION) {
            case "binary-fallback" -> "binaryAlphaFallbacks";
            case "fractional-fallback" -> "fractionalAlphaFallbacks";
            case "dimension-fallback" -> "dimensionFallbacks";
            case "pending-fallback" -> "unknownImageFallbacks";
            default -> "admissionFallbacks";
        };
    }
    private static Map<String, Object> voxelEvidence() {
        Map<String, Object> result = new LinkedHashMap<>(Map.of("gpuEnabled", gpuVoxels(), "nativeMeshEntryCalls", Features062Qa.voxelDraws - phaseVoxelStart,
                "analyticParts", counter("GpuVoxelSkinPipeline", "analyticParts") - phaseAnalyticVoxelStart,
                "nativeGpuParts", counter("GpuVoxelSkinPipeline", "nativeParts") - phaseNativeVoxelStart,
                "mdiDrawCalls", counter("GpuVoxelSkinPipeline", "drawCalls") - phaseVoxelDrawStart,
                "verticesDrawn", counter("GpuVoxelSkinPipeline", "verticesDrawn") - phaseVoxelVerticesStart,
                "outerPartMask", Long.toHexString(counter("GpuVoxelSkinPipeline", "analyticPartMask") | counter("GpuVoxelSkinPipeline", "nativePartMask")),
                "requiredOuterPartMask", "fc0"));
        result.put("expectedAdmission", VOXEL_ADMISSION); result.put("admissionCounter", admissionCounter());
        result.put("admissionFallbacks", counter("GpuVoxelSkinPipeline", admissionCounter()) - phaseAdmissionStart);
        return result;
    }
    private static boolean prepareWorld(Minecraft mc) throws Exception {
        mc.openScreen(null); mc.player.abilities.flying = true;
        mc.player.updatePositionAndAngles(0, 6, -10, 0, 8);
        mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.player.lastYaw = 0; mc.player.lastPitch = 8; mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0;
        if (environmentSetup == null) {
            require(mc.getServer() != null, "The fixture requires its owned integrated server");
            environmentSetup = mc.getServer().executeTask(() -> {
                var serverWorld = mc.getServer().getWorld(0);
                serverWorld.getGameRules().set("doDaylightCycle", "false");
                serverWorld.getGameRules().set("doMobSpawning", "false");
                serverWorld.getGameRules().set("randomTickSpeed", "0");
                serverWorld.setTimeOfDay(6000);
                serverWorld.getData().setRaining(false); serverWorld.getData().setThundering(false);
                serverWorld.getData().setRainTime(1_000_000); serverWorld.getData().setThunderTime(1_000_000); serverWorld.getData().setClearWeatherTime(1_000_000);
                int removed = 0;
                for (var entity : new ArrayList<>(serverWorld.entities)) if (!(entity instanceof PlayerEntity)) { serverWorld.removeEntityNow(entity); removed++; }
                return Map.of("doMobSpawning", false, "doDaylightCycle", false, "randomTickSpeed", 0, "timeOfDay", 6000,
                        "raining", false, "thundering", false, "removedNonPlayers", removed);
            });
            status("preparing_authoritative_world"); return false;
        }
        if (!environmentSetup.isDone()) return false;
        report.put("serverEnvironment", environmentSetup.get());
        updateServerDiagnostics(mc);
        if (!normalViewWarmup(mc)) {
            if (System.nanoTime() - settled > 90_000_000_000L) throw new IllegalStateException("Native D9 halo did not make the original D8 population/send set ready");
            return false;
        }
        mc.world.setTimeOfDay(6000); mc.world.getData().setRaining(false); mc.world.getData().setThundering(false);
        Map<String, Object> state = terrainState(mc);
        boolean onlyPlayers = mc.world.entities.stream().allMatch(entity -> entity instanceof PlayerEntity);
        if (!onlyPlayers || !Boolean.TRUE.equals(state.get("ready"))) {
            worldReadySince = 0;
            if (System.nanoTime() - lastReadinessLog > 1_000_000_000L) {
                lastReadinessLog = System.nanoTime();
                Map<String, Object> summary = new LinkedHashMap<>(state);
                if (state.get("inventory") instanceof Map<?, ?> inventory) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    for (String key : List.of("cameraChunk", "radius", "loadedChunkCount", "missingCameraChunks", "extraLoadedChunks", "sectionCount", "missingCameraSections")) data.put(key, inventory.get(key));
                    summary.put("inventory", data);
                }
                Files.writeString(output().resolveSibling("readiness-current.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary));
                status("waiting_for_initial_chunks_and_entities");
            }
            if (System.nanoTime() - settled > 90_000_000_000L) { report.put("incompleteInitialTerrain", state); report.put("incompleteFogAndSky", fogState(mc)); throw new IllegalStateException("Authoritative full-radius chunk/section data did not arrive"); }
            return false;
        }
        if (worldReadySince == 0) worldReadySince = System.nanoTime();
        if (System.nanoTime() - worldReadySince < 2_000_000_000L) return false;
        report.put("initialTerrain", state); return true;
    }
    private static void updateServerDiagnostics(Minecraft mc) throws Exception {
        if (serverDiagnostics != null && serverDiagnostics.isDone()) {
            @SuppressWarnings("unchecked") Map<String, Object> state = (Map<String, Object>) serverDiagnostics.get();
            serverTracking = state; report.put("serverTracking", state);
            Files.writeString(output().resolveSibling("server-tracking-current.json"), new GsonBuilder().setPrettyPrinting().create().toJson(state));
            serverDiagnostics = null;
        }
        if (serverDiagnostics != null || System.nanoTime() - lastServerDiagnostic < 1_000_000_000L) return;
        lastServerDiagnostic = System.nanoTime();
        serverDiagnostics = mc.getServer().executeTask(() -> {
            var serverWorld = mc.getServer().getWorld(0); var tracker = serverWorld.getChunkMap();
            int radius = ((PlayerPipeline072ChunkMapAccess) tracker).player072$trackingRadius();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("trackingRadius", radius); result.put("playerManagerRadius", mc.getServer().getPlayerManager().getChunkViewDistance());
            List<List<Integer>> active = ((PlayerPipeline072WorldAccess) serverWorld).player072$tickingChunks().stream()
                    .map(p -> List.of(p.x, p.z)).sorted(Comparator.comparingInt((List<Integer> p) -> p.get(0)).thenComparingInt(p -> p.get(1))).toList();
            result.put("activeTickingChunks", active); result.put("activeTickingChunkCount", active.size());
            Map<List<Integer>, net.minecraft.world.chunk.WorldChunk> existing = new HashMap<>();
            for (var chunk : serverWorld.chunkCache.getChunks()) existing.put(List.of(chunk.chunkX, chunk.chunkZ), chunk);
            List<Object> players = new ArrayList<>();
            for (var entity : serverWorld.players) if (entity instanceof ServerPlayerEntity player) {
                Map<String, Object> data = new LinkedHashMap<>();
                int cx = (int) Math.floor(player.trackedX) >> 4, cz = (int) Math.floor(player.trackedZ) >> 4;
                data.put("name", player.getName()); data.put("position", List.of(player.x, player.y, player.z)); data.put("trackedPosition", List.of(player.trackedX, player.trackedZ));
                data.put("trackingCentre", List.of(cx, cz)); data.put("entityChunk", List.of(player.chunkX, player.chunkZ));
                List<List<Integer>> pending = player.pendingChunks.stream().map(p -> List.of(p.x, p.z))
                        .sorted(Comparator.comparingInt((List<Integer> p) -> p.get(0)).thenComparingInt(p -> p.get(1))).toList();
                data.put("pendingSendCoordinates", pending); data.put("pendingSendCount", pending.size());
                List<Object> subscription = new ArrayList<>();
                for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
                    List<Integer> pos = List.of(x, z); var chunk = existing.get(pos); Map<String, Object> chunkState = new LinkedHashMap<>();
                    chunkState.put("coordinate", pos); chunkState.put("holderExists", tracker.hasChunk(x, z)); chunkState.put("sentToPlayer", tracker.isChunkWithinView(player, x, z));
                    chunkState.put("pendingSend", pending.contains(pos)); chunkState.put("existsInServerCache", chunk != null); chunkState.put("activeTicking", active.contains(pos));
                    if (chunk != null) {
                        chunkState.put("loaded", chunk.isLoaded()); chunkState.put("hasTicked", ((PlayerPipeline072ChunkStateAccess) chunk).player072$hasTicked());
                        chunkState.put("terrainPopulated", chunk.isTerrainPopulated()); chunkState.put("lightPopulated", chunk.isLightPopulated()); chunkState.put("sendablePopulated", chunk.isPopulated());
                    }
                    subscription.add(chunkState);
                }
                data.put("subscriptions", subscription); players.add(data);
            }
            result.put("players", players); result.put("serverCachedChunks", existing.size());
            return result;
        });
    }
    @SuppressWarnings("unchecked")
    private static boolean normalViewWarmup(Minecraft mc) throws Exception {
        if (serverTracking == null) return false;
        if (viewWarmupStage == 0) {
            mc.options.viewDistance = 9; viewWarmupStage = 1;
            viewWarmupTransitions.add(Map.of("from", 8, "to", 9, "reason", "Normal native view-distance halo population warmup"));
            report.put("viewWarmupTransitions", viewWarmupTransitions); status("native_D9_halo_warmup"); return false;
        }
        int serverRadius = ((Number) serverTracking.get("trackingRadius")).intValue();
        List<?> players = (List<?>) serverTracking.get("players");
        if (players.size() != 1) return false;
        Map<String, Object> player = (Map<String, Object>) players.get(0);
        List<?> centre = (List<?>) player.get("trackingCentre");
        if (((Number) centre.get(0)).intValue() != 0 || ((Number) centre.get(1)).intValue() != -1) return false;
        int expected = 0, pending = 0, unlit = 0, unpopulated = 0, unticked = 0, unsent = 0;
        for (Object raw : (List<?>) player.get("subscriptions")) {
            Map<?, ?> chunk = (Map<?, ?>) raw; List<?> coordinate = (List<?>) chunk.get("coordinate");
            int x = ((Number) coordinate.get(0)).intValue(), z = ((Number) coordinate.get(1)).intValue();
            if (Math.abs(x) > 8 || Math.abs(z + 1) > 8) continue;
            expected++;
            if (Boolean.TRUE.equals(chunk.get("pendingSend"))) pending++;
            if (!Boolean.TRUE.equals(chunk.get("lightPopulated"))) unlit++;
            if (!Boolean.TRUE.equals(chunk.get("terrainPopulated"))) unpopulated++;
            if (!Boolean.TRUE.equals(chunk.get("hasTicked"))) unticked++;
            if (!Boolean.TRUE.equals(chunk.get("sentToPlayer"))) unsent++;
        }
        boolean ready = expected == 289 && pending == 0 && unlit == 0 && unpopulated == 0 && unticked == 0 && unsent == 0;
        Map<String, Object> inner = Map.of("originalD8Subscriptions", expected, "pending", pending, "unlit", unlit,
                "unpopulated", unpopulated, "unticked", unticked, "unsent", unsent, "ready", ready, "serverRadius", serverRadius);
        report.put("haloOriginalD8Readiness", inner);
        Files.writeString(output().resolveSibling("halo-readiness-current.json"), new GsonBuilder().setPrettyPrinting().create().toJson(inner));
        if (viewWarmupStage == 1) {
            if (serverRadius != 9 || !ready) return false;
            viewWarmupTransitions.add(Map.of("from", 9, "to", 8, "originalD8Readiness", inner));
            mc.options.viewDistance = 8; viewWarmupStage = 2;
            status("native_D8_restored_waiting_for_unloads"); return false;
        }
        return serverRadius == 8 && ready && ((Number) player.get("pendingSendCount")).intValue() == 0;
    }

    private static Map<String, Object> terrainState(Minecraft mc) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        Object renderer;
        try { renderer = mc.worldRenderer.getClass().getMethod("argentum$getWorldRenderer").invoke(mc.worldRenderer); }
        catch (NoSuchMethodException otherBinding) { renderer = mc.worldRenderer.getClass().getMethod("celeritas$getWorldRenderer").invoke(mc.worldRenderer); }
        if (renderer == null) { state.put("ready", false); return state; }
        Object sections = renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);
        if (sections == null) { state.put("ready", false); return state; }
        Object builder = sections.getClass().getMethod("getBuilder").invoke(sections);
        int busy = ((Number) builder.getClass().getMethod("getBusyThreadCount").invoke(builder)).intValue();
        int scheduled = ((Number) builder.getClass().getMethod("getScheduledJobCount").invoke(builder)).intValue();
        boolean queueEmpty = (boolean) builder.getClass().getMethod("isBuildQueueEmpty").invoke(builder);
        int pendingUploads = collectionSize(sections, "buildResults"), pendingAsync = collectionSize(sections, "asyncSubmittedTasks");
        int visible = ((Number) renderer.getClass().getMethod("getVisibleChunkCount").invoke(renderer)).intValue();
        var inventory = inventories(mc, renderer, sections);
        boolean targetSections = true;
        int columns = PLAYERS == 3 ? 3 : PLAYERS == 20 ? 5 : 10;
        for (int i = 0; i < PLAYERS; i++) {
            int cx = (int) Math.floor((i % columns - (columns - 1) * .5) * 1.45) >> 4;
            int cz = (int) Math.floor(2 + i / columns * 1.7) >> 4;
            targetSections &= mc.world.getChunkSource().hasChunk(cx, cz)
                    && (boolean) renderer.getClass().getMethod("isSectionReady", int.class, int.class, int.class).invoke(renderer, cx, 0, cz);
        }
        state.put("queueEmpty", queueEmpty); state.put("busyWorkers", busy); state.put("scheduledJobs", scheduled);
        state.put("pendingUploads", pendingUploads); state.put("pendingAsyncTasks", pendingAsync); state.put("visibleChunks", visible);
        state.put("targetSectionsReady", targetSections); state.put("chunkDebug", mc.worldRenderer.getChunkDebugInfo());
        state.put("inventory", inventory);
        state.put("ready", queueEmpty && busy == 0 && scheduled == 0 && pendingUploads == 0 && pendingAsync == 0 && visible > 0 && targetSections
                && ((List<?>) inventory.get("missingCameraChunks")).isEmpty() && ((List<?>) inventory.get("extraLoadedChunks")).isEmpty()
                && ((Number) inventory.get("missingCameraSections")).intValue() == 0
                && ((Number) inventory.get("sectionCount")).intValue() == ((Number) inventory.get("expectedCameraSectionCount")).intValue());
        return state;
    }
    private static Map<String, Object> inventories(Minecraft mc, Object renderer, Object manager) throws Exception {
        int cameraX = (int) Math.floor(mc.player.x) >> 4, cameraZ = (int) Math.floor(mc.player.z) >> 4, radius = mc.options.viewDistance;
        Comparator<List<Integer>> order = Comparator.comparingInt((List<Integer> p) -> p.get(0)).thenComparingInt(p -> p.get(1)).thenComparingInt(p -> p.size() > 2 ? p.get(2) : 0);
        SortedSet<List<Integer>> loaded = new TreeSet<>(order), expected = new TreeSet<>(order), sections = new TreeSet<>(order);
        for (var chunk : ((PlayerPipeline072ChunksAccess) mc.world.getChunkSource()).player072$loadedChunks()) loaded.add(List.of(chunk.chunkX, chunk.chunkZ));
        for (int x = cameraX - radius; x <= cameraX + radius; x++) for (int z = cameraZ - radius; z <= cameraZ + radius; z++) expected.add(List.of(x, z));
        Collection<?> actualSections = (Collection<?>) manager.getClass().getMethod("getAllRenderSections").invoke(manager);
        for (Object section : actualSections) {
            Class<?> type = section.getClass();
            sections.add(List.of(((Number) type.getMethod("getChunkX").invoke(section)).intValue(),
                    ((Number) type.getMethod("getChunkY").invoke(section)).intValue(), ((Number) type.getMethod("getChunkZ").invoke(section)).intValue()));
        }
        List<List<Integer>> missing = expected.stream().filter(p -> !loaded.contains(p)).toList();
        List<List<Integer>> extra = loaded.stream().filter(p -> !expected.contains(p)).toList();
        int low = ((Number) renderer.getClass().getMethod("getMinimumBuildHeight").invoke(renderer)).intValue() >> 4;
        int high = (((Number) renderer.getClass().getMethod("getMaximumBuildHeight").invoke(renderer)).intValue() + 15) >> 4;
        int missingSections = 0;
        for (var chunk : expected) for (int y = low; y < high; y++) if (!sections.contains(List.of(chunk.get(0), y, chunk.get(1)))) missingSections++;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cameraChunk", List.of(cameraX, cameraZ)); result.put("radius", radius); result.put("expectedCameraChunks", expected);
        result.put("loadedChunkCoordinates", loaded); result.put("loadedChunkCount", loaded.size()); result.put("missingCameraChunks", missing); result.put("extraLoadedChunks", extra);
        result.put("sectionCoordinates", sections); result.put("sectionCount", sections.size()); result.put("missingCameraSections", missingSections);
        result.put("expectedCameraSectionCount", expected.size() * (high - low));
        result.put("loadedChunksSha256", digest(new Gson().toJson(loaded))); result.put("sectionsSha256", digest(new Gson().toJson(sections)));
        return result;
    }
    private static String digest(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private static boolean stableInventory(Map<String, Object> terrain) {
        Map<?, ?> data = (Map<?, ?>) terrain.get("inventory");
        if (data == null) return false;
        String signature = data.get("loadedChunksSha256") + ":" + data.get("sectionsSha256");
        if (!signature.equals(inventorySignature)) { inventorySignature = signature; inventoryStableSince = System.nanoTime(); return false; }
        return System.nanoTime() - inventoryStableSince >= 2_000_000_000L;
    }
    @SuppressWarnings("unchecked")
    private static void matchDataset(Map<String, Object> terrain) throws Exception {
        require(Boolean.TRUE.equals(terrain.get("ready")), "The camera chunk/section dataset became incomplete");
        Map<String, Object> dataset = (Map<String, Object>) terrain.get("inventory");
        if (canonicalDataset == null) canonicalDataset = dataset;
        if (!Objects.equals(canonicalDataset.get("loadedChunksSha256"), dataset.get("loadedChunksSha256"))
                || !Objects.equals(canonicalDataset.get("sectionsSha256"), dataset.get("sectionsSha256"))) {
            report.put("datasetMismatch", Map.of("reference", canonicalDataset, "actual", dataset));
            throw new IllegalStateException("The authoritative loaded chunk/section dataset differs from the frozen reference");
        }
        report.put("dataset", dataset);
        Files.writeString(output().resolveSibling("dataset.json"), new GsonBuilder().setPrettyPrinting().create().toJson(dataset));
    }
    private static Map<String, Object> fogState(Minecraft mc) {
        var fog = (PlayerPipeline072FogAccess) mc.gameRenderer;
        Map<String, Object> result = new TreeMap<>();
        Map<String, Float> values = Map.of("fogRed", fog.player072$fogRed(), "fogGreen", fog.player072$fogGreen(), "fogBlue", fog.player072$fogBlue(),
                "fogBrightness", fog.player072$fogBrightness(), "lastFogBrightness", fog.player072$lastFogBrightness(),
                "skyDarkness", fog.player072$skyDarkness(), "lastSkyDarkness", fog.player072$lastSkyDarkness(), "gamma", mc.options.gamma,
                "entityBrightness", mc.player.getBrightness(.5f), "sunAngle", mc.world.getSunAngle(.5f));
        for (var entry : values.entrySet()) result.put(entry.getKey(), Map.of("value", entry.getValue(), "rawFloatBits", Integer.toHexString(Float.floatToRawIntBits(entry.getValue()))));
        var sky = mc.world.getSkyColor(mc.player, .5f); var color = mc.world.getFogColor(.5f);
        result.put("skyColor", List.of(sky.x, sky.y, sky.z)); result.put("worldFogColor", List.of(color.x, color.y, color.z));
        result.put("worldTimeOfDay", mc.world.getTimeOfDay());
        return result;
    }
    private static int collectionSize(Object instance, String fieldName) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(fieldName); field.setAccessible(true);
            return ((Collection<?>) field.get(instance)).size();
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(fieldName);
    }
    private static void validatePresence(Minecraft mc) throws Exception {
        for (int i = 0; i < actors.size(); i++) {
            Actor actor = actors.get(i);
            if (actor.removed || !mc.world.entities.contains(actor) || !mc.world.players.contains(actor) || mc.world.getEntity(72000 + i) != actor) {
                report.put("invalidWorldState", worldState(mc));
                throw new IllegalStateException("A synthetic actor left the world: " + actor.getName());
            }
        }
        if (mc.world.entities.stream().anyMatch(entity -> !(entity instanceof PlayerEntity))) {
            report.put("invalidWorldState", worldState(mc)); throw new IllegalStateException("An uncontrolled non-player entity entered the fixture");
        }
    }
    private static Map<String, Object> worldState(Minecraft mc) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 0; i < actors.size(); i++) {
            Actor actor = actors.get(i); Map<String, Object> state = new LinkedHashMap<>();
            state.put("name", actor.getName()); state.put("id", 72000 + i); state.put("removed", actor.removed);
            state.put("inEntities", mc.world.entities.contains(actor)); state.put("inPlayers", mc.world.players.contains(actor)); state.put("idResolves", mc.world.getEntity(72000 + i) == actor);
            state.put("inChunk", actor.inChunk); state.put("chunk", List.of(actor.chunkX, actor.chunkY, actor.chunkZ));
            state.put("position", List.of(actor.x, actor.y, actor.z)); state.put("previous", List.of(actor.prevX, actor.prevY, actor.prevZ));
            state.put("last", List.of(actor.lastX, actor.lastY, actor.lastZ)); state.put("invisible", actor.isInvisible());
            state.put("age", actor.ticks); state.put("walk", actor.walkAnimationProgress);
            state.put("yaw", actor.yaw); state.put("pitch", actor.pitch); state.put("headYaw", actor.headYaw);
            var shape = actor.getShape();
            state.put("bounds", List.of(shape.minX, shape.minY, shape.minZ, shape.maxX, shape.maxY, shape.maxZ));
            state.put("spectator", actor.isSpectator()); state.put("sleeping", actor.isSleeping()); state.put("riding", actor.isRiding());
            state.put("skinMask", actor.getSyncedData().getByte(10)); players.add(state);
        }
        Map<String, Integer> types = new TreeMap<>(); for (var entity : mc.world.entities) types.merge(entity.getClass().getName(), 1, Integer::sum);
        return Map.of("actors", players, "entityCount", mc.world.entities.size(), "playerCount", mc.world.players.size(), "types", types,
                "rendererEntityDebug", mc.worldRenderer.getEntityDebugInfo());
    }

    public static void beginWorld() {
        NameReset072Qa.beforeWorld();
        ActorPacket072Qa.beforeWorld();
        if (enabled() && VISUAL && !done) renderTraces.clear();
    }
    public record RenderCheckpoint(long compact, long nativeParts, int voxels, long analyticVoxels, long nativeGpuVoxels,long resident) {}
    private static final class RenderTrace { int calls; long compact, nativeParts, voxels, analyticVoxels, nativeGpuVoxels,resident; }
    public static RenderCheckpoint beginPlayer(net.minecraft.client.entity.living.player.ClientPlayerEntity player) {
        if (!enabled() || !VISUAL || done || !(player instanceof Actor)) return null;
        RenderCheckpoint checkpoint=new RenderCheckpoint(counter("GpuPlayerPipeline", "playersDrawn"), counter("NativePlayerPosePipeline", "bodyPartsQueued"), Features062Qa.voxelDraws,
                counter("GpuVoxelSkinPipeline", "analyticParts"), counter("GpuVoxelSkinPipeline", "nativeParts"),counter("GpuResidentBodies","actorsQueued"));
        GpuVoxelSkin072QualityControls.nativeRootActor(true);return checkpoint;
    }
    public static void endPlayer(net.minecraft.client.entity.living.player.ClientPlayerEntity player, RenderCheckpoint before) {
        if (before == null || !(player instanceof Actor actor)) return;
        try{
            RenderTrace trace = renderTraces.computeIfAbsent(actor, ignored -> new RenderTrace()); trace.calls++;
            trace.compact += counter("GpuPlayerPipeline", "playersDrawn") - before.compact();
            trace.nativeParts += counter("NativePlayerPosePipeline", "bodyPartsQueued") - before.nativeParts();
            trace.voxels += Features062Qa.voxelDraws - before.voxels();
            trace.analyticVoxels += counter("GpuVoxelSkinPipeline", "analyticParts") - before.analyticVoxels();
            trace.nativeGpuVoxels += counter("GpuVoxelSkinPipeline", "nativeParts") - before.nativeGpuVoxels();
            trace.resident+=counter("GpuResidentBodies","actorsQueued")-before.resident();
            ActorPacket072Qa.afterPlayer(actor,counter("GpuPlayerPipeline", "playersDrawn")-before.compact());
        }finally{GpuVoxelSkin072QualityControls.nativeRootActor(false);}
    }
    private static List<Map<String, Object>> actorRenderState() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Actor actor : actors) {
            RenderTrace trace = renderTraces.get(actor);
            result.add(Map.of("name", actor.getName(), "rendererCalls", trace == null ? 0 : trace.calls,
                    "compactBodies", trace == null ? 0 : trace.compact, "nativeGpuBodyParts", trace == null ? 0 : trace.nativeParts, "nativeVoxelCalls", trace == null ? 0 : trace.voxels,
                    "analyticGpuVoxelParts", trace == null ? 0 : trace.analyticVoxels, "nativeGpuVoxelParts", trace == null ? 0 : trace.nativeGpuVoxels,"residentActorRecords",trace==null?0:trace.resident));
        }
        return result;
    }
    private static void readPixels(Minecraft mc) {
        int bytes = mc.width * mc.height * 4;
        if (visualPixels == null || visualPixels.capacity() != bytes) visualPixels = BufferUtils.createByteBuffer(bytes);
        visualPixels.clear();
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getRenderTarget().frameBufferId); GL11.glReadPixels(0, 0, mc.width, mc.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, visualPixels); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
    }
    private static void capture(Minecraft mc, String filename) throws Exception {
        IntBuffer pixels = visualPixels.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        BufferedImage image = new BufferedImage(mc.width, mc.height, BufferedImage.TYPE_INT_ARGB); int[] row = new int[mc.width];
        for (int y = 0; y < mc.height; y++) { pixels.position((mc.height - y - 1) * mc.width); pixels.get(row); image.setRGB(0, y, mc.width, 1, row, 0, mc.width); }
        Path target = output().getParent().resolve(filename); ImageIO.write(image, "png", target.toFile());
    }
    private static Path output() { return Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath(); }
    private static void status(String state) throws Exception {
        Files.createDirectories(output().getParent()); Files.writeString(output().resolveSibling("status.json"), new Gson().toJson(Map.of("state", state, "phase", phase)));
    }
    private static void finish(Throwable failure) {
        if (done) return;
        if (failure == null && NameReset072Qa.enabled()) try { report.put("nativeNameResetProof", NameReset072Qa.verify()); } catch (Throwable error) { failure = error; }
        if (failure == null && ActorPacket072Qa.enabled()) try { report.put("nativeActorPacketProof", ActorPacket072Qa.verify()); } catch (Throwable error) { failure = error; }
        if(failure==null&&ResidentMaterial072Evidence.enabled())try{report.put("residentMaterialProof",ResidentMaterial072Evidence.verify());}catch(Throwable error){failure=error;}
        done = true; report.put("passed", failure == null); report.put("syntheticOffline", true); report.put("phase", phase);
        report.put("profiling", PROFILING); report.put("diagnosticOnly", PROFILING||NATIVE_VOXEL_ROOT_CONTROL||!RESIDENT_BODY_MODE.equals("off")||RESIDENT_BODY_PERFORMANCE); report.put("notFinalPerformance", PROFILING||NATIVE_VOXEL_ROOT_CONTROL||!RESIDENT_BODY_MODE.equals("off")||RESIDENT_BODY_PERFORMANCE);
        report.put("scope", NATIVE_VOXEL_ROOT_CONTROL ? "Correctness-only original-callback body root and GPU voxel diagnostic on the pinned pre-admission main. No FPS qualification."
                : PROFILING ? "Diagnostic-only JFR capture; timing includes profiler overhead and is not final FPS evidence. Owned offline synthetic players."
                : "Owned local synthetic player rendering fixture; not real Hypixel performance or another PC measurement.");
        report.put("nativeVoxelDraws", Features062Qa.voxelDraws); report.put("nativeVoxelCompiles", Features062Qa.meshCompiles);
        if (failure != null) { report.put("failure", failure.toString()); failure.printStackTrace(); }
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class Actor extends RemoteClientPlayerEntity {
        private final int index;
        Actor(net.minecraft.world.World world, GameProfile profile, int index) { super(world, profile); this.index = index; }
        @Override public void tick() {
            if (VISUAL) {
                ticks = 100 + index % 20; walkAnimationProgress = 2 + index * .1f;
                walkAnimationSpeed = lastWalkAnimationSpeed = .6f;
                yaw = lastYaw = bodyYaw = lastBodyYaw = 180 + index % 13;
                pitch = lastPitch = 10 + index % 9; headYaw = lastHeadYaw = yaw + 10;
                return;
            }
            lastX = prevX = x; lastY = prevY = y; lastZ = prevZ = z;
            lastYaw = yaw; lastPitch = pitch; lastBodyYaw = bodyYaw; lastHeadYaw = headYaw;
            headYaw = bodyYaw + (float) Math.sin((ticks + index * 3) * .045) * 16;
            lastWalkAnimationSpeed = walkAnimationSpeed; walkAnimationProgress += .6f; walkAnimationSpeed = .6f;
        }
    }
}
