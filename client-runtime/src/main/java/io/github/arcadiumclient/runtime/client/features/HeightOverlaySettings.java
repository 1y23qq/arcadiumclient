// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

public interface HeightOverlaySettings {
    boolean arcadium$minemenHeightLimitEnabled();
    default boolean arcadium$compMcHeightLimitEnabled() { return true; }
    boolean arcadium$heightOverlayEnabled();
}
