// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPackets;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResolvedInstanceCache;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferTarget;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

public final class ResolvedInstance072Qa {
    private static final int MAGIC = Float.floatToRawIntBits(-9876543f), SORT_MAGIC = Float.floatToRawIntBits(-7654321f);
    private static final String[] CASES = {"repeated", "changed-cpu-word", "changed-pose-domain", "changed-template-domain", "invalidated-owner", "aborted-transaction", "abort-after-hit"};
    private static final String[] COUNTERS = {"uploadCallbacks", "driverCallbacks", "bindCallbacks", "sizeCallbacks", "rawDriverCalls", "drawCallbacks", "sortCallbacks", "bindingSizeVaoChecks"};
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Object> nativeMaterials = new LinkedHashMap<>();
    private static long nativeMaterialCalls;
    private static final Frozen[] actors = new Frozen[3];
    private static final long[][] counts = new long[3][COUNTERS.length];
    private static final long[] nativeUploadReturns = new long[3], repairDriverTransactions = new long[3], abortAfterSkipTriggers = new long[3];
    private static final List<Snapshot>[] snapshots = new List[]{null, new ArrayList<>(), new ArrayList<>()};
    private static final IdentityHashMap<Object, Integer> ownerUploads = new IdentityHashMap<>();
    private static final Set<Object> ownerDraws = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int phase, ticks, wait, warm, index, mode, templateNonce = 1, menuFrames;
    private static boolean done, afterReload;
    private static Future<?> environment;
    private static String failure;
    private static long deletes;
    private static long unloadInvalidations;
    private static GlMutableBuffer currentBuffer;
    private static long currentAddress, currentBytes;
    private static long currentRawBefore, currentRepairsBefore;
    private static int currentVao, currentSortedOccurrence;
    private static Field cacheDepth, cacheRecords;
    private record Snapshot(Object owner, int vertices, int instances, int alpha, int[] words) {}
    private ResolvedInstance072Qa() {}
    public static boolean enabled() { return System.getProperty("arcadium.resolved072.output") != null; }

