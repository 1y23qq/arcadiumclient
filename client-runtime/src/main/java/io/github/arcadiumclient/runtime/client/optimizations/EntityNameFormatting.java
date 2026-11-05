// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.branding.NametagText;

public final class EntityNameFormatting {
    public static long calls, plainNames;
    public static volatile boolean resetNameCacheEnabled = true;
    public static long resetCalls, resetHits;
    public static int resetEntries, resetCharacters;
    private static final ResetNameCache resetNames = new ResetNameCache(2048, 512 << 10);
    private static boolean resetWasEnabled;
    private EntityNameFormatting() { }

    public static String strip(String text) {
        calls++;
        if (text == null || text.indexOf('§') < 0) { plainNames++; return text; }
        return NametagText.stripFormatting(text);
    }

    public static String appendReset(String text) {
        resetCalls++;
        if (!resetNameCacheEnabled) {
            if (resetWasEnabled) { resetNames.clear(); resetEntries = resetCharacters = 0; }
            resetWasEnabled = false; return text + "\u00a7r";
        }
        resetWasEnabled = true;
        String result = resetNames.format(text);
        if (resetNames.hit()) resetHits++;
        resetEntries = resetNames.size(); resetCharacters = resetNames.characters();
        return result;
    }
}
