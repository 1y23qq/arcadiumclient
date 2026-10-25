// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HeightLimitMapsTest {
    @Test void mapLimitsUseServerMapNamesAndIgnoreOtherGames() {
        var maps = HeightLimitMaps.parse("""
            [{"gameType":"BEDWARS","name":" Air Show ","maxBuild":105},
             {"gameType":"BRIDGE","name":"Air Show","maxBuild":512},
             {"gameType":"BEDWARS","name":"Invalid","maxBuild":0},
             {"gameType":"BEDWARS","name":"Malformed","maxBuild":{}}, null]
            """);
        assertEquals(105, maps.get(HeightLimitMaps.key("AIR SHOW")));
        assertNull(maps.get("unknown_map"));
        assertEquals(1, maps.size());
    }

    @Test void aBadRefreshCannotReplaceTheLastUsableMapTable() {
        assertThrows(IllegalArgumentException.class, () -> HeightLimitMaps.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> HeightLimitMaps.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> HeightLimitMaps.parse("[{\"gameType\":\"BRIDGE\",\"name\":\"Map\",\"maxBuild\":100}]"));
    }
}
