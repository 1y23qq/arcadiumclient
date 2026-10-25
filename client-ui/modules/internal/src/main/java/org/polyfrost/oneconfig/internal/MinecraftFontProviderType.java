// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

public final class MinecraftFontProviderType {
    private MinecraftFontProviderType() { }
    public static String builtinName(String identifier) {
        if (identifier == null) return "";
        if (identifier.startsWith("minecraft:")) return identifier.substring(10);
        return identifier.indexOf(':') < 0 ? identifier : "";
    }
}