    public static void tick() {
        if (done) return;
        try {
            Minecraft mc = Minecraft.getInstance(); wait++;
            require(++ticks < 6000, "Resolved instance QA timed out");
            if (failure != null) throw new IllegalStateException(failure);
            if (phase == 0 && wait > 60 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Only an isolated fake-account fixture is permitted");
                require(!Boolean.getBoolean("arcadium.forge.prepared"), "Initial output reuse qualification requires the native non-Forge upload path");
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                Skin3DConfig.INSTANCE.chooseEnabled(false);
                BetaOptimizationsConfig.setGpuPlayerPipeline(true); BetaOptimizationsConfig.setGpuModelTransforms(false);
                BetaOptimizationsConfig.setGpuEquipmentPipeline(false); BetaOptimizationsConfig.setResidentPlayerState(true);
                BetaOptimizationsConfig.setMinimumPlayers(1); BetaOptimizationsConfig.setGpuVoxelSkinLayers(false);
                BetaOptimizationsConfig.setGpuNameTags(false); BetaOptimizationsConfig.setGpuParticles(false);
                BetaOptimizationsConfig.setRetainedHudBatches(false); BetaOptimizationsConfig.setWorldRenderScale(false);
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.fpsLimit = 120; mc.options.viewDistance = 3;
                mc.options.entityShadows = false; mc.options.cloudRenderMode = 0; mc.options.viewBobbing = false; mc.options.hideGui = true; mc.options.perspective = 0;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                report.put("purpose", "Native resolved output reuse correctness; no FPS or timing claim");
                report.put("settings", Map.of("gpuPlayerPipeline", true, "gpuModelTransforms", false, "gpuEquipmentPipeline", false,
                        "residentPlayerState", true, "minimumPlayers", 1, "gpuVoxelSkinLayers", false, "skin3d", false, "renderDistance", 3, "fpsLimit", 120, "renderDelta", .5f));
                mc.startGame("arcadium-resolved072", "Resolved Instance QA", new WorldSettings(720722, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 100) {
                if (environment == null) {
                    environment = mc.getServer().executeTask(() -> {
                        var world = mc.getServer().getWorld(0);
                        world.getGameRules().set("doMobSpawning", "false"); world.getGameRules().set("doDaylightCycle", "false"); world.getGameRules().set("randomTickSpeed", "0");
                        world.setTimeOfDay(6000); world.getData().setRaining(false); world.getData().setThundering(false);
                        int removed = 0;
                        for (var entity : new ArrayList<>(world.entities)) if (!(entity instanceof net.minecraft.entity.living.player.PlayerEntity)) { world.removeEntityNow(entity); removed++; }
                        return removed;
                    });
                    return;
                }
                if (!environment.isDone() || mc.world.entities.stream().anyMatch(entity -> !(entity instanceof net.minecraft.entity.living.player.PlayerEntity))) return;
                report.put("authoritativeMobSpawning", false); report.put("removedInitialNonPlayers", environment.get());
                mc.openScreen(null); mc.player.abilities.flying = true; mc.player.updatePositionAndAngles(0, 19, -10, 0, 5);
                for (int actor = 0; actor < actors.length; actor++) {
                    actors[actor] = new Frozen(mc, new GameProfile(new UUID(0x7207220000000000L, actor + 1), "Resolved" + actor));
                    actors[actor].updatePositionAndAngles((actor - 1) * 2.4, 17, 3, 190, 12);
                    mc.world.forceEntity(722000 + actor, actors[actor]);
                }
                freeze(mc); phase = 2; warm = 0;
                report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("resolution", mc.width + "x" + mc.height);
            } else if (phase == 3) {
                progress("before-resource-reload");
                long before = GpuResolvedInstanceCache.invalidations; mc.reloadResources();
                require(ownerCount() == 0 && GpuResolvedInstanceCache.invalidations > before, "Resource reload did not clear resolved owners");
                report.put("resourceReloadCleared", true); phase = 4; warm = 0;
                progress("resource-reload-cleared");
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void freeze(Minecraft mc) {
        for (Frozen actor : actors) if (actor != null) {
            actor.ticks = 160; actor.yaw = actor.lastYaw = actor.bodyYaw = actor.lastBodyYaw = 190; actor.headYaw = actor.lastHeadYaw = 205;
            actor.pitch = actor.lastPitch = 12; actor.walkAnimationProgress = 2.4f; actor.walkAnimationSpeed = actor.lastWalkAnimationSpeed = .6f;
            actor.attackAnimationProgress = actor.lastAttackAnimationProgress = .35f;
            actor.lastX = actor.prevX = actor.x; actor.lastY = actor.prevY = actor.y; actor.lastZ = actor.prevZ = actor.z;
        }
        mc.player.yaw = mc.player.lastYaw = 0; mc.player.pitch = mc.player.lastPitch = 5;
        mc.player.lastX = mc.player.prevX = mc.player.x; mc.player.lastY = mc.player.prevY = mc.player.y; mc.player.lastZ = mc.player.prevZ = mc.player.z;
        mc.world.setTimeOfDay(6000); mc.world.getData().setRaining(false); mc.world.getData().setThundering(false);
    }

    public static void world(float delta, long deadline, Operation<Void> original) {
        if (done || phase != 2 && phase != 4) { original.call(delta, deadline); return; }
        try {
            Minecraft mc = Minecraft.getInstance(); freeze(mc); GpuResolvedInstanceCache.enabled = true;
            if (++warm <= 80) { original.call(.5f, deadline); return; }
            if (phase == 4) { afterReload = true; index = 0; }
            String label = afterReload ? "after-resource-reload" : CASES[index];
            BufferedImage reference = render(mc, 1, false, deadline, original);
            long oldHits = GpuResolvedInstanceCache.hits, oldSaved = GpuResolvedInstanceCache.bytesAvoided, oldRepairs = GpuResolvedInstanceCache.repairs;
            BufferedImage optimized = render(mc, 2, true, deadline, original);
            long hits = GpuResolvedInstanceCache.hits - oldHits;
            int changed = 0, maximum = 0;
            for (int y = 0; y < reference.getHeight(); y++) for (int x = 0; x < reference.getWidth(); x++) {
                int a = reference.getRGB(x, y), b = optimized.getRGB(x, y); if (a != b) changed++;
                for (int shift = 0; shift < 32; shift += 8) maximum = Math.max(maximum, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
            }
            Path directory = Path.of(System.getProperty("arcadium.resolved072.output")).toAbsolutePath().getParent(); Files.createDirectories(directory);
            ImageIO.write(reference, "png", directory.resolve(label + "-streaming.png").toFile()); ImageIO.write(optimized, "png", directory.resolve(label + "-reuse.png").toFile());
            require(snapshots[1].size() == snapshots[2].size() && !snapshots[1].isEmpty(), "Native draw inventory changed");
            long compared = 0;
            for (int draw = 0; draw < snapshots[1].size(); draw++) {
                Snapshot a = snapshots[1].get(draw), b = snapshots[2].get(draw);
                require(a.owner == b.owner && a.vertices == b.vertices && a.instances == b.instances && a.alpha == b.alpha && Arrays.equals(a.words, b.words), "Resolved GPU words/draw order changed at draw " + draw);
                compared += a.words.length;
            }
            boolean lateAbort = !afterReload && index == 6;
            for (int field : new int[]{0, 5, 6}) require(counts[1][field] == counts[2][field] && counts[1][field] > 0, "Native callback/side-effect count changed: " + COUNTERS[field]);
            for (int field : new int[]{1, 2, 3, 7}) require(counts[1][field] > 0 && counts[2][field] == counts[1][field] + (lateAbort ? 1 : 0), "Native driver transaction count changed: " + COUNTERS[field]);
            require(nativeUploadReturns[1] == counts[1][0] && nativeUploadReturns[2] == counts[2][0], "Original native upload return callbacks changed");
            require(counts[1][4] == counts[1][1], "Streaming did not execute each real raw driver upload");
            if (index == 0) require(hits > 0 && counts[2][1] - counts[2][4] == hits, "No confirmed raw upload suppression: " + GpuResolvedInstanceCache.status());
            else if (lateAbort) require(hits == 0 && GpuResolvedInstanceCache.repairs - oldRepairs == 1 && abortAfterSkipTriggers[2] == 1 &&
                    repairDriverTransactions[2] == 1 && repairDriverTransactions[1] == 0 && counts[2][4] == counts[1][4], "Late-aborted hit did not execute exactly one repair upload");
            else require(hits == 0 && counts[2][4] == counts[2][1], "Changed/aborted state incorrectly reused native output");
            Map<String, Object> result = new LinkedHashMap<>(); result.put("changedPixels", changed); result.put("maximumChannelError", maximum);
            result.put("comparedGpuWords", compared); result.put("cacheHits", hits); result.put("bytesAvoided", GpuResolvedInstanceCache.bytesAvoided - oldSaved);
            result.put("repairs", GpuResolvedInstanceCache.repairs - oldRepairs); result.put("abortAfterSkipTriggers", abortAfterSkipTriggers[2]);
            result.put("postUploadRepairDriverTransactions", repairDriverTransactions[2]);
            result.put("streamingCallbacks", counterMap(1)); result.put("reuseCallbacks", counterMap(2)); report.put(label, result);
            require(maximum == 0 && changed == 0, "Exact full RGBA output changed");
            require(GpuResolvedInstanceCache.residentBytes() <= (8L << 20) && ownerCount() <= 128, "Resolved CPU residency exceeded its budget");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after native reuse test");
            report.put("lastCompletedCase", label); progress("case-complete");
            if (afterReload) phase = 5; else if (++index == CASES.length) phase = 3;
        } catch (Throwable error) { failure = error.toString(); }
        finally { mode = 0; currentBuffer = null; GpuResolvedInstanceCache.enabled = true; }
    }

    private static BufferedImage render(Minecraft mc, int which, boolean reuse, long deadline, Operation<Void> original) {
        mode = which; Arrays.fill(counts[which], 0); snapshots[which].clear(); ownerUploads.clear(); ownerDraws.clear(); currentBuffer = null;
        nativeUploadReturns[which] = repairDriverTransactions[which] = abortAfterSkipTriggers[which] = 0;
        freeze(mc); GpuResolvedInstanceCache.enabled = reuse; GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        original.call(.5f, deadline); return capture(mc);
    }

    public static void upload(Object owner, GlMutableBuffer buffer, IntBuffer payload) {
        if (mode == 0) return;
        counts[mode][0]++; boolean sortedGpu = false;
        for (int at = payload.position(); at + 18 < payload.limit(); at += 19) if (payload.get(at + 1) == MAGIC && payload.get(at + 2) == SORT_MAGIC) { sortedGpu = true; break; }
        int occurrence = sortedGpu ? ownerUploads.merge(owner, 1, Integer::sum) : 0;
        if (occurrence == 2 && index == 1) payload.put(payload.position() + 14, payload.get(payload.position() + 14) ^ 0x000f0f0f);
        if (occurrence == 2 && index == 2) { int job = GpuPlayerPackets.addPose(new Matrix4f(), 0); GpuPlayerPackets.writePose(job, 0, 0, 0, 0, .1f, .2f, .3f); }
        if (occurrence == 2 && index == 3) { float[] template = new float[12 * 16]; template[8] = templateNonce++ * .001f; for (int part = 0; part < 12; part++) template[part * 16 + 12] = template[part * 16 + 13] = template[part * 16 + 14] = 1; GpuPlayerPackets.registerTemplate(template); }
        currentBuffer = buffer; currentAddress = MemoryUtil.memAddress(payload); currentBytes = (long)payload.remaining() * 4;
        currentSortedOccurrence = occurrence; currentRepairsBefore = GpuResolvedInstanceCache.repairs;
    }
    public static void uploaded() { if (mode != 0 && currentBuffer != null) nativeUploadReturns[mode]++; }
    public static void driverStart(GlMutableBuffer buffer, long address, long bytes) {
        if (mode == 0 || buffer != currentBuffer || address != currentAddress || bytes != currentBytes) return;
        counts[mode][1]++; currentVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING); currentRawBefore = counts[mode][4];
        if (GpuResolvedInstanceCache.repairs > currentRepairsBefore) repairDriverTransactions[mode]++;
    }
    public static void driverEnd(GlMutableBuffer buffer, long address, long bytes) {
        if (mode == 0 || buffer != currentBuffer || address != currentAddress || bytes != currentBytes) return;
        require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == buffer.handle() && buffer.getSize() == bytes &&
                GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) == bytes && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == currentVao,
                "Native array-buffer binding, size or VAO state changed");
        counts[mode][7]++;
        if (index == 5) try { if (cacheDepth == null) { cacheDepth = GpuResolvedInstanceCache.class.getDeclaredField("depth"); cacheDepth.setAccessible(true); } GpuResolvedInstanceCache.abort(cacheDepth.getInt(null)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        if (index == 6 && mode == 2 && currentSortedOccurrence == 2 && counts[mode][4] == currentRawBefore && abortAfterSkipTriggers[mode] == 0) try {
            if (cacheDepth == null) { cacheDepth = GpuResolvedInstanceCache.class.getDeclaredField("depth"); cacheDepth.setAccessible(true); }
            int token = cacheDepth.getInt(null);
            require(GpuResolvedInstanceCache.needsRepair(token), "Late abort did not observe an actual suppressed raw upload");
            abortAfterSkipTriggers[mode]++; GpuResolvedInstanceCache.abort(token);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static void bound(Object buffer, GlBufferTarget target) { if (mode != 0 && buffer == currentBuffer && target == GlBufferTarget.ARRAY_BUFFER) counts[mode][2]++; }
    public static void sized(Object buffer) { if (mode != 0 && buffer == currentBuffer) counts[mode][3]++; }
    public static void raw(int target, long bytes, long address) { if (mode != 0 && currentBuffer != null && target == GL15.GL_ARRAY_BUFFER && bytes == currentBytes && address == currentAddress) counts[mode][4]++; }
    public static void sorted() { if (mode != 0) counts[mode][6]++; }
    public static void deleted() { if (enabled()) deletes++; }

    public static void material(net.minecraft.client.render.model.ModelPart part, net.minecraft.resource.Identifier texture, Object pass, boolean armor) {
        if (!enabled() || done || phase < 2) return;
        nativeMaterialCalls++;
        if (nativeMaterials.size() >= 16) return;
        String kind = pass instanceof Enum<?> value ? value.name() : String.valueOf(pass);
        String id = String.valueOf(texture), key = kind + ":" + armor + ":" + id;
        Object model = ((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel();
        nativeMaterials.putIfAbsent(key, Map.of("texture", id, "pass", kind, "armor", armor, "model", model == null ? "null" : model.getClass().getName()));
    }

    private static void readiness() {
        report.put("nativeMaterialCalls", nativeMaterialCalls); report.put("nativeMaterials", nativeMaterials);
        report.put("effectivePlayerPipeline", BetaOptimizationsConfig.getGpuPlayerPipeline());
        report.put("effectiveEquipmentPipeline", BetaOptimizationsConfig.getGpuEquipmentPipeline());
        for (String name : List.of("CeraEmissiveAdmission", "GpuPlayerPipeline", "NativePlayerPosePipeline", "GpuModelTransforms")) try {
            Class<?> type = Class.forName("io.github.arcadiumclient.runtime.client.optimizations." + name);
            report.put(name, type.getMethod("status").invoke(null));
            if (name.equals("CeraEmissiveAdmission")) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (String field : List.of("checked", "trusted", "present", "lookupFailed", "ordinaryTextures", "emissiveFallbacks", "unavailableFallbacks")) {
                    Field value = type.getDeclaredField(field); value.setAccessible(true); values.put(field, value.get(null));
                }
                report.put("admissionState", values);
            }
        } catch (ReflectiveOperationException unavailable) { report.put(name, unavailable.toString()); }
        try {
            Set<String> mixins = new TreeSet<>();
            for (var method : Minecraft.getInstance().getTextureManager().getClass().getDeclaredMethods()) for (var annotation : method.getDeclaredAnnotations()) {
                if (annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged") && mixins.size() < 128)
                    mixins.add((String)annotation.annotationType().getMethod("mixin").invoke(annotation));
            }
            report.put("actualTextureManagerMixins", mixins);
        } catch (ReflectiveOperationException unavailable) { report.put("actualTextureManagerMixins", unavailable.toString()); }
    }

    public static void draw(Object owner, GlMutableBuffer buffer, int vertices, int instances) {
        if (mode == 0) return;
        counts[mode][5]++;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), location = GL20.glGetUniformLocation(program, "uAlphaPass");
        int alpha = location < 0 ? -1 : GL20.glGetUniformi(program, location);
        if (index == 4 && alpha == 2 && ownerDraws.add(owner)) GpuResolvedInstanceCache.invalidate(owner);
        IntBuffer words = BufferUtils.createIntBuffer(instances * 19); int oldRead = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try { GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT); GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffer.handle()); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, words); }
        finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldRead); }
        int[] result = new int[words.remaining()]; words.get(result);
        snapshots[mode].add(new Snapshot(owner, vertices, instances, alpha, result));
        if (buffer == currentBuffer) currentBuffer = null;
    }

    private static int ownerCount() throws ReflectiveOperationException {
        if (cacheRecords == null) { cacheRecords = GpuResolvedInstanceCache.class.getDeclaredField("records"); cacheRecords.setAccessible(true); }
        Object records = cacheRecords.get(null); var method = records.getClass().getDeclaredMethod("ownerCount"); method.setAccessible(true); return (int)method.invoke(records);
    }
    private static Map<String, Long> counterMap(int which) { Map<String, Long> result = new LinkedHashMap<>(); for (int field = 0; field < COUNTERS.length; field++) result.put(COUNTERS[field], counts[which][field]); return result; }
    private static BufferedImage capture(Minecraft mc) {
        int old = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING); IntBuffer pixels = BufferUtils.createIntBuffer(mc.width * mc.height);
        try { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getRenderTarget().frameBufferId); GL11.glReadPixels(0, 0, mc.width, mc.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, old); }
        BufferedImage image = new BufferedImage(mc.width, mc.height, BufferedImage.TYPE_INT_ARGB); int[] row = new int[mc.width];
        for (int y = 0; y < mc.height; y++) { pixels.position((mc.height - 1 - y) * mc.width); pixels.get(row); image.setRGB(0, y, mc.width, 1, row, 0, mc.width); } return image;
    }
    public static void frame() {
        if (done) return;
        if (failure != null) { finish(new IllegalStateException(failure)); return; }
        try {
            if (phase == 5) {
                progress("before-pause-menu");
                Minecraft.getInstance().openScreen(new GameMenuScreen()); phase = 6; menuFrames = 0;
                return;
            }
            if (phase == 6) {
                Minecraft mc = Minecraft.getInstance();
                require(++menuFrames < 240, "Normal pause menu did not become paused");
                require(mc.screen instanceof GameMenuScreen, "Normal pause menu was replaced before disconnect");
                if (menuFrames < 3 || !mc.isPaused()) return;
                var screen = (GameMenuScreen)mc.screen;
                var button = ((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)screen).arcadium$buttons().stream()
                        .filter(value -> value.id == 1 && value.active).findFirst().orElseThrow(() -> new IllegalStateException("Normal disconnect button is unavailable"));
                report.put("menuWasPaused", mc.isPaused()); report.put("worldUnloadVia", "GameMenuScreen.buttonClicked(1)");
                progress("before-normal-menu-disconnect"); unloadInvalidations = GpuResolvedInstanceCache.invalidations;
                ((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)screen).resolved$click(button);
                report.put("afterMenuAction", unloadState(mc)); progress("normal-menu-action-returned");
                if (mc.world != null && mc.screen != null && mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen")) {
                    phase = 7; menuFrames = 0; return;
                }
                completeUnload(mc);
            }
            if (phase == 7) {
                Minecraft mc = Minecraft.getInstance();
                require(++menuFrames < 240, "Normal disconnect confirmation did not become active");
                require(mc.screen != null && mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen"), "Normal disconnect confirmation changed unexpectedly");
                if (menuFrames < 3 || !mc.isPaused()) return;
                var button = ((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream()
                        .filter(value -> value.id == 0 && value.active).findFirst();
                if (button.isEmpty()) return;
                report.put("normalConfirmationScreen", mc.screen.getClass().getName()); report.put("normalConfirmationButton", button.get().id);
                progress("before-normal-confirmation");
                ((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button.get());
                report.put("afterConfirmation", unloadState(mc)); completeUnload(mc);
            }
            if (phase == 8) finish(null);
        } catch (Throwable error) { finish(error); }
    }

    private static Map<String, Object> unloadState(Minecraft mc) throws ReflectiveOperationException {
        return Map.of("worldNull", mc.world == null, "owners", ownerCount(), "invalidationsBefore", unloadInvalidations,
                "invalidationsAfter", GpuResolvedInstanceCache.invalidations, "screen", mc.screen == null ? "null" : mc.screen.getClass().getName());
    }

    private static void completeUnload(Minecraft mc) throws Exception {
        require(mc.world == null && ownerCount() == 0 && GpuResolvedInstanceCache.invalidations > unloadInvalidations, "World unload did not clear resolved owners");
        report.put("worldUnloadCleared", true); report.put("nativeDeleteCallbacks", deletes);
        require(deletes > 0, "No native geometry deletion callback was observed"); phase = 8; progress("world-unload-cleared");
    }

    private static void progress(String stage) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>(report);
        snapshot.put("stage", stage); snapshot.put("phase", phase); snapshot.put("diagnostic", GpuResolvedInstanceCache.status());
        snapshot.put("cacheHits", GpuResolvedInstanceCache.hits); snapshot.put("savedBytes", GpuResolvedInstanceCache.bytesAvoided);
        snapshot.put("residentCpuBytes", GpuResolvedInstanceCache.residentBytes()); snapshot.put("nativeDeleteCallbacks", deletes);
        byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= (128 << 10), "Resolved QA progress exceeded its bound");
        Path output = Path.of(System.getProperty("arcadium.resolved072.output")).toAbsolutePath().resolveSibling("progress.json");
        Files.createDirectories(output.getParent()); Files.write(output, bytes);
    }
    private static void finish(Throwable error) {
        if (done) return;
        try { readiness(); } catch (Throwable unavailable) { report.put("readinessFailure", unavailable.toString()); }
        done = true; report.put("passed", error == null); report.put("diagnostic", GpuResolvedInstanceCache.status()); report.put("residentCpuBytes", GpuResolvedInstanceCache.residentBytes());
        report.put("activeCase", phase >= 5 ? "lifecycle" : afterReload ? "after-resource-reload" : index < CASES.length ? CASES[index] : "lifecycle"); report.put("lastStreamingCallbacks", counterMap(1)); report.put("lastReuseCallbacks", counterMap(2));
        report.put("lastStreamingGpuDraws", snapshots[1].size()); report.put("lastReuseGpuDraws", snapshots[2].size());
        report.put("cacheTotals", Map.of("hits", GpuResolvedInstanceCache.hits, "misses", GpuResolvedInstanceCache.misses,
                "unknownFallbacks", GpuResolvedInstanceCache.unknownFallbacks, "capacityFallbacks", GpuResolvedInstanceCache.capacityFallbacks,
                "repairs", GpuResolvedInstanceCache.repairs, "invalidations", GpuResolvedInstanceCache.invalidations));
        try { report.put("driverService", Class.forName("org.taumc.celeritas.lwjgl.LWJGLServiceProvider").getField("LWJGL").get(null).getClass().getName()); }
        catch (ReflectiveOperationException ignored) { }
        if (error != null) { report.put("error", error.toString()); error.printStackTrace(); }
        try { progress("finished"); Path path = Path.of(System.getProperty("arcadium.resolved072.output")); Files.createDirectories(path.toAbsolutePath().getParent()); Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception write) { write.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(Minecraft mc, GameProfile profile) { super(mc.world, profile); ticks = 160; }
        @Override public void tick() { ticks = 160; }
    }
}
