package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.qa.mixin.Scoreboard060GuiAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.text.LiteralText;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class Scoreboard060Qa {
    public static boolean measureProbe;
    public static final List<Boolean> widths = new ArrayList<>();
    private static int ticks, phase, frames;
    private static boolean done;
    private static ScoreboardObjective objective;
    private static final List<Object> cases = new ArrayList<>();
    public static boolean enabled() { return System.getProperty("arcadium.scoreboard060.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            require(++ticks < 1600, "Scoreboard QA timed out");
            OneConfigConfig.limitUnfocusedFps = false;
            if (phase == 0 && ticks > 60) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
                ClientPreferences.setSetupComplete(true);
                var screen = new ArcadiumServersScreen(new TitleScreen()); mc.openScreen(screen);
                var connect = ArcadiumServersScreen.class.getDeclaredMethod("connect", ServerListEntry.class); connect.setAccessible(true);
                connect.invoke(screen, new ServerListEntry("Scoreboard QA", System.getProperty("arcadium.scoreboard060.server"), false)); phase = 1;
            } else if (phase == 1 && mc.world != null && mc.player != null && mc.screen == null) {
                require(ChatTweaksConfig.getHideEmojiInScoreboard(), "New users did not get scoreboard emoji disabled");
                var board = mc.world.getScoreboard(); objective = board.createObjective("emoji060", ScoreboardCriterion.DUMMY);
                objective.setDisplayName("Title :smile: \ud83d\ude00"); board.getScore("Entry :heart:", objective).set(1);
                board.setDisplayObjective(1, objective);
                mc.gui.getChat().addMessage(new LiteralText("Chat :smile: \ud83d\ude00")); phase = 2;
            } else if (phase == 2 && frames >= 4) {
                Screenshot.take(mc.gameDir, "scoreboard-emoji-disabled.png", mc.width, mc.height, mc.getRenderTarget());
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }
    public static void frame() {
        if (done || phase != 2 || frames >= 4) return;
        try {
            var mc = Minecraft.getInstance(); boolean hidden = frames != 1;
            EmojiText.setHideInScoreboard(hidden);
            widths.clear(); measureProbe = true;
            var huds = Class.forName("org.polyfrost.vanillahud.hud.Huds");
            var hud = huds.getMethod("getScoreboard").invoke(huds.getField("INSTANCE").get(null));
            var measure = hud.getClass().getDeclaredMethod("measureSize"); measure.setAccessible(true); measure.invoke(hud);
            measureProbe = false;
            require(!widths.isEmpty() && widths.stream().allMatch(value -> value == !hidden), "HUD measurement used the wrong emoji state: " + widths);
            Chat059Qa.positions.clear(); Chat059Qa.measuring = true;
            ((Scoreboard060GuiAccess) mc.gui).release$scoreboard(objective, new Window(mc));
            Chat059Qa.measuring = false; int scoreboardImages = Chat059Qa.positions.size();
            require(hidden ? scoreboardImages == 0 : scoreboardImages >= 3, "Unexpected scoreboard emoji images: " + scoreboardImages);
            Chat059Qa.positions.clear(); Chat059Qa.measuring = true;
            mc.textRenderer.draw("Chat :smile: \ud83d\ude00", 10, 10, -1, false);
            Chat059Qa.measuring = false;
            require(Chat059Qa.positions.size() == 2 && EmojiText.enabled(), "The scoreboard setting leaked into chat");
            cases.add(java.util.Map.of("hidden", hidden, "scoreboardImages", scoreboardImages, "chatImages", 2, "widthCalls", widths.size()));
            frames++;
        } catch (Throwable error) { finish(error); }
    }
    private static void finish(Throwable error) {
        done = true; measureProbe = false; Chat059Qa.measuring = false;
        var report = new LinkedHashMap<String, Object>(); report.put("passed", error == null); report.put("cases", cases);
        if (error != null) { report.put("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(Path.of(System.getProperty("arcadium.scoreboard060.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
