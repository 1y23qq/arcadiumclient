// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import java.lang.ref.WeakReference;

public final class AttackParticleState {
    private static final long MAX_AGE_NANOS = 2_000_000_000L;
    private record Entry(WeakReference<Object> attacker, WeakReference<Object> target, WeakReference<Object> level, long created, boolean sweep) {
        boolean matches(Object player, Object entity, Object world) {
            return attacker.get() == player && target.get() == entity && level.get() == world;
        }
    }
    private volatile Entry entry;

    public void begin(Object player, Object target, Object level, boolean sweep, long now) {
        entry = new Entry(new WeakReference<>(player), new WeakReference<>(target), new WeakReference<>(level), now, sweep);
    }

    public void resolve(Object player, Object target, Object level, boolean sweep) {
        Entry current = entry;
        if (current != null && current.matches(player, target, level))
            entry = new Entry(current.attacker, current.target, current.level, current.created, sweep);
    }

    public boolean suppress(Object player, Object target, Object level, long now) {
        Entry current = entry;
        if (current == null || !current.matches(player, target, level)) return false;
        long age = now - current.created;
        return current.sweep && age >= 0 && age <= MAX_AGE_NANOS;
    }
}
