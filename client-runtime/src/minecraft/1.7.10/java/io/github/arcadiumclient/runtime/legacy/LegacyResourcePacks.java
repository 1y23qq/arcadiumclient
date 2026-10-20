// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resource.pack.ResourcePacks__UnopenedPack;
import io.github.arcadiumclient.runtime.mixin.client.access.ResourcePackEntryAccessor;

public final class LegacyResourcePacks {
    private static final List<String> CONFIRMED = new ArrayList<>();
    private LegacyResourcePacks() {}
    public static List<String> incompatible() { return CONFIRMED; }
    public static int format(ResourcePacks__UnopenedPack pack) {
        var metadata = ((ResourcePackEntryAccessor) pack).arcadium$metadata();
        return metadata == null ? -1 : metadata.getFormat();
    }
}
