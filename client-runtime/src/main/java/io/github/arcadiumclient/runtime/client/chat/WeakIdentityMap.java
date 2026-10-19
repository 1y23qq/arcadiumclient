// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;

public final class WeakIdentityMap<K, V> {
    private static final class Key<T> extends WeakReference<T> {
        private final int hash;
        Key(T value, ReferenceQueue<T> queue) { super(Objects.requireNonNull(value), queue); hash = System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            T value = get();
            return value != null && other instanceof Key<?> key && value == key.get();
        }
    }

    private final ReferenceQueue<K> collected = new ReferenceQueue<>();
    private final HashMap<Key<K>, V> entries = new HashMap<>();

    public synchronized V getOrDefault(K key, V fallback) {
        discardCollected();
        return entries.getOrDefault(new Key<>(key, null), fallback);
    }

    public synchronized void put(K key, V value) {
        discardCollected();
        entries.put(new Key<>(key, collected), value);
    }

    public synchronized V computeIfAbsent(K key, Function<K, V> factory) {
        discardCollected();
        Key<K> identity = new Key<>(key, collected);
        V value = entries.get(identity);
        if (value == null) { value = factory.apply(key); entries.put(identity, value); }
        return value;
    }

    private void discardCollected() {
        for (java.lang.ref.Reference<? extends K> key; (key = collected.poll()) != null;) entries.remove(key);
    }
}
