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
}
