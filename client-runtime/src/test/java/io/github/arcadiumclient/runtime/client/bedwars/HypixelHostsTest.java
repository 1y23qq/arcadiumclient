// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.bedwars;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelHostsTest {
    @Test void officialHostsAcceptCasePortsAndTrailingDotsButRejectLookalikes() {
        assertTrue(HypixelHosts.matches("MC.HYPIXEL.NET.:25565", ""));
        assertTrue(HypixelHosts.matches("hypixel.net", null));
        for (String host : new String[]{"hypixel.net.evil.test", "evilhypixel.net", "hypixel.net:65536", "hypixel.net:0",
                "https://hypixel.net", "hypixel.net/player", "user@hypixel.net", "[hypixel.net]"})
            assertFalse(HypixelHosts.matches(host, ""), host);
    }

    @Test void configuredProxyHostsUpdateImmediatelyWithoutWideningExactEntries() {
        String rules = "proxy.example.net:25565, second.example.net\n*.other.example.net;例え.テスト";
        assertTrue(HypixelHosts.matches("PROXY.EXAMPLE.NET:25566", rules));
        assertTrue(HypixelHosts.matches("second.example.net", rules));
        assertTrue(HypixelHosts.matches("other.example.net", rules));
        assertTrue(HypixelHosts.matches("a.other.example.net", rules));
        assertTrue(HypixelHosts.matches("xn--r8jz45g.xn--zckzah", rules));
        assertFalse(HypixelHosts.matches("a.proxy.example.net", rules));
        assertFalse(HypixelHosts.matches("proxy.example.net", "new.example.net"));
        assertTrue(HypixelHosts.matches("new.example.net", "new.example.net"));
    }

    @Test void ipv6ProxiesRequireAValidLiteralAndOptionalPort() {
        assertTrue(HypixelHosts.matches("[2001:db8::1]:25565", "2001:db8::1"));
        assertTrue(HypixelHosts.matches("2001:db8::1", "[2001:db8::1]:25565"));
        assertFalse(HypixelHosts.matches("[2001:db8::1]garbage", "2001:db8::1"));
        assertFalse(HypixelHosts.matches("[2001:db8::1]:", "2001:db8::1"));
        assertFalse(HypixelHosts.matches("[not:ipv6]", "[not:ipv6]"));
    }
}
