// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.HashSet;
import java.util.Set;

public final class AutoTextPressGate {
    private final Set<String> held = new HashSet<>();

    public boolean press(String id, boolean down) {
        if (!down) { held.remove(id); return false; }
        return held.add(id);
    }

    public void forget(String id) { held.remove(id); }
}
