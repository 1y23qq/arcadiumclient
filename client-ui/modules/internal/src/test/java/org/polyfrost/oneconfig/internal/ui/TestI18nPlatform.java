// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui;

import org.polyfrost.oneconfig.api.platform.v1.I18nPlatform;

public final class TestI18nPlatform implements I18nPlatform {
    @Override public Object translate(String key, String fallback, Object... args) { return fallback; }
    @Override public String getUnformattedText(Object component) {
        return String.valueOf(component).replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
