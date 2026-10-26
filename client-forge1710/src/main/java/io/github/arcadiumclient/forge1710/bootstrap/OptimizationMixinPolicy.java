// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import java.util.List;
import java.util.Set;

public final class OptimizationMixinPolicy {
    private static final Set<String> ARCHAIC_GAMEPLAY_MIXINS = Set.of(
        "common.core.MixinAxisAlignedBB", "client.core.MixinEntityRenderer", "client.core.MixinMinecraft");

    private OptimizationMixinPolicy() { }

    public static List<String> archaicMixins(List<String> mixins) {
        if (!mixins.containsAll(ARCHAIC_GAMEPLAY_MIXINS))
            throw new IllegalStateException("The pinned ArchaicFix core mixin list has changed");
        return mixins.stream().filter(name -> !ARCHAIC_GAMEPLAY_MIXINS.contains(name)).toList();
    }
}
