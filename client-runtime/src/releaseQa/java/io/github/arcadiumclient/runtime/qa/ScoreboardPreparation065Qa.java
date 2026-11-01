// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlayConfig;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.client.optimizations.ScoreboardPreparation;
import io.github.arcadiumclient.runtime.qa.mixin.Scoreboard060GuiAccess;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.Team;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ScoreboardPreparation065Qa {
    private record Image(byte[] pixels, int sorts, int repeating, int consecutive, int rows, long snapshots, long decisions) { }
    private static final Map<String,Object> report = new LinkedHashMap<>();
    private static boolean done, measuring, mutated;
    private static int ticks, sorts, repeating, consecutive, rows, mutation, framebuffer, texture, width, height;
    private static Scoreboard board;
    private static ScoreboardObjective objective;
    private static Team team;
    private static Object hud;

    private ScoreboardPreparation065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.scoreboardPreparation065.output") != null; }
    public static void noteSort() { if (measuring) sorts++; }
    public static void noteRepeating() { if (measuring) repeating++; }
    public static void noteConsecutive() { if (measuring) consecutive++; }
    public static void row() {
        if (!measuring || ++rows != 8 || mutation == 0) return;
        try {
            mutated = true;
            if (mutation == 1) { board.getScore("Line 04", objective).set(73); objective.setDisplayName("§dUpdated title"); }
            if (mutation == 2) set("ScoreboardPoints", int.class, 2);
            if (mutation == 3) team.setPrefix("§e[NEW] ");
            if (mutation == 4) hud.getClass().getMethod("getScorePointsColor").invoke(hud).getClass().getMethod("setArgb", int.class)
                    .invoke(hud.getClass().getMethod("getScorePointsColor").invoke(hud), 0xFF55FF55);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static void tick() {
        if (done || ++ticks < 80) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline fixture");
            require(mc.world == null, "Scoreboard preparation QA must remain offline without a world");
            ClientPreferences.setSetupComplete(true); mc.openScreen(null);
            report.putAll(verify()); finish(null);
        } catch (Throwable error) { finish(error); }
    }

    private static Map<String,Object> verify() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        boolean old = BetaOptimizationsConfig.getRetainedScoreboardPreparation(), retainedHud = BetaOptimizationsConfig.getRetainedHudBatches();
        boolean prefix = BedwarsOverlayConfig.INSTANCE.animatedPrefix;
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer viewport = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        var huds = Class.forName("org.polyfrost.vanillahud.hud.Huds");
        hud = huds.getMethod("getScoreboard").invoke(huds.getField("INSTANCE").get(null));
        int oldPoints = (int) hud.getClass().getMethod("getScoreboardPoints").invoke(hud);
        boolean oldRepeating = (boolean) hud.getClass().getMethod("getHideRepeatingScores").invoke(hud);
        Window window = new Window(mc); width = mc.width; height = mc.height;
        List<Map<String,Object>> cases = new ArrayList<>();
        try {
            BedwarsOverlayConfig.INSTANCE.animatedPrefix = false;
            framebuffer = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            texture = GL11.glGenTextures(); GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Scoreboard framebuffer incomplete");
            for (int mode = 0; mode < 2; mode++) {
                BetaOptimizationsConfig.setRetainedHudBatches(mode == 1);
                for (int scenario = 0; scenario < 8; scenario++) {
                    reset(scenario); image(window, false, scenario);
                    reset(scenario); Image reference = image(window, false, scenario);
                    reset(scenario); Image accelerated = image(window, true, scenario);
                    require(Arrays.equals(reference.pixels, accelerated.pixels), "Scoreboard pixels changed in scenario " + scenario + "/HUD" + mode);
                    require(reference.rows == accelerated.rows && accelerated.rows == 15, "Scoreboard row count/order changed");
                    require(visible(reference.pixels) > 200, "Native scoreboard reference is blank");
                    require(reference.sorts >= 31, "Native repeated score sorting fixture did not execute: " + reference.sorts);
                    int allowed = scenario == 3 || scenario == 5 ? 2 : 1;
                    require(accelerated.sorts == allowed && accelerated.snapshots == allowed, "Score snapshot was not shared or invalidated: " + accelerated.sorts + "/" + accelerated.snapshots);
                    require(accelerated.repeating <= 2 && accelerated.consecutive <= 2 && accelerated.decisions <= 2,
                            "Points decision repeated per row: " + accelerated.repeating + "/" + accelerated.consecutive + "/" + accelerated.decisions);
                    require(accelerated.repeating < reference.repeating, "Native points visibility work was not removed");
                    cases.add(Map.of("hudRetained", mode == 1, "scenario", scenario, "nativeSorts", reference.sorts, "cachedSorts", accelerated.sorts,
                            "nativeRepeatingChecks", reference.repeating, "cachedRepeatingChecks", accelerated.repeating, "cachedVisibilityDecisions", accelerated.decisions,
                            "rows", accelerated.rows, "pixelExact", true));
                    if (mode == 0 && scenario == 0) { write("scoreboard-reference.png", reference.pixels); write("scoreboard-cached.png", accelerated.pixels); }
                }
            }
            reset(1);
            BetaOptimizationsConfig.setRetainedScoreboardPreparation(true);
            var previous = ScoreboardPreparation.begin(objective, true);
            try {
                var first = board.getScores(objective); first.clear();
                require(board.getScores(objective).size() == 17, "A mutable caller view corrupted the shared score snapshot");
            } finally { ScoreboardPreparation.end(previous); }
            report.put("freshMutableCollectionViews", true);
            reset(0); BedwarsOverlayConfig.INSTANCE.animatedPrefix = true;
            objective.setDisplayName(io.github.arcadiumclient.runtime.client.bedwars.ArcadiumPrefix.MARKED);
            Image animated0 = image(window, true, 0); Thread.sleep(35); Image animated1 = image(window, true, 0);
            require(!Arrays.equals(animated0.pixels, animated1.pixels), "Animated prefix color was frozen by preparation reuse");
            report.put("prefixStillAnimatesEachFrame", true);
            report.put("cases", cases); report.put("nativeDrawsPreserved", true); report.put("midFrameScoreTeamConfigAndColorChanges", true);
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after scoreboard preparation QA");
            return report;
        } finally {
            measuring = false; NickHiderConfig.setEnabled(false);
            set("ScoreboardPoints", int.class, oldPoints); set("HideRepeatingScores", boolean.class, oldRepeating);
            BetaOptimizationsConfig.setRetainedScoreboardPreparation(old); BetaOptimizationsConfig.setRetainedHudBatches(retainedHud);
            BedwarsOverlayConfig.INSTANCE.animatedPrefix = prefix;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer); GL20.glUseProgram(previousProgram);
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer); if (texture != 0) GlStateManager.deleteTextures(texture);
        }
    }

    private static void reset(int scenario) throws Exception {
        board = new Scoreboard(); objective = board.createObjective("scope065", ScoreboardCriterion.DUMMY);
        objective.setDisplayName("§bPreparation :smile:");
        team = board.addTeam("scope-team"); team.setPrefix("§a");
        for (int i = 0; i < 15; i++) { String name = "Line " + String.format(java.util.Locale.ROOT, "%02d", i); board.getScore(name, objective).set(scenario == 2 ? 7 : i + 1); board.addMemberToTeam(name, team.getName()); }
        board.getScore("#hidden-a", objective).set(scenario == 2 ? 7 : 16); board.getScore("#hidden-b", objective).set(scenario == 2 ? 7 : 17);
        set("ScoreboardPoints", int.class, scenario == 1 || scenario == 6 ? 2 : 1); set("HideRepeatingScores", boolean.class, true);
        Object color = hud.getClass().getMethod("getScorePointsColor").invoke(hud); color.getClass().getMethod("setArgb", int.class).invoke(color, 0xFFFF5555);
        NickHiderConfig.setEnabled(scenario == 7); NickHiderConfig.setNickname("ScopeAlias");
        if (scenario == 7) { board.getScore("ArcadiumTest", objective).set(100); board.removeScore("Line 00", objective); }
    }

    private static Image image(Window window, boolean enabled, int scenario) throws Exception {
        BetaOptimizationsConfig.setRetainedScoreboardPreparation(enabled);
        mutation = switch (scenario) { case 3 -> 1; case 4 -> 2; case 5 -> 3; case 6 -> 4; default -> 0; };
        GL20.glUseProgram(0); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer); GlStateManager.viewport(0, 0, width, height);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity(); GlStateManager.ortho(0, window.getWidth(), window.getHeight(), 0, 1000, 3000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity(); GlStateManager.translatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GlStateManager.colorMask(true, true, true, true);
        GlStateManager.clearColor(.15f, .18f, .22f, 1); GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.disableDepthTest(); GlStateManager.disableLighting(); GlStateManager.disableFog(); GlStateManager.disableCull();
        GlStateManager.enableTexture(); GlStateManager.enableAlphaTest(); GlStateManager.alphaFunc(GL11.GL_GREATER, .1f); GlStateManager.color4f(1, 1, 1, 1);
        sorts = repeating = consecutive = rows = 0; mutated = false;
        long snapshots = ScoreboardPreparation.snapshots, decisions = ScoreboardPreparation.visibilityComputations;
        measuring = true;
        try { ((Scoreboard060GuiAccess) Minecraft.getInstance().gui).release$scoreboard(objective, window); }
        finally { measuring = false; }
        if (mutation != 0) require(mutated, "Mid-frame mutation hook did not run");
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4); GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.capacity()]; pixels.get(data);
        return new Image(data, sorts, repeating, consecutive, rows, ScoreboardPreparation.snapshots - snapshots, ScoreboardPreparation.visibilityComputations - decisions);
    }

    private static void set(String name, Class<?> type, Object value) throws Exception { hud.getClass().getMethod("set" + name, type).invoke(hud, value); }
    private static int visible(byte[] pixels) { int count = 0; for (int i = 4; i < pixels.length; i += 4) if (pixels[i] != pixels[0] || pixels[i + 1] != pixels[1] || pixels[i + 2] != pixels[2]) count++; return count; }
    private static void write(String name, byte[] data) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) { int i = ((height - 1 - y) * width + x) * 4; image.setRGB(x, y, (data[i + 3] & 255) << 24 | (data[i] & 255) << 16 | (data[i + 1] & 255) << 8 | data[i + 2] & 255); }
        ImageIO.write(image, "PNG", output().resolveSibling(name).toFile());
    }
    private static Path output() { return Path.of(System.getProperty("arcadium.scoreboardPreparation065.output")); }
    private static void finish(Throwable error) {
        done = true; report.put("passed", error == null); report.put("syntheticOfflineFixture", true);
        if (error != null) { report.put("error", error.toString()); error.printStackTrace(); }
        try { Files.createDirectories(output().toAbsolutePath().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
