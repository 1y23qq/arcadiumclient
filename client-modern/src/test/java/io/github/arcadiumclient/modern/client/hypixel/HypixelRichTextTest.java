// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelRichTextTest {
    @Test void changingAChannelPrefixKeepsTheNamesColorAndClickAction() {
        var click = new ClickEvent.RunCommand("/msg Tester ");
        Component source = Component.literal("Party > ").withStyle(ChatFormatting.BLUE)
            .append(Component.literal("Tester").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(click)))
            .append(Component.literal(": hello").withStyle(ChatFormatting.YELLOW));
        Component changed = HypixelRichText.replace(source, 0, 8, "P > ");
        assertEquals("P > Tester: hello", changed.getString());
        var styles = new ArrayList<Style>();
        changed.visit((style, text) -> { if (text.equals("Tester")) styles.add(style); return Optional.empty(); }, Style.EMPTY);
        assertEquals(1, styles.size());
        assertEquals(ChatFormatting.GREEN.getColor(), styles.getFirst().getColor().getValue());
        assertEquals(click, styles.getFirst().getClickEvent());
    }
    @Test void whiteningOnlyTheBodyDoesNotLosePlayerActions() {
        Component source = Component.literal("Tester: ").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/msg Tester ")))
            .append(Component.literal("message").withStyle(ChatFormatting.GRAY));
        Component changed = HypixelRichText.restyle(source, 8, 15, style -> style.withColor(ChatFormatting.WHITE));
        assertEquals("Tester: message", changed.getString());
        changed.visit((style, text) -> {
            if (text.equals("message")) assertEquals(ChatFormatting.WHITE.getColor(), style.getColor().getValue());
            if (text.equals("Tester: ")) assertNotNull(style.getClickEvent());
            return Optional.empty();
        }, Style.EMPTY);
    }
    @Test void onlyTheArcadiumWordGetsTheAnimatedFont() {
        Component message = ModernHypixel.message("Tipped 10 players.");
        assertEquals("Arcadium > Tipped 10 players.", message.getString());
        message.visit((style, text) -> {
            if (text.equals("Arcadium")) assertEquals("arcadium_builtin:prefix", ((FontDescription.Resource) style.getFont()).id().toString());
            if (text.equals(" > ") || text.equals("Tipped 10 players.")) assertEquals(FontDescription.DEFAULT, style.getFont());
            return Optional.empty();
        }, Style.EMPTY);
    }
}
