// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.capes;

import java.util.UUID;

public final class LocalCapePolicy {
    private LocalCapePolicy() {}
    public static boolean applies(boolean enabled, boolean knownPreset, UUID requested, UUID self, boolean accountPreview) {
        return enabled && knownPreset && !accountPreview && requested != null && requested.equals(self);
    }
}
