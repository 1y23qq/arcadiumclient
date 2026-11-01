// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NametagTextTest {
    @Test void fastFormattingMatchesMinecraftCodesWithoutRemovingUnknownSequences() {
        var random=new java.util.Random(839);var pattern=java.util.regex.Pattern.compile("(?i)§[0-9A-FK-OR]");
        for(int sample=0;sample<2000;sample++){
            var text=new StringBuilder();for(int i=0;i<60;i++)text.append(random.nextInt(4)==0?'§':(char)(32+random.nextInt(95)));
            assertEquals(pattern.matcher(text).replaceAll(""),NametagText.stripFormatting(text.toString()));
        }
        String plain="Player Name";assertSame(plain,NametagText.stripFormatting(plain));assertNull(NametagText.stripFormatting(null));
    }
    @Test void trimsServerPaddingAfterSuffixWhilePreservingFormattingAndInteriorSpaces() {
        assertEquals("§b[MVP§9++§b] qKeyy §7[QUEST]§r", NametagText.withoutTrailingPadding("§b[MVP§9++§b] qKeyy §7[QUEST]   §r"));
        assertEquals("Player§r", NametagText.withoutTrailingPadding("Player\u00a0§r\u200b\ufeff"));
        assertEquals("§aPlayer Name§l§r", NametagText.withoutTrailingPadding("§aPlayer Name §l §r"));
        assertNull(NametagText.withoutTrailingPadding(null));
    }

    @Test void doesNotStripVisibleSuffixesOrLeadingPadding() {
        assertEquals("  Player [QUEST]§r", NametagText.withoutTrailingPadding("  Player [QUEST]§r"));
        assertEquals("Player █", NametagText.withoutTrailingPadding("Player █ "));
    }
}
