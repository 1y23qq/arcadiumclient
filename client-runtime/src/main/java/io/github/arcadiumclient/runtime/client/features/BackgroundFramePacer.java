// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features;

public final class BackgroundFramePacer {
    private long lastFrame;
    private boolean initialized;

    public long remaining(long now, int fps) {
        if (!initialized) return 0;
        long elapsed = now - lastFrame;
        long interval = 1_000_000_000L / Math.max(5, Math.min(200, fps));
        return elapsed < 0 || elapsed >= interval ? 0 : interval - elapsed;
    }

    public void mark(long now) { lastFrame = now; initialized = true; }
    public void reset() { initialized = false; }
}
