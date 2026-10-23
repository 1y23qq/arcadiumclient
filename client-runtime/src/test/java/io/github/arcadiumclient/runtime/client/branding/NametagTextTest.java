// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NametagTextTest {
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
