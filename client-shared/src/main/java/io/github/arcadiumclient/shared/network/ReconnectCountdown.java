// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.network;

import java.util.function.LongSupplier;

public final class ReconnectCountdown {
    private static final long DELAY = 3_000_000_000L;
    private final LongSupplier clock;
    private long started;
    private boolean active, startedOnce, attempted;

    public ReconnectCountdown() { this(System::nanoTime); }
    public ReconnectCountdown(LongSupplier clock) { this.clock = clock; }
    public void start() { if (!startedOnce) { started = clock.getAsLong(); active = true; startedOnce = true; } }
    public int seconds() {
        if (!active) return 0;
        long remaining = DELAY - (clock.getAsLong() - started);
        return remaining <= 0 ? 0 : (int) ((remaining + 999_999_999L) / 1_000_000_000L);
    }
    public boolean ready() { return active && clock.getAsLong() - started >= DELAY; }
    public boolean take() { if (!startedOnce || attempted) return false; attempted = true; active = false; return true; }
    public void cancel() { active = false; }
    public String label() { return active ? "Reconnect (" + seconds() + ")" : "Reconnect"; }
}
