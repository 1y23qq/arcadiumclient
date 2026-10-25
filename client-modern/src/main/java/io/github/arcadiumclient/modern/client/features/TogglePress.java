// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

public final class TogglePress {
    private boolean down;

    public boolean rising(boolean pressed, boolean allowed) {
        boolean transition = pressed && !down;
        down = pressed;
        return transition && allowed;
    }
}
