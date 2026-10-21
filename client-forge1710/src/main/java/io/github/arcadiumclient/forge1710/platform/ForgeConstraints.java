// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import com.moulberry.mixinconstraints.util.Abstractions;

public final class ForgeConstraints extends Abstractions {
    @Override protected boolean isDevEnvironment() { return false; }
    @Override protected String getModVersion(String id) {
        return PortLoader.getInstance().getModContainer(id).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }
    @Override protected boolean isVersionInRange(String version, String min, String max, boolean minInclusive, boolean maxInclusive) {
        PortVersion current = PortVersion.parse(version);
        int lower = min == null || min.isBlank() ? 1 : current.compareTo(PortVersion.parse(min));
        int upper = max == null || max.isBlank() ? -1 : current.compareTo(PortVersion.parse(max));
        return (lower > 0 || lower == 0 && minInclusive) && (upper < 0 || upper == 0 && maxInclusive);
    }
    @Override protected String getPlatformName() { return "Forge"; }
}
