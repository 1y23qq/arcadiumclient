// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.net.IDN;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class HypixelHosts {
    private static volatile Rules cached = new Rules("", Set.of(), Set.of());
    private record Rules(String source, Set<String> exact, Set<String> domains) { }
    private HypixelHosts() { }

    public static boolean matches(String address, String additional) {
        String host = host(address);
        if (host.isEmpty()) return false;
        if (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) return true;
        String source = additional == null ? "" : additional;
        Rules rules = cached;
        if (!rules.source.equals(source)) {
            Set<String> exact = new HashSet<>(), domains = new HashSet<>();
            for (String item : source.split("[,;\\s]+")) {
                boolean wildcard = item.startsWith("*.");
                String entry = host(wildcard ? item.substring(2) : item);
                if (!entry.isEmpty()) {
                    if (wildcard && entry.indexOf(':') < 0) domains.add(entry);
                    else if (!wildcard) exact.add(entry);
                }
                if (exact.size() + domains.size() >= 128) break;
            }
            rules = new Rules(source, Set.copyOf(exact), Set.copyOf(domains));
            cached = rules;
        }
        if (rules.exact.contains(host)) return true;
        for (String domain : rules.domains) if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }

    private static String host(String address) {
        if (address == null) return "";
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.indexOf('/') >= 0 || value.indexOf('@') >= 0 || value.chars().anyMatch(Character::isWhitespace)) return "";
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 2 || close + 1 < value.length() && (value.charAt(close + 1) != ':' || !validPort(value.substring(close + 2)))) return "";
            return ipv6(value.substring(1, close));
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            if (value.indexOf(':') != colon) return ipv6(value);
            if (!validPort(value.substring(colon + 1))) return "";
            value = value.substring(0, colon);
        }
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        try { return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES); }
        catch (IllegalArgumentException invalid) { return ""; }
    }

    private static boolean validPort(String port) {
        try { return !port.isEmpty() && port.chars().allMatch(Character::isDigit) && Integer.parseInt(port) > 0 && Integer.parseInt(port) <= 65535; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static String ipv6(String address) {
        try { return URI.create("http://[" + address + "]").getHost() == null ? "" : address; }
        catch (IllegalArgumentException invalid) { return ""; }
    }
}
