package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import io.github.arcadiumclient.runtime.qa.mixin.Chat059FontAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.text.LiteralText;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class Chat059Qa {
    public static boolean measuring;
    public static final List<Float> positions = new ArrayList<>();
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final List<Object> layouts = new ArrayList<>();
    private static int ticks, phase, frames;
    private static boolean done;
    public static boolean enabled() { return System.getProperty("arcadium.chat059.output") != null; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static TextFieldWidget field() { return ((ChatScreenAccessor) Minecraft.getInstance().screen).arcadium$chatField(); }

    public static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
            require(++ticks < 1600, "Chat QA timed out in phase " + phase);
            OneConfigConfig.limitUnfocusedFps = false;
            mc.options.pauseOnUnfocus = false;
            if (phase == 0 && ticks > 60) {
                ClientPreferences.setSetupComplete(true);
                var screen = new ArcadiumServersScreen(new TitleScreen()); mc.openScreen(screen);
                var connect = ArcadiumServersScreen.class.getDeclaredMethod("connect", ServerListEntry.class);
                connect.setAccessible(true);
                connect.invoke(screen, new ServerListEntry("Chat QA", System.getProperty("arcadium.chat059.server"), false));
                phase = 1;
            } else if (phase == 1 && mc.world != null && mc.player != null && mc.screen == null) {
                mc.openScreen(new ChatScreen("")); phase = 2;
            } else if (phase == 2) {
                checkInput();
                mc.gui.getChat().addMessage(new LiteralText("\u00a7aRank\u00a7r\ud83d\ude00 after emoji \u00a7lbold\ud83d\ude00 done"));
                field().setText("Rank\ud83d\ude00 after emoji");
                phase = 3;
            } else if (phase == 3 && frames >= 4) {
                require(!layouts.isEmpty(), "No font layouts were checked");
                result.put("layouts", layouts);
                Screenshot.take(mc.gameDir, "chat059.png", mc.width, mc.height, mc.getRenderTarget());
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void checkInput() {
        var f = field(); ChatTweaksConfig.setBypassCommandLimit(false);
        f.setText("a".repeat(100)); f.write("b"); require(f.getText().equals("a".repeat(100)), "ASCII limit overflowed");
        f.setText(""); f.write("\u3042".repeat(102)); require(f.getText().equals("\u3042".repeat(100)), "CJK paste limit failed");
        f.setText("a".repeat(90)); f.write("\ud83d"); f.write("\ude00");
        require(f.getText().equals("a".repeat(90) + "\ud83d\ude00"), "Native surrogate key events did not join");
        require(EmojiText.toWire(f.getText()).length() == 100, "Emoji wire budget was not used");
        f.write("x"); require(f.getText().length() == 92, "Emoji allowed an oversized outgoing message");
        f.eraseCharacters(-1); require(f.getText().equals("a".repeat(90)), "Backspace split a supplementary character");
        f.write("\ud83d\ude00"); f.setCursor(90); f.eraseCharacters(1);
        require(f.getText().equals("a".repeat(90)), "Delete split a supplementary character");
        f.setText("a".repeat(100)); f.setSelectionEnd(95); f.write("1234567");
        require(f.getText().equals("a".repeat(95) + "12345"), "Selection replacement exceeded its budget");
        ChatTweaksConfig.setBypassCommandLimit(true); f.setText("/"); f.write("b".repeat(32770));
        require(f.getText().length() == 32767, "Local command budget was not preserved");
        f.setText("/" + "\u3042".repeat(12000));
        require(f.getText().length() == 10923, "Local command UTF-8 budget overflowed");
        f.setText(""); ChatTweaksConfig.setBypassCommandLimit(false);
        f.write("a".repeat(90) + "\ud83d\ude00");
        Minecraft.getInstance().player.sendChat(f.getText());
        result.put("inputLimitsAndUnicodeEditing", true);
    }

    public static void frame() {
        if (done || phase != 3 || frames >= 4) return;
        var font = Minecraft.getInstance().textRenderer;
        boolean inter = frames >= 2, shadow = frames % 2 != 0;
        OneConfigConfig.useInterInGame = inter;
        for (String prefix : new String[]{"Rank", "\u00a7aRank", "\u00a7lRank", "\u00a7lRank\u00a7r", "\u00a7oRank"}) {
            font.draw(prefix, 20, 20, -1, shadow);
            float end = ((Chat059FontAccess) font).release$x();
            positions.clear(); measuring = true;
            font.draw(prefix + "\ud83d\ude00after\ud83d\ude00", 20, 20, -1, shadow);
            measuring = false;
            require(positions.size() == 2, "Expected two emoji draws");
            require(Math.abs(positions.getFirst() - end) < .01f, "Emoji overlapped its preceding run: " + positions.getFirst() + " / " + end);
            require(positions.get(1) > positions.getFirst() + 8, "Emoji runs overlapped");
            layouts.add(Map.of("inter", inter, "shadow", shadow, "prefix", prefix, "textEnd", end, "emojiX", positions.getFirst()));
        }
        frames++;
    }

    private static void finish(Throwable error) {
        done = true; measuring = false;
        result.put("passed", error == null);
        if (error != null) { result.put("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(Path.of(System.getProperty("arcadium.chat059.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
