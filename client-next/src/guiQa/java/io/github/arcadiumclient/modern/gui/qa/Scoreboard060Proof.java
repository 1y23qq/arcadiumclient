package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.modern.gui.qa.mixin.FontSource060Access;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FontDescription;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class Scoreboard060Proof {
    public static void verify(Minecraft client, Path output) throws Exception {
        if (!ChatTweaksConfig.getHideEmojiInScoreboard()) throw new IllegalStateException("Scoreboard emoji default was not enabled");
        var field = ChatTweaksConfig.INSTANCE.getTree().getProp("hideEmojiInScoreboard");
        if (field == null) throw new IllegalStateException("The setting was not registered");
        var font = (FontSource060Access) client.font;
        if (!font.release$source(FontDescription.DEFAULT).getClass().getName().contains("EmojiSource")) throw new IllegalStateException("Normal emoji font was not selected");
        boolean previous = EmojiText.beginScoreboard();
        try {
            if (font.release$source(FontDescription.DEFAULT).getClass().getName().contains("EmojiSource")) throw new IllegalStateException("The scoreboard retained the emoji font");
        } finally { EmojiText.endScoreboard(previous); }
        field.setAs(false);
        previous = EmojiText.beginScoreboard();
        try {
            if (!font.release$source(FontDescription.DEFAULT).getClass().getName().contains("EmojiSource")) throw new IllegalStateException("Turning the setting off did not restore emoji");
        } finally { EmojiText.endScoreboard(previous); field.setAs(true); }
        if (!EmojiText.enabled()) throw new IllegalStateException("The scoreboard scope leaked into chat");
        client.options.save();
        if (Boolean.getBoolean("arcadium.guiQa.ao060") && !Files.readAllLines(client.gameDirectory.toPath().resolve("options.txt")).contains("oneconfig_legacy_ao:1")) throw new IllegalStateException("Legacy smooth lighting state was discarded");
        Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("passed",true,"scoreboardDefaultOn",true,"nativeFontSuppressed",true,"guiPropertyToggle",true,"chatPreserved",true)));
    }
}
