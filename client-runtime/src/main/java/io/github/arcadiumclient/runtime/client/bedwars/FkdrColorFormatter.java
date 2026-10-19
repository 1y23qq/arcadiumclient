// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.Locale;

public final class FkdrColorFormatter {
    private FkdrColorFormatter() {
    }

    public static String colorCode(double fkdr) {
        if (fkdr < 1.0) {
            return "§8";
        }
        if (fkdr < 2.0) {
            return "§7";
        }
        if (fkdr < 3.0) {
            return "§a";
        }
        if (fkdr < 5.0) {
            return "§2";
        }
        if (fkdr < 7.5) {
            return "§e";
        }
        if (fkdr < 12.0) {
            return "§c";
        }
        return "§4";
    }

    public static String format(double fkdr) {
        return colorCode(fkdr) + String.format(Locale.ROOT, "%.2f", fkdr);
    }
}
