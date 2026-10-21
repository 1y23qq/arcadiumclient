// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HealthText {
    private HealthText() { }
    public static String format(float health, boolean hearts) {
        if (!Float.isFinite(health)) return "0";
        double value = Math.max(0, health) / (hearts ? 2.0 : 1.0);
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
