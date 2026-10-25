package org.polyfrost.oneconfig.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FontPreferencesTest {
    @Test
    void keepsPackFontsByDefaultAndResolvesConflictingFlags() {
        assertEquals(FontPreferences.Source.RESOURCE_PACK, FontPreferences.source(false, false));
        assertEquals(FontPreferences.Source.VANILLA, FontPreferences.source(false, true));
        assertEquals(FontPreferences.Source.INTER, FontPreferences.source(true, false));
        assertEquals(FontPreferences.Source.INTER, FontPreferences.source(true, true));
    }

    @Test void gameVanillaAlsoRemovesResourcePackFontsFromMods() {
        assertEquals(FontPreferences.Source.VANILLA, FontPreferences.modSource(false, false, true));
        assertEquals(FontPreferences.Source.INTER, FontPreferences.modSource(true, false, true));
        assertEquals(FontPreferences.Source.VANILLA, FontPreferences.modSource(false, true, true));
    }

    @Test void gameInterLeavesModFontsIndependent() {
        assertEquals(FontPreferences.Source.INTER, FontPreferences.source(true, false));
        assertEquals(FontPreferences.Source.RESOURCE_PACK, FontPreferences.modSource(false, false, false));
        assertEquals(FontPreferences.Source.VANILLA, FontPreferences.modSource(false, true, false));
    }
}
