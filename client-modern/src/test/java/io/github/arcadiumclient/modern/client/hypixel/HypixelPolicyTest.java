// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelPolicyTest {
    @Test void restrictsServerActionsToTheRealHypixelDomain() {
        assertTrue(HypixelChatPolicy.isHypixel("mc.hypixel.net:25565"));
        assertTrue(HypixelChatPolicy.isHypixel("HYPIXEL.NET"));
        assertFalse(HypixelChatPolicy.isHypixel("hypixel.net.example.com"));
        assertFalse(HypixelChatPolicy.isHypixel("fakehypixel.net"));
        assertFalse(HypixelChatPolicy.isHypixel(null));
    }
    @Test void cleanupNeverMistakesAPlayersChatForAnXpOrKarmaNotification() {
        assertTrue(HypixelChatPolicy.matchingCleanup("§d+25 Karma!").contains("hideKarmaMessages"));
        assertTrue(HypixelChatPolicy.matchingCleanup("+100 Hypixel Experience").contains("removeEarnedCoinsAndExp"));
        assertTrue(HypixelChatPolicy.matchingCleanup("[MVP+] Tester: +25 Karma!").isEmpty());
        assertTrue(HypixelChatPolicy.matchingCleanup("Party > [VIP] Tester: You earned 100 coins").isEmpty());
        assertTrue(HypixelChatPolicy.isPlayerMessage("[123✫] [MVP++] Tester: Watchdog banned someone"));
        assertTrue(HypixelChatPolicy.matchingCleanup("You have 100 coins in your bank.").isEmpty());
    }
    @Test void friendRequestsSupportServerBlocksButNeverPlayerImitations() {
        assertEquals("Tester", HypixelChatPolicy.friendRequest("----------------\nFriend request from [MVP++] Tester\n[ACCEPT] [DENY]\n----------------"));
        assertNull(HypixelChatPolicy.friendRequest("Tester: Friend request from OtherPlayer"));
        assertNull(HypixelChatPolicy.friendRequest("[10✫] [MVP++] Tester: Friend request from OtherPlayer"));
    }
    @Test void dangerousFarmHuntTauntsCreateValidHorizontalCoordinates() {
        assertEquals(new HypixelChatPolicy.DangerousTaunt("Chicken", -20, 40), HypixelChatPolicy.dangerousTaunt("A Chicken taunted from 24 blocks away! They are around: (-20, 40)"));
        assertNull(HypixelChatPolicy.dangerousTaunt("Tester: A Chicken taunted from 24 blocks away! They are around: (-20, 40)"));
        assertNull(HypixelChatPolicy.dangerousTaunt("A Chicken taunted from 24 blocks away! They are around: (30000001, 40)"));
    }
    @Test void advertFilteringUsesMessageBodiesInPrivateChannelsToo() {
        assertTrue(HypixelChatPolicy.advertisement("Guild > [MVP+] Tester: join discord.gg/example", false));
        assertTrue(HypixelChatPolicy.advertisement("Tester: Bedwars carry service", true));
        assertFalse(HypixelChatPolicy.advertisement("Tester: I like playing Bedwars", true));
        assertFalse(HypixelChatPolicy.advertisement("Visit the official discord.gg/example", false));
    }
    @Test void coordinatesSupportNegativeHeightAndRejectInvalidWorldLocations() {
        assertEquals(new HypixelChatPolicy.Coordinates(-15, -64, 27), HypixelChatPolicy.coordinates("Party > Tester: x: -15, y: -64, z: 27"));
        assertEquals(new HypixelChatPolicy.Coordinates(1, 99, -2), HypixelChatPolicy.coordinates("Tester: (1, 99, -2)"));
        assertNull(HypixelChatPolicy.coordinates("x: 30000001, y: 100, z: 5"));
        assertNull(HypixelChatPolicy.coordinates("x: 2, y: 4000, z: 5"));
        assertNull(HypixelChatPolicy.coordinates("Address 123.45.67.89"));
    }
    @Test void playerCountsStayWithinTheirRealGameBounds() {
        var join = HypixelChatPolicy.playerNotice("[VIP] Tester has joined (3/16)!", 0, 0);
        assertNotNull(join); assertEquals(3, join.current()); assertEquals(16, join.maximum());
        var leave = HypixelChatPolicy.playerNotice("Tester has quit!", 0, 16);
        assertNotNull(leave); assertEquals(0, leave.current());
        assertNull(HypixelChatPolicy.playerNotice("Tester has joined (20/16)!", 0, 0));
        assertNull(HypixelChatPolicy.playerNotice("Tester: I have joined (3/16)!", 0, 0));
    }
    @Test void hypixelEmojiReplacementDoesNotCascadeOrModifyUrlPaths() {
        assertEquals("<o/ ( ﾟ◡ﾟ)/", HypixelChatPolicy.hypixelEmoji(":dab: o/"));
        assertEquals("https://example.com/foo/bar", HypixelChatPolicy.hypixelEmoji("https://example.com/foo/bar"));
        assertEquals("☕ ⚔ √(π+x)=L", HypixelChatPolicy.hypixelEmoji(":java: :pvp: :maths:"));
    }
    @Test void silentLobbyRequiresBothServerAndLobbyAndDoesNotLeakToGames() {
        String firework = "minecraft:entity.firework_rocket.blast";
        assertTrue(SilentLobbyPolicy.muted(firework, true, true, true, Set.of("firework")));
        assertFalse(SilentLobbyPolicy.muted(firework, true, false, true, Set.of("firework")));
        assertFalse(SilentLobbyPolicy.muted(firework, false, true, true, Set.of("firework")));
        assertFalse(SilentLobbyPolicy.muted(firework, true, true, false, Set.of("firework")));
        assertFalse(SilentLobbyPolicy.muted("minecraft:entity.player.hurt", true, true, true, Set.of("firework")));
        assertEquals(Set.of("step"), SilentLobbyPolicy.categories("minecraft:block.grass.step"));
    }
    @Test void repeatedWaypointsRefreshWithoutGrowingForeverAndExpireOnTime() {
        WaypointStore store = new WaypointStore();
        var coordinates = new HypixelChatPolicy.Coordinates(1, 80, 2);
        store.put("Tester", coordinates, false, 0, 5);
        store.put("Tester", coordinates, false, 1_000_000_000L, 5);
        assertEquals(1, store.size());
        store.expire(5_000_000_000L); assertEquals(1, store.size());
        store.expire(6_000_000_000L); assertEquals(0, store.size());
        for (int i = 0; i < 40; i++) store.put("Tester", new HypixelChatPolicy.Coordinates(i, 80, 2), false, 0, 60);
        assertEquals(32, store.size());
        store.clear(); assertEquals(0, store.size());
    }
}
