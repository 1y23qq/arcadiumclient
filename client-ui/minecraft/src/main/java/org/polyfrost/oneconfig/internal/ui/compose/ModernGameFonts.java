// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.compose;

//? if >= 1.21.11 && < 26.3 {
/*import com.mojang.blaze3d.font.GlyphInfo;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.polyfrost.oneconfig.internal.ui.themes.ThemeAccent;

public final class ModernGameFonts {
    private static volatile boolean refreshRequested;
    private static boolean initialized;
    private static final FontDescription ARCADIUM_INTER = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(ModernFontResources.BUILTIN_NAMESPACE, "inter"));
    private static final FontDescription ARCADIUM_VANILLA = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(ModernFontResources.VANILLA_NAMESPACE, "default"));
    private static final FontDescription ARCADIUM_EMOJI = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(ModernFontResources.BUILTIN_NAMESPACE, "emoji"));
    private GlyphSource arcadium$lastSource;
    private GlyphSource arcadium$lastEmoji;
    private GlyphSource arcadium$wrapped;
    private GlyphSource arcadium$accentBase;
    private GlyphSource arcadium$accentSource;

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        FontPreferences.setNativeRefresh(() -> refreshRequested = true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!refreshRequested || client.gui == null) return;
            refreshRequested = false;
            //~ if >= 26.2 'gui.getChat' -> 'gui.hud.getChat'
            client.gui.getChat().rescaleChat();
        });
    }


    public GlyphSource select(Font.Provider provider, FontDescription description, GlyphSource original) {
        GlyphSource source = original;
        boolean prefix = description instanceof FontDescription.Resource resource &&
            resource.id().getNamespace().equals(ModernFontResources.BUILTIN_NAMESPACE) && resource.id().getPath().equals("prefix");
        boolean mod = description instanceof FontDescription.Resource resource &&
            resource.id().getNamespace().equals(ModernFontResources.BUILTIN_NAMESPACE) && resource.id().getPath().equals("mod");
        if (prefix || mod || description.equals(FontDescription.DEFAULT)) {
            source = switch (mod ? FontPreferences.modSource() : FontPreferences.nativeSource()) {
                case INTER -> provider.glyphs(ARCADIUM_INTER);
                case VANILLA -> provider.glyphs(ARCADIUM_VANILLA);
                default -> source;
            };
        }
        if (prefix) {
            if (source != arcadium$accentBase) {
                arcadium$accentBase = source;
                arcadium$accentSource = new AccentSource(source);
            }
            source = arcadium$accentSource;
        }
        if (!EmojiText.enabled()) return source;
        GlyphSource emoji = provider.glyphs(ARCADIUM_EMOJI);
        if (source != arcadium$lastSource || emoji != arcadium$lastEmoji) {
            arcadium$lastSource = source;
            arcadium$lastEmoji = emoji;
            arcadium$wrapped = new EmojiSource(source, emoji);
        }
        return arcadium$wrapped;
    }


    private static final class EmojiSource implements GlyphSource {
        private static final Map<Integer, Integer> UNICODE = unicodeGlyphs();
        private final GlyphSource base;
        private final GlyphSource emoji;
        private final Map<Integer, BakedGlyph> glyphs = new HashMap<>();

        private EmojiSource(GlyphSource base, GlyphSource emoji) { this.base = base; this.emoji = emoji; }

        @Override public BakedGlyph getGlyph(int codepoint) {
            if (codepoint == 0xFE0F || codepoint == 0xFE0E) return base.getGlyph(0x200C);
            int glyph = codepoint >= 0x2600 ? UNICODE.getOrDefault(codepoint, codepoint) : codepoint;
            if (!EmojiText.isGlyph(glyph)) return base.getGlyph(codepoint);
            return glyphs.computeIfAbsent(glyph, id -> new ColoredEmoji(emoji.getGlyph(id)));
        }
        @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) { return base.getRandomGlyph(random, codepoint); }

        private static Map<Integer, Integer> unicodeGlyphs() {
            Map<Integer, Integer> glyphs = new HashMap<>();
            for (EmojiText.Entry entry : EmojiText.entries()) glyphs.put(entry.codepoint(), (int) entry.glyph());
            return Map.copyOf(glyphs);
        }
    }

    private record ColoredEmoji(BakedGlyph glyph) implements BakedGlyph {
        private static final GlyphInfo INFO = GlyphInfo.simple(EmojiText.ADVANCE);
        @Override public GlyphInfo info() { return INFO; }
        @Override public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
            return glyph.createGlyph(x, y, color & 0xFF000000 | 0xFFFFFF, shadowColor & 0xFF000000, style, boldOffset, shadowOffset);
        }
    }

    private static final class AccentSource implements GlyphSource {
        private final GlyphSource base;
        private final Map<Integer, BakedGlyph> glyphs = new HashMap<>();
        private AccentSource(GlyphSource base) { this.base = base; }
        @Override public BakedGlyph getGlyph(int codepoint) {
            int index = "Arcadium".indexOf(codepoint);
            return index < 0 ? base.getGlyph(codepoint) : glyphs.computeIfAbsent(codepoint,
                id -> new AccentGlyph(base.getGlyph(id), index));
        }
        @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) { return base.getRandomGlyph(random, codepoint); }
    }

    private record AccentGlyph(BakedGlyph glyph, int index) implements BakedGlyph {
        @Override public GlyphInfo info() { return glyph.info(); }
        @Override public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
            boolean animate = ThemeAccent.animatedChatPrefix();
            int accent = ThemeAccent.snapshot().colorAtPhase(animate ? index / 16.0 : 0,
                ThemeAccent.phaseAt(System.nanoTime()), animate);
            int tint = color & 0xFF000000 | accent & 0xFFFFFF;
            int shadow = shadowColor & 0xFF000000 | (accent & 0xFCFCFC) >>> 2;
            return glyph.createGlyph(x, y, tint, shadow, style, boldOffset, shadowOffset);
        }
    }
}
*///?}
