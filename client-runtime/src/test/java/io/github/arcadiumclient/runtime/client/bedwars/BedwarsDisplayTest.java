// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedwarsDisplayTest {
    private static final UUID ID = UUID.fromString("12345678-1234-4234-9234-123456789abc");

    private static BedwarsStats stats() {
        return BedwarsData.parsePlayer(ID, "Player", BedwarsData.object("""
                {"displayname":"Player","newPackageRank":"MVP_PLUS","networkExp":3430000,
                 "achievements":{"bedwars_level":245},"stats":{"Bedwars":{
                 "final_kills_bedwars":30,"final_deaths_bedwars":4,"kills_bedwars":20,
                 "deaths_bedwars":8,"wins_bedwars":1234,"losses_bedwars":400}}}
                """));
    }

    private static String plain(String text) { return text.replaceAll("(?i)§[0-9a-fk-or]", ""); }

    @Test void everySelectableValueUsesThePlayersOwnStats() {
        BedwarsStats stats = stats();
        String[] values = {"7.50", "2.50", "1,234", "3.09", "50", "245", "[MVP+]"};
        for (int metric = 0; metric < values.length; metric++) {
            String line = plain(BedwarsTabLineFormatter.format(stats, "Player", "§c", true, metric, null));
            assertTrue(line.endsWith(": " + values[metric]), "metric " + metric + ": " + line);
        }
        assertEquals(2.5, stats.getKdr());
        assertEquals(1234, stats.getWins());
        assertEquals(3.085, stats.getWlr());
    }

    @Test void rankCanBeHiddenInBothBuiltInAndCustomFormats() {
        BedwarsStats stats = stats();
        assertTrue(plain(BedwarsTabLineFormatter.format(stats, "Player", "§c", true, 0, null)).contains("[MVP+]"));
        assertFalse(plain(BedwarsTabLineFormatter.format(stats, "Player", "§c", false, 0, null)).contains("[MVP+]"));
        assertFalse(plain(BedwarsTabLineFormatter.format(stats, "Player", "§c", false, 6, "%RANK% %USERNAME%" )).contains("[MVP+]"));
        assertFalse(plain(BedwarsTabLineFormatter.format(stats, "Player", "§c", false, 6, null)).contains("[MVP+]"));
    }

    @Test void aTemplateCanCombineValuesAndOverrideTheirMinecraftColors() {
        String template = "%STAR% %RANK% %USERNAME% : %FKDR% | %WLR% | %KDR% | %WINS% | %LEVEL%";
        String line = plain(BedwarsTabLineFormatter.format(stats(), "Player", "§c", true, 0, template));
        assertEquals("[245✫] [MVP+] Player : 7.50 | 3.09 | 2.50 | 1,234 | 50", line);
        assertEquals("§cPlayer §a7.50 §b3.09", BedwarsTabLineFormatter.format(stats(), "Player", "§e", true, 0,
                "&C%username% &a%FKDR% §b%WLR%"));
    }

    @Test void unknownNewMetricsStayUnknownAndMissingFinalStatsDoNotDiscardKnownWins() {
        BedwarsStats partial = BedwarsData.parsePlayer(ID, "Player", BedwarsData.object("{\"stats\":{\"Bedwars\":{\"wins_bedwars\":9}}}"));
        assertFalse(partial.isUnavailable());
        assertEquals(9, partial.getWins());
        assertTrue(Double.isNaN(partial.getKdr()));
        assertTrue(Double.isNaN(partial.getWlr()));
        assertEquals("9 | -- | --", plain(BedwarsTabLineFormatter.format(partial, "Player", null, false, 0,
                "%WINS% | %KDR% | %WLR%")));
        BedwarsStats noDeaths = BedwarsData.parsePlayer(ID, "Player", BedwarsData.object("{\"stats\":{\"Bedwars\":{\"kills_bedwars\":12,\"deaths_bedwars\":0,\"wins_bedwars\":5,\"losses_bedwars\":0}}}"));
        assertEquals(12, noDeaths.getKdr());
        assertEquals(5, noDeaths.getWlr());
    }

    @Test void nickedPlayersAndInvalidTemplatesKeepAUsableSingleTabLine() {
        assertEquals("Player : Nicked", plain(BedwarsTabLineFormatter.format(BedwarsStats.nicked(ID, "Player"), "Player", null, false, 0, "%FKDR%")));
        assertEquals(plain(BedwarsTabLineFormatter.format(stats(), "Player", null)),
                plain(BedwarsTabLineFormatter.format(stats(), "Player", null, true, 0, "  ")));
        assertEquals("Player %UNKNOWN% 7.50", plain(BedwarsTabLineFormatter.format(stats(), "Player", null, true, 0,
                "%NAME%\n%UNKNOWN%\t%FKDR%")));
        assertEquals(512, BedwarsTabLineFormatter.format(stats(), "Player", null, true, 0, "a".repeat(600)).length());
    }
}
