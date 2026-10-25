// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftFontProviderTypeTest {
    @Test void builtinNamespaceAndLegacyBareTypesResolveToTheSameProvider() {
        for (String provider : new String[]{"space", "bitmap", "reference", "unihex", "legacy_unicode", "ttf"}) {
            assertEquals(provider, MinecraftFontProviderType.builtinName(provider));
            assertEquals(provider, MinecraftFontProviderType.builtinName("minecraft:" + provider));
        }
    }
    @Test void otherNamespacesDoNotBorrowVanillaProviderImplementations() {
        assertEquals("", MinecraftFontProviderType.builtinName("custom:space"));
        assertEquals("", MinecraftFontProviderType.builtinName("other:bitmap"));
        assertEquals("", MinecraftFontProviderType.builtinName(null));
    }
}
