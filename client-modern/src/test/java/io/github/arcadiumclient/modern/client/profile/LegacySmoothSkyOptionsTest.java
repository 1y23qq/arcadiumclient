// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LegacySmoothSkyOptionsTest {
    @Test void oldVoidAndSunriseChoicesRemainIndependentFromModernWeatherSettings() {
        var raw = new JsonObject(); raw.addProperty("removeVoidBox", true); raw.addProperty("removeSunriseAndSunsetColoring", false); raw.addProperty("clearSkies", true);
        var selected = LegacySmoothSkyOptions.parse(raw);
        assertTrue(selected.removeVoidBox()); assertFalse(selected.removeSunriseColor());
        assertEquals(selected, LegacySmoothSkyOptions.parse(LegacySmoothSkyOptions.encoded(selected)));
        assertEquals(new LegacySmoothSkyOptions.Selection(false, false), LegacySmoothSkyOptions.parse(new JsonObject()));
        raw.addProperty("removeVoidBox", "bad"); assertThrows(IllegalArgumentException.class, () -> LegacySmoothSkyOptions.parse(raw));
    }
}
