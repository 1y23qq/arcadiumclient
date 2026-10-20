// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.Locale;
import java.util.Set;

public final class MinemenHeightLimit {
    public static final int LIMIT = 100;
    public static final String MAP_KEY = "arcadium:minemen";
    private static final Set<String> HOSTS = Set.of("minemen.club", "as.minemen.club", "na.minemen.club", "eu.minemen.club");

    private MinemenHeightLimit() { }

    public static boolean matches(String address) {
        if (address == null) return false;
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            if (colon != host.lastIndexOf(':')) return false;
            String port = host.substring(colon + 1);
            if (port.isEmpty() || port.length() > 5) return false;
            int number = 0;
            for (int index = 0; index < port.length(); index++) {
                char digit = port.charAt(index);
                if (digit < '0' || digit > '9') return false;
                number = number * 10 + digit - '0';
            }
            if (number < 1 || number > 65535) return false;
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return HOSTS.contains(host);
    }
}
