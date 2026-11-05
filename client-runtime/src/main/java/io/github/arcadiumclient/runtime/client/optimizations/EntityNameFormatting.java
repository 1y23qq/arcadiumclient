// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.branding.NametagText;

public final class EntityNameFormatting {
    public static long calls, plainNames;
    private EntityNameFormatting() { }

    public static String strip(String text) {
        calls++;
        if (text == null || text.indexOf('§') < 0) { plainNames++; return text; }
        return NametagText.stripFormatting(text);
    }
}
