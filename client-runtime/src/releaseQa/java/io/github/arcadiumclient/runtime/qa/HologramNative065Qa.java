// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.client.optimizations.FastInvisibleHolograms;
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags;
import io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.entity.ArmorStandRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.ArmorStandModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class HologramNative065Qa {
    private static final int WIDTH = 960, HEIGHT = 540, COUNT = 16;
    private static final boolean RETAINED_TEXT = Boolean.getBoolean("arcadium.holograms065.retainedText");
    private static final boolean COMPACT_PREPARATION = Boolean.getBoolean("arcadium.compactNames066");
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static int ticks, phase, wait, bodies, names;
    private static boolean done, verifying;
    private static int framebuffer, color, depth;
    private static Object batch;
    private static Method begin, end;
    private static EntityRenderDispatcher dispatcher;
    private static String textPattern;

    private HologramNative065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.holograms065.output") != null; }
    public static void noteBody() { if (verifying) bodies++; }
    public static void noteName() { if (verifying) names++; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Hologram QA requires the isolated offline fixture");
            require(++ticks < 1800, "Hologram QA timed out at phase " + phase); wait++;
            if (phase == 0 && ticks > 65 && mc.screen != null) {
                ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; GL11.glFlush(); Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                mc.startGame("arcadium-hologram-065", "Hologram QA", new WorldSettings(65065, WorldSettings.GameMode.CREATIVE,
                    false, false, WorldGeneratorType.FLAT).enableCommands()); phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 90) {
                mc.openScreen(null); report.putAll(verify()); finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private record Image(byte[] pixels, int bodies, int names, long accelerated, List<Integer> state) { }

    public static Map<String, Object> verify() throws Exception {
        Files.createDirectories(output().toAbsolutePath().getParent());
        Minecraft mc = Minecraft.getInstance(); dispatcher = mc.getEntityRenderDispatcher();
        boolean oldFast = BetaOptimizationsConfig.getFastInvisibleHolograms(), oldGpu = BetaOptimizationsConfig.getGpuNameTags();
        boolean oldRetained = BetaOptimizationsConfig.getRetainedNameTagScenes(); int oldMinimum = BetaOptimizationsConfig.getMinimumGpuLabels();
        boolean oldCompact=BetaOptimizationsConfig.getCompactNameTagPreparation();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer viewport = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        float yaw = dispatcher.cameraYaw, pitch = dispatcher.cameraPitch;
        List<ArmorStandEntity> stands = fixtures(); Map<String, Object> checks = report;
        try {
            BetaOptimizationsConfig.setRetainedNameTagScenes(false);
            BetaOptimizationsConfig.setMinimumGpuLabels(1);
            dispatcher.cameraYaw = 0; dispatcher.cameraPitch = 0;
            framebuffer = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            color = GL11.glGenTextures(); GlStateManager.bindTexture(color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            depth = GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, WIDTH, HEIGHT);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Hologram QA framebuffer is incomplete");
            Object worldRenderer = Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getMethod("instanceNullable").invoke(null);
            require(worldRenderer != null, "The native Argentum world renderer is missing");
            batch = worldRenderer.getClass().getMethod("getNameTagBatch").invoke(worldRenderer);
            begin = batch.getClass().getMethod("begin", Matrix4fc.class); end = batch.getClass().getMethod("end");
            require(Class.forName("dev.rdh.argentum.impl.render.entity.NameTagBatch").getMethod("active").invoke(null) == null, "A name-tag batch leaked across frames");
            if (RETAINED_TEXT) return retainedText(stands);
            checks.put("fallbackCases", fallbacks(stands.get(0)));
            Image nativeReference = image(stands, false, false, false, 0);
            Image nativeFast = image(stands, true, false, false, 0);
            checks.put("nativeReference", Map.of("bodyCalls", nativeReference.bodies, "nameCalls", nativeReference.names, "visiblePixels", visiblePixels(nativeReference), "state", nativeReference.state));
            checks.put("nativeFast", Map.of("bodyCalls", nativeFast.bodies, "nameCalls", nativeFast.names, "accelerated", nativeFast.accelerated, "visiblePixels", visiblePixels(nativeFast), "state", nativeFast.state));
            write("hologram-native-reference.png", nativeReference); write("hologram-native-fast.png", nativeFast);
            require(visiblePixels(nativeReference) > 200, "Native reference did not draw visible hologram names");
            equal(nativeReference, nativeFast, "native");
            checks.put("nativePixelsAndNamesEquivalent", true);
            Image movedReference = image(stands, false, false, false, 1), movedFast = image(stands, true, false, false, 1);
            equal(movedReference, movedFast, "moved camera and current names");
            require(!Arrays.equals(nativeReference.pixels, movedReference.pixels), "The frame update fixture did not change names, positions or camera");
            checks.put("currentNamePositionAndCameraPreserved", true);
            image(stands, false, true, false, 0);
            Image batchedReference = image(stands, false, true, false, 0), batchedFast = image(stands, true, true, false, 0);
            write("hologram-batched-reference.png", batchedReference); write("hologram-batched-fast.png", batchedFast);
            equal(batchedReference, batchedFast, "Argentum name batching");
            checks.put("argentumPixelsAndNamesEquivalent", true);
            image(stands, false, true, true, 0);
            long packets = GpuNameTags.packetsDrawn;
            Image gpuReference = image(stands, false, true, true, 0), gpuFast = image(stands, true, true, true, 0);
            write("hologram-gpu-reference.png", gpuReference); write("hologram-gpu-fast.png", gpuFast);
            equal(gpuReference, gpuFast, "GPU name tags");
            require(GpuNameTags.packetsDrawn > packets, "GPU name-tag mode did not execute production GPU draws");
            checks.put("gpuNamePixelsAndNamesEquivalent", true);
            checks.put("bodyCallsBefore", nativeReference.bodies); checks.put("bodyCallsAfter", nativeFast.bodies);
            checks.put("nameCallsPerScene", nativeFast.names); checks.put("acceleratedPerScene", nativeFast.accelerated);
            checks.put("gpuPacketsDrawn", GpuNameTags.packetsDrawn - packets);
            checks.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); checks.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after native hologram equivalence checks");
            return checks;
        } finally {
            verifying = false;
            if (batch != null && end != null && batch.getClass().getMethod("active").invoke(null) == batch) end.invoke(batch);
            BetaOptimizationsConfig.setFastInvisibleHolograms(oldFast); BetaOptimizationsConfig.setGpuNameTags(oldGpu);
            BetaOptimizationsConfig.setRetainedNameTagScenes(oldRetained); BetaOptimizationsConfig.setMinimumGpuLabels(oldMinimum);
            BetaOptimizationsConfig.setCompactNameTagPreparation(oldCompact);
            dispatcher.setSolidRender(false); dispatcher.cameraYaw = yaw; dispatcher.cameraPitch = pitch;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            if (depth != 0) GL30.glDeleteRenderbuffers(depth); if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
            if (color != 0) GlStateManager.deleteTextures(color); depth = framebuffer = color = 0;
            GL20.glUseProgram(previousProgram); GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }
    }

    private static List<ArmorStandEntity> fixtures() {
        Minecraft mc = Minecraft.getInstance(); List<ArmorStandEntity> stands = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            ArmorStandEntity stand = new ArmorStandEntity(mc.world, mc.player.x, mc.player.y, mc.player.z + 8);
            stand.setInvisible(true); stand.setCustomNameVisible(true); stand.setCustomName("§bHologram " + i + " §f250✫");
            if (i == 1) { stand.width = 0; stand.height = 0; }
            if (i == 2) { NbtCompound nbt = new NbtCompound(); nbt.putBoolean("Small", true); nbt.putBoolean("Invisible", true); stand.readCustomNbt(nbt); stand.width = .25f; stand.height = .9875f; }
            if (i == 3) stand.setSneaking(true);
            if (i == 4) stand.setPosition(mc.player.x, mc.player.y, mc.player.z + 160);
            stands.add(stand);
        }
        return stands;
    }

    private static Image image(List<ArmorStandEntity> stands, boolean fast, boolean batched, boolean gpu, int change) throws Exception {
        BetaOptimizationsConfig.setFastInvisibleHolograms(fast); BetaOptimizationsConfig.setGpuNameTags(gpu);
        GL20.glUseProgram(0); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager.viewport(0, 0, WIDTH, HEIGHT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity();
        if (RETAINED_TEXT) GlStateManager.ortho(-10, 10, -5.625, 5.625, -100, 100);
        else GlStateManager.ortho(-8, 8, -4.5, 4.5, -100, 100);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity(); GlStateManager.rotatef(180, 0, 1, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GlStateManager.colorMask(true, true, true, true);
        GlStateManager.clearColor(.15f, .18f, .22f, 1); GlStateManager.clearDepth(1); GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepthTest(); GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.enableAlphaTest(); GlStateManager.alphaFunc(GL11.GL_GREATER, .1f);
        GlStateManager.disableLighting(); GlStateManager.disableFog(); GlStateManager.disableBlend(); GlStateManager.enableCull(); GlStateManager.disableRescaleNormal();
        Minecraft.getInstance().gameRenderer.enableLightMap();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1); GlStateManager.enableTexture(); GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240, 240);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0); GlStateManager.enableTexture(); GlStateManager.color4f(1, 1, 1, 1);
        dispatcher.cameraYaw = change == 0 ? 0 : 17; dispatcher.cameraPitch = change == 0 ? 0 : -9;
        if (batched) {
            begin.invoke(batch, new Matrix4f().rotateY((float) Math.PI));
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240, 240);
        }
        long before = FastInvisibleHolograms.accelerated; bodies = names = 0; verifying = true;
        try {
            for (int i = 0; i < stands.size(); i++) {
                ArmorStandEntity stand = stands.get(i);
                String label = RETAINED_TEXT ? textPattern != null ? textPattern.replace("%i%", Integer.toString(i))
                    : (change == 0 ? "§6[250✫] §b" : "§d[500✫] §a") + "ArcadiumTest " + i + "➜玩家§f:3.7"
                    : (change == 0 ? "§bHologram " : "§aUpdated ") + i + " §f250✫";
                stand.setCustomName(label);
                double x = (RETAINED_TEXT ? -7.5 + i % 4 * 5 : -6 + i % 4 * 4) + change * .17, y = -4 + i / 4 * 1.8, z = change * .13;
                require(dispatcher.render(stand, x, y, z, 0, 0, false), "Native dispatcher rejected a fixture stand");
            }
        } finally { verifying = false; if (batched) end.invoke(batch); }
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4); GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[pixels.capacity()]; pixels.get(rgba);
        List<Integer> state = List.of(GL11.glIsEnabled(GL11.GL_ALPHA_TEST) ? 1 : 0, GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0,
            GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL) ? 1 : 0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),
            GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        return new Image(rgba, bodies, names, FastInvisibleHolograms.accelerated - before, state);
    }

    private static void equal(Image original, Image accelerated, String mode) {
        int different = 0; for (int i = 0; i < original.pixels.length; i++) if (original.pixels[i] != accelerated.pixels[i]) different++;
        require(different == 0, mode + " pixel bytes differ: " + different);
        require(original.names == accelerated.names && original.names == COUNT, mode + " changed the original virtual name-tag calls");
        require(original.bodies == COUNT && accelerated.bodies == 0 && accelerated.accelerated == COUNT,
            mode + " did not bypass every empty invisible body: " + original.bodies + "/" + accelerated.bodies + "/" + accelerated.accelerated + " | " + FastInvisibleHolograms.status());
        require(original.state.equals(accelerated.state), mode + " changed the native caller GL state");
    }

    private static int visiblePixels(Image image) {
        int count = 0; for (int i = 4; i < image.pixels.length; i += 4)
            if (image.pixels[i] != image.pixels[0] || image.pixels[i + 1] != image.pixels[1] || image.pixels[i + 2] != image.pixels[2]) count++;
        return count;
    }

    private static Map<String, Object> retainedText(List<ArmorStandEntity> stands) throws Exception {
        boolean oldEmoji = EmojiText.enabled(), oldNick = NickHiderConfig.getEnabled();
        String oldNickname = NickHiderConfig.getNickname();
        try {
            EmojiText.setEnabled(true); NickHiderConfig.setEnabled(false);
            if(COMPACT_PREPARATION)BetaOptimizationsConfig.setCompactNameTagPreparation(false);
            BetaOptimizationsConfig.setRetainedNameTagScenes(false);
            image(stands, true, true, true, 0);
            Image reference = image(stands, true, true, true, 0);
            require(visiblePixels(reference) > 200, "Native Unicode reference did not draw labels");
            if(COMPACT_PREPARATION)BetaOptimizationsConfig.setCompactNameTagPreparation(true);
            RetainedNameTags.invalidate(); BetaOptimizationsConfig.setRetainedNameTagScenes(true);
            long built = RetainedNameTags.built;
            image(stands, true, true, true, 0);
            require(RetainedNameTags.built > built, "Native star/arrow/CJK/colon plans were not recorded: " + RetainedNameTags.status());
            long reused = RetainedNameTags.reused, builtAfter = RetainedNameTags.built;
            Image replayed = image(stands, true, true, true, 0);
            write("retained-unicode-reference.png", reference); write("retained-unicode-replay.png", replayed);
            sameScene(reference, replayed, "native Unicode retained replay");
            require(RetainedNameTags.reused > reused && RetainedNameTags.built == builtAfter, "Stable Unicode labels rebuilt instead of reusing plans");
            report.put("nativeUnicodeAndColonRetained", true);
            report.put("stableFrameReuses", RetainedNameTags.reused - reused);
            if(COMPACT_PREPARATION){
                long cached=GpuNameTags.commandsReused;
                sameScene(replayed,image(stands,true,true,true,0),"compact MDI command replay");
                require(GpuNameTags.commandsReused>cached,"Unchanged native MDI commands were uploaded again");
                report.put("compactPreparationPixelsAndOrder",true);
                report.put("cachedNativeMdiPasses",GpuNameTags.commandsReused-cached);
            }

            BetaOptimizationsConfig.setRetainedNameTagScenes(false);
            Image moved = image(stands, true, true, true, 1);
            BetaOptimizationsConfig.setRetainedNameTagScenes(true);
            image(stands, true, true, true, 1);
            Image movedReplay = image(stands, true, true, true, 1);
            write("retained-updated-reference.png", moved); write("retained-updated-replay.png", movedReplay);
            sameScene(moved, movedReplay, "same-frame changed names/colors/camera");
            require(!Arrays.equals(reference.pixels, moved.pixels), "The live name/color/camera fixture did not change");
            report.put("currentNamesColorsAndCamera", true);

            NickHiderConfig.setEnabled(true); NickHiderConfig.setNickname("AliasOne");
            Image aliasOne = retainedReferenceAndReplay(stands, "AliasOne");
            NickHiderConfig.setNickname("AliasTwo");
            Image aliasTwo = retainedReferenceAndReplay(stands, "AliasTwo");
            require(!Arrays.equals(aliasOne.pixels, aliasTwo.pixels), "The actual NickHider change did not reach the rendered labels");
            report.put("liveNickHiderAliasChange", true);
            NickHiderConfig.setEnabled(false);

            GpuNameTags.invalidateAll(); built = RetainedNameTags.built;
            image(stands, true, true, true, 0);
            require(RetainedNameTags.built > built, "Resource generation invalidation did not rebuild Unicode plans");
            Image generationReplay = image(stands, true, true, true, 0);
            sameScene(reference, generationReplay, "resource generation rebuild");
            report.put("resourceGenerationInvalidation", true);
            report.put("customFontFallback", optionFallback(stands, "org.polyfrost.oneconfig.internal.OneConfigConfig", "useInterInGame"));
            report.put("customBackgroundFallback", optionFallback(stands, "org.polyfrost.polynametag.client.PolyNametagConfig", "rounded"));

            textPattern = "§fNPC%i% :smile: 😀" + (char) EmojiText.FIRST_GLYPH;
            BetaOptimizationsConfig.setRetainedNameTagScenes(false);
            Image emojiReference = image(stands, true, true, true, 0);
            BetaOptimizationsConfig.setRetainedNameTagScenes(true);
            reused = RetainedNameTags.reused; built = RetainedNameTags.built; long rejected = RetainedNameTags.emojiRejected;
            Image emojiFallback = image(stands, true, true, true, 0);
            sameScene(emojiReference, emojiFallback, "actual emoji fallback");
            require(RetainedNameTags.reused == reused && RetainedNameTags.built == built && RetainedNameTags.emojiRejected > rejected,
                "An actual emoji alias/Unicode/atlas glyph was retained");
            report.put("actualEmojiFallback", true);

            textPattern = "§KHidden%i%§r"; long animated = RetainedNameTags.animatedRejected;
            image(stands, true, true, true, 0); image(stands, true, true, true, 0);
            require(RetainedNameTags.animatedRejected > animated && RetainedNameTags.reused == reused, "Uppercase obfuscated text was retained");
            report.put("obfuscationFallback", true);

            textPattern = "\u2063Arcadium\u2064 %i%"; animated = RetainedNameTags.animatedRejected;
            image(stands, true, true, true, 0); image(stands, true, true, true, 0);
            require(RetainedNameTags.animatedRejected > animated && RetainedNameTags.reused == reused, "The animated Arcadium prefix was retained");
            report.put("animatedPrefixFallback", true);

            textPattern = "§fNPC%i% :smile: 😀"; EmojiText.setEnabled(false);
            long dormant = RetainedNameTags.dormantTextRejected; built = RetainedNameTags.built;
            image(stands, true, true, true, 0); EmojiText.setEnabled(true); image(stands, true, true, true, 0);
            require(RetainedNameTags.dormantTextRejected > dormant && RetainedNameTags.built == built && RetainedNameTags.reused == reused,
                "Disabled emoji seeded a stale plan across its enable transition");
            report.put("emojiEnableTransitionFallback", true);
            report.put("virtualNameCallsPerScene", COUNT);
            report.put("gpuPacketsDrawn", GpuNameTags.packetsDrawn);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after retained native text proof");
            return report;
        } finally {
            textPattern = null; EmojiText.setEnabled(oldEmoji);
            NickHiderConfig.setEnabled(oldNick); NickHiderConfig.setNickname(oldNickname);
            RetainedNameTags.invalidate();
        }
    }

    private static Image retainedReferenceAndReplay(List<ArmorStandEntity> stands, String context) throws Exception {
        BetaOptimizationsConfig.setRetainedNameTagScenes(false);
        Image reference = image(stands, true, true, true, 0);
        BetaOptimizationsConfig.setRetainedNameTagScenes(true);
        image(stands, true, true, true, 0);
        Image replay = image(stands, true, true, true, 0);
        sameScene(reference, replay, "actual NickHider " + context);
        return replay;
    }

    private static boolean optionFallback(List<ArmorStandEntity> stands, String owner, String option) throws Exception {
        BetaOptimizationsConfig.setRetainedNameTagScenes(true);
        image(stands, true, true, true, 0); image(stands, true, true, true, 0);
        Field field = Class.forName(owner).getDeclaredField(option); field.setAccessible(true);
        Object old = field.get(null);
        try {
            begin.invoke(batch, new Matrix4f().rotateY((float) Math.PI));
            String name = stands.get(0).getDisplayName().getFormattedString();
            require(RetainedNameTags.begin(stands.get(0), name, -7.5, -4, 0), "Option fallback control had no reusable Unicode plan: " + option);
            end.invoke(batch);
            field.set(null, true); begin.invoke(batch, new Matrix4f().rotateY((float) Math.PI));
            require(!RetainedNameTags.begin(stands.get(0), name, -7.5, -4, 0), "Custom rendering option reused stale Unicode geometry: " + option);
            end.invoke(batch); return true;
        } finally { field.set(null, old); }
    }

    private static void sameScene(Image reference, Image replay, String context) {
        int different = 0; for (int i = 0; i < reference.pixels.length; i++) if (reference.pixels[i] != replay.pixels[i]) different++;
        require(different == 0, context + " pixel bytes differ: " + different);
        require(reference.names == COUNT && replay.names == COUNT && reference.names == replay.names, context + " changed virtual name calls");
        require(reference.state.equals(replay.state), context + " changed caller GL state");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> fallbacks(ArmorStandEntity stand) throws Exception {
        Minecraft mc = Minecraft.getInstance(); ArmorStandRenderer renderer = (ArmorStandRenderer) (Object) dispatcher.getRenderer(stand);
        require(FastInvisibleHolograms.eligible(renderer, stand, mc.player), "The actual vanilla renderer was not eligible: " + FastInvisibleHolograms.status());
        List<String> checked = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) { stand.setEquipment(slot, new ItemStack(Items.DIAMOND)); require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Equipped slot accelerated: " + slot); stand.setEquipment(slot, null); checked.add("equipment-" + slot); }
        stand.setInvisible(false); require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Visible armor stand accelerated"); stand.setInvisible(true); checked.add("visible");
        var spectator = new RemoteClientPlayerEntity(mc.world, new GameProfile(new UUID(0, 65065), "Spectator065")) { @Override public boolean isSpectator() { return true; } };
        require(!stand.isInvisibleTo(spectator) && !FastInvisibleHolograms.eligible(renderer, stand, spectator), "Spectator-visible armor stand accelerated"); checked.add("spectator-visible");
        renderer.setSolidRender(true); require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Outline renderer accelerated"); renderer.setSolidRender(false); checked.add("outline");
        stand.damagedTimer = 1; require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Hurt overlay accelerated"); stand.damagedTimer = 0; checked.add("hurt");
        stand.deathTicks = 1; require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Death overlay accelerated"); stand.deathTicks = 0; checked.add("death");
        stand.setCustomNameVisible(false); require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Hidden custom name accelerated"); stand.setCustomNameVisible(true); checked.add("hidden-name");
        stand.setOnFireFor(3); require(stand.shouldRenderOnFire() && !FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Burning armor stand accelerated"); stand.extinguish(); checked.add("fire");
        ArmorStandEntity custom = new ArmorStandEntity(mc.world) { @Override public ItemStack[] getEquipment() { throw new AssertionError("Custom entity was inspected"); } };
        require(!FastInvisibleHolograms.eligible(renderer, custom, mc.player), "Custom entity accelerated"); checked.add("custom-entity");
        ArmorStandRenderer customRenderer = new ArmorStandRenderer(dispatcher) { };
        require(!FastInvisibleHolograms.eligible(customRenderer, stand, mc.player), "Custom renderer accelerated"); checked.add("custom-renderer");
        Field model = Arrays.stream(net.minecraft.client.render.entity.LivingEntityRenderer.class.getDeclaredFields()).filter(field -> field.getType() == Model.class).findFirst().orElseThrow(); model.setAccessible(true);
        Object oldModel = model.get(renderer); model.set(renderer, new ArmorStandModel() { });
        require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Custom model accelerated"); model.set(renderer, oldModel); checked.add("custom-model");
        List layers = ((LivingPipelineAccess) renderer).arcadium$layers(); Object removed = layers.remove(2);
        require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Nonstandard layer list accelerated"); layers.add(removed); checked.add("changed-layers");
        stand.height = Float.NaN; require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Non-finite dimensions accelerated"); stand.height = 1.975f; checked.add("malformed-dimensions");
        Field equipment = Arrays.stream(ArmorStandEntity.class.getDeclaredFields()).filter(field -> field.getType() == ItemStack[].class).findFirst().orElseThrow(); equipment.setAccessible(true);
        Object originalEquipment = equipment.get(stand); equipment.set(stand, new ItemStack[0]);
        require(!FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Malformed equipment accelerated"); equipment.set(stand, originalEquipment); checked.add("malformed-equipment");
        require(FastInvisibleHolograms.eligible(renderer, stand, mc.player), "Restored renderer did not recover eligibility");
        return checked;
    }

    private static void write(String name, Image image) throws Exception {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) { int i = ((HEIGHT - y - 1) * WIDTH + x) * 4;
            result.setRGB(x, y, (image.pixels[i + 3] & 255) << 24 | (image.pixels[i] & 255) << 16 | (image.pixels[i + 1] & 255) << 8 | image.pixels[i + 2] & 255); }
        ImageIO.write(result, "png", output().resolveSibling(name).toFile());
    }
    private static Path output() { return Path.of(System.getProperty("arcadium.holograms065.output")); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true; report.put("passed", failure == null); report.put("phase", phase); report.put("syntheticOfflineFixture", true);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Files.createDirectories(output().toAbsolutePath().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
