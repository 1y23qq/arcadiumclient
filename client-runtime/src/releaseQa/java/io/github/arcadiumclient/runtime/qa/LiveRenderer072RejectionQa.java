// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class LiveRenderer072RejectionQa {
    private static final boolean ENABLED = System.getProperty("arcadium.rendererRejection072.output") != null;
    private static final String[] CASES = {"gui", "numeric", "focus"};
    private static final String[] EXPECTED = {"A GUI opened", "A numeric/boolean setting changed", "The native window lost foreground focus"};
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static int phase, ticks, age, selected, oldMinimum;
    private static Path current;
    private static JFrame focusTarget;
    private static boolean pendingStop, stopped, minimumCaptured;
    private static String failure;

    private LiveRenderer072RejectionQa() {}
    public static boolean enabled() { return ENABLED; }
    private static Path output() { return Path.of(System.getProperty("arcadium.rendererRejection072.output")); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    public static void tick() {
        if (!ENABLED || pendingStop || stopped) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Only the owned offline fixture is allowed");
            require("9".equals(System.getProperty("socksProxyPort")), "The fixture must block external network access");
            require(System.nanoTime() - START < 240_000_000_000L && ++ticks < 7000, "Native rejection fixture timed out");
            age++;
            if (phase == 0 && ticks > 65 && mc.screen != null) {
                ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false;
                mc.options.vsync = false; Display.setVSyncEnabled(false);
                mc.options.fpsLimit = 260; mc.options.viewDistance = 8;
                OneConfigConfig.limitUnfocusedFps = false;
                oldMinimum = ((Number) BetaOptimizationsConfig.INSTANCE.getTree().getProp("minimumPlayers").get()).intValue();
                minimumCaptured = true;
                mc.startGame("arcadium-renderer-rejection072", "Offline sampler validation", new WorldSettings(720721, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; age = 0; progress("waiting_for_owned_world");
            } else if (phase == 1 && mc.world != null && mc.player != null && age > 60) {
                require(mc.isIntegratedServerRunning() && mc.getServer() != null, "An owned integrated server is required");
                mc.player.abilities.flying = true;
                mc.player.updatePositionAndAngles(0, 12, -10, 0, 0);
                mc.player.prevX = mc.player.lastX = mc.player.x;
                mc.player.prevY = mc.player.lastY = mc.player.y;
                mc.player.prevZ = mc.player.lastZ = mc.player.z;
                mc.player.lastYaw = mc.player.yaw;
                mc.player.lastPitch = mc.player.pitch;
                mc.player.velocityX = mc.player.velocityY = mc.player.velocityZ = 0;
                var board = mc.world.getScoreboard();
                var objective = board.createObjective("rejection072", ScoreboardCriterion.DUMMY);
                objective.setDisplayName("BED WARS"); board.setDisplayObjective(1, objective);
                mc.setCurrentServerEntry(new ServerListEntry("Offline sampler validation", "mc.hypixel.net", false));
                mc.openScreen(null); phase = 2; age = 0; progress("settling_owned_scene");
            } else if (phase == 2 && age > 40 && Display.isActive() && mc.screen == null) {
                arm(); phase = 3; age = 0;
            } else if (phase == 3 && age > 5) {
                require(LiveRenderer072Qa.running(), "The sampler did not enter its native warmup");
                if (selected == 0) mc.openScreen(new ValidationScreen());
                else if (selected == 1) BetaOptimizationsConfig.INSTANCE.getTree().getProp("minimumPlayers").setAs(oldMinimum == 1 ? 2 : 1);
                else {
                    SwingUtilities.invokeAndWait(() -> {
                        focusTarget = new JFrame("Arcadium Offline Sampler Focus Target");
                        focusTarget.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                        focusTarget.setSize(360, 100); focusTarget.setLocation(40, 40);
                        focusTarget.setFocusableWindowState(false); focusTarget.setVisible(true);
                        focusTarget.setFocusableWindowState(true);
                    });
                }
                phase = 4; age = 0; progress(selected == 2 ? "awaiting_actual_focus_loss" : "awaiting_native_rejection");
            } else if (phase == 4 && !LiveRenderer072Qa.running()) {
                verify();
                mc.openScreen(null);
                BetaOptimizationsConfig.INSTANCE.getTree().getProp("minimumPlayers").setAs(oldMinimum);
                if (selected == 2 && !Display.isActive()) { phase = 5; progress("awaiting_game_focus_restore"); }
                else next();
            } else if (phase == 5 && Display.isActive()) {
                if (focusTarget != null) SwingUtilities.invokeAndWait(() -> focusTarget.dispose());
                next();
            }
            LiveRenderer072Qa.tick(true);
        } catch (Throwable error) {
            failure = error.getClass().getName() + ": " + error.getMessage();
            pendingStop = true;
        }
    }

    private static void arm() throws Exception {
        current = output().toAbsolutePath().getParent().resolve("case-" + CASES[selected]).resolve("status.json");
        Files.createDirectories(current.getParent());
        JsonObject control = new JsonObject(); control.addProperty("requireHealthyGpu", false);
        LiveRenderer072Qa.arm(current, true, false, control);
        require(LiveRenderer072Qa.running(), "Native sampler setup failed");
        progress("armed_" + CASES[selected]);
    }

    private static void verify() throws Exception {
        JsonObject raw = JsonParser.parseString(Files.readString(current)).getAsJsonObject();
        require(raw.has("passed") && !raw.get("passed").getAsBoolean(), "An invalid native capture was accepted");
        require(raw.has("failure") && EXPECTED[selected].equals(raw.get("failure").getAsString()), "Unexpected native rejection reason");
        require(raw.get("includedFrames").getAsInt() == 0 && !raw.has("performance"), "Warmup cancellation must not publish FPS values");
        results.add(Map.of("case", CASES[selected], "expectedFailure", EXPECTED[selected], "actualFailure", raw.get("failure").getAsString(), "nativeCaptureAccepted", false,
                "includedFrames", 0, "report", current.getParent().getFileName().toString() + "/status.json"));
    }

    private static void next() throws Exception {
        selected++;
        if (selected == CASES.length) { pendingStop = true; progress("all_rejections_verified"); }
        else { phase = 2; age = 0; progress("restored_before_" + CASES[selected]); }
    }

    public static void head() { if (ENABLED && !stopped) LiveRenderer072Qa.head(true); }
    public static void frame() {
        if (!ENABLED || stopped) return;
        LiveRenderer072Qa.tail(true);
        if (!pendingStop) return;
        stopped = true;
        try {
            if (focusTarget != null) SwingUtilities.invokeAndWait(() -> focusTarget.dispose());
            if (minimumCaptured) BetaOptimizationsConfig.INSTANCE.getTree().getProp("minimumPlayers").setAs(oldMinimum);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("passed", failure == null && results.size() == CASES.length);
            report.put("failure", failure); report.put("syntheticOffline", true); report.put("correctnessOnly", true);
            report.put("networkBlocked", true); report.put("sceneIsRealHypixel", false);
            report.put("serverMetadata", "A synthetic local scoreboard/server label satisfies only the sampler's unrelated scene guard; no external server is connected.");
            report.put("cases", results); report.put("focusRestored", Display.isActive());
            report.put("minimumRestored", !minimumCaptured || ((Number) BetaOptimizationsConfig.INSTANCE.getTree().getProp("minimumPlayers").get()).intValue() == oldMinimum);
            Files.createDirectories(output().toAbsolutePath().getParent());
            Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable ignored) { }
        Minecraft.getInstance().stop();
    }

    private static void progress(String stage) throws Exception {
        Files.createDirectories(output().toAbsolutePath().getParent());
        Files.writeString(output().resolveSibling("progress.json"), new GsonBuilder().create().toJson(Map.of("stage", stage, "caseIndex", selected, "syntheticOffline", true, "correctnessOnly", true)));
    }

    private static final class ValidationScreen extends Screen {
        @Override public void render(int mouseX, int mouseY, float delta) { renderBackground(); }
    }
}
