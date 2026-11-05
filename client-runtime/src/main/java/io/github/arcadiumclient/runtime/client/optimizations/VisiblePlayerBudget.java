// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class VisiblePlayerBudget {
    private final Set<Object> observed = Collections.newSetFromMap(new IdentityHashMap<>());
    private int previous;

    void observe(Object player) {
        if (player != null && observed.size() < 8192) observed.add(player);
    }
    void beginFrame() { previous = observed.size(); observed.clear(); }
    int previousFrame() { return previous; }
    void clear() { observed.clear(); previous = 0; }
}
