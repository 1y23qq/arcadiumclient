// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackSearch
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourcePackSearchTest {
    private fun matches(query: String, vararg fields: String) =
        ResourcePackSearch.matches(ResourcePackSearch.terms(query), ResourcePackSearch.text(*fields))

    @Test
    fun `finds formatted names inside nested folders without exact whitespace`() {
        assertTrue(matches("  dewier 140k ", "PVP/Favorites/!     Dew§bier 140k.zip", "Dew§bier 140k", "By §aausn"))
        assertTrue(matches("favorites ausn", "PVP/Favorites/Pack.zip", "By §aausn"))
        assertTrue(matches("ＤＥＷＩＥＲ　１４０Ｋ", "Dewier\u00a0140k.zip"))
        assertFalse(matches("dewier 160k", "Dewier 140k.zip"))
    }

    @Test
    fun `treats terms as a case independent search across title path and description`() {
        assertTrue(matches("creator minecraft", "Minecraft textures.zip", "By Creator"))
        assertTrue(matches("", "PVP/Favorites/Pack.zip"))
        assertTrue(matches("  ", "PVP/Favorites/Pack.zip"))
        assertFalse(matches("missing", "PVP/Favorites/Pack.zip", "A description"))
    }
}
