// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.features.HeightLimitText
import io.github.arcadiumclient.runtime.client.features.MinemenHeightLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinemenHeightLimitTest {
    @Test
    fun acceptsAllFourHostsAndNormalizedServerAddresses() {
        for (host in listOf("minemen.club", "as.minemen.club", "na.minemen.club", "eu.minemen.club")) {
            for (address in listOf(host, host.uppercase(), "$host.", "$host:25565", " $host.:25565 ")) {
                assertTrue(MinemenHeightLimit.matches(address), address)
            }
        }
    }

    @Test
    fun rejectsLookalikesUnlistedHostsAndInvalidAddresses() {
        for (address in listOf(null, "", "hypixel.net", "play.minemen.club", "fakeminemen.club", "minemen.club.evil.test",
            "as.minemen.club.evil.test", "minemen.club..", "minemen.club:", "minemen.club:0", "minemen.club:65536",
            "minemen.club:abcd", "minemen.club:25565:123", "https://minemen.club", "minemen.club/path", "minemen.club :25565")) {
            assertFalse(MinemenHeightLimit.matches(address), address.orEmpty())
        }
    }

    @Test
    fun compMcUsesTheSameAddressValidationAndCeiling() {
        for (address in listOf("compmc.net", "COMPMC.NET", "compmc.net.", "compmc.net:25565", " compmc.net.:25565 ")) {
            assertTrue(MinemenHeightLimit.matchesCompMc(address), address)
            assertFalse(MinemenHeightLimit.matches(address), address)
        }
        for (address in listOf(null, "", "minemen.club", "fakecompmc.net", "compmc.net.evil.test", "compmc.net:0",
            "compmc.net:65536", "compmc.net:abc", "compmc.net..", "https://compmc.net", "compmc.net/path")) {
            assertFalse(MinemenHeightLimit.matchesCompMc(address), address.orEmpty())
        }
        assertEquals("100(36)", HeightLimitText.format(1, 64, MinemenHeightLimit.LIMIT, true, false, true))
    }

    @Test
    fun hudLimitAndDistanceUseTheSameCeiling() {
        assertEquals("64 / 100", HeightLimitText.format(0, 64, MinemenHeightLimit.LIMIT, true, false, true))
        assertEquals("100(36)", HeightLimitText.format(1, 64, MinemenHeightLimit.LIMIT, true, false, true))
        assertEquals("100(0)", HeightLimitText.format(1, 100, MinemenHeightLimit.LIMIT, true, false, true))
        assertEquals("100(0)", HeightLimitText.format(1, 101, MinemenHeightLimit.LIMIT, true, false, true))
    }
}
