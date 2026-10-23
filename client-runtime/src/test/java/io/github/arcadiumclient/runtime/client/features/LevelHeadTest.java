// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.runtime.client.bedwars.BedwarsStats;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LevelHeadTest {
    private static final UUID ID = UUID.fromString("12345678-1234-4234-9234-123456789abc");

    private static BedwarsStats stats(int networkLevel) {
        return new BedwarsStats(ID, "Player", 245, 1, 1, 1, false, false, null, null, null, null, null, null, networkLevel);
    }

    @Test public void selectingTheLevelChangesTheNametagLabel() {
        assertEquals("Level 50", LevelHead.labelFor(stats(50), ID, "player", 0));
        assertEquals("Bedwars Level 245", LevelHead.labelFor(stats(50), ID, "Player", 1));
        assertNull(LevelHead.labelFor(stats(-1), ID, "Player", 0));
    }

    @Test public void wrongUuidAndNicknamesNeverBorrowAnotherPlayersLevel() {
        assertNull(LevelHead.labelFor(stats(50), UUID.randomUUID(), "Player", 0));
        assertNull(LevelHead.labelFor(stats(50), ID, "Nickname", 0));
        assertNull(LevelHead.labelFor(BedwarsStats.nicked(ID, "Player"), ID, "Player", 0));
        assertNull(LevelHead.labelFor(BedwarsStats.unavailable(ID, "Player"), ID, "Player", 1));
    }
}
