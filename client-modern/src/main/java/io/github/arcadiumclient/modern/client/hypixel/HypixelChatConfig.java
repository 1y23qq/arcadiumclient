// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.*;
import org.polyfrost.oneconfig.internal.ui.themes.ThemeAccent;

public final class HypixelChatConfig extends Config {
    public static final HypixelChatConfig INSTANCE = new HypixelChatConfig();
    @Switch(title = "Enabled") public static boolean enabled = true;
    @Switch(title = "Animated Arcadium Prefix", category = "Style", description = "Use the current theme accent for local Arcadium messages.") public static boolean animatedPrefix = true;
    @Switch(title = "Player Advertisements", category = "Cleanup") public static boolean removePlayerAds;
    @Switch(title = "Bedwars Advertisements", category = "Cleanup") public static boolean removePlayerBedwarsAds;
    @Switch(title = "Lobby Join Messages", category = "Cleanup") public static boolean removeLobbyJoin;
    @Switch(title = "Connection Messages", category = "Cleanup") public static boolean removeConnectionStatus;
    @Switch(title = "Server Connection Messages", category = "Cleanup") public static boolean removedServerConnectedMsgs;
    @Switch(title = "Online Status", category = "Cleanup") public static boolean removeOnlineStatus;
    @Switch(title = "Automatic Quest Messages", category = "Cleanup") public static boolean removeAutoQuests;
    @Switch(title = "Coins and Experience", category = "Cleanup") public static boolean removeEarnedCoinsAndExp;
    @Switch(title = "Karma", category = "Cleanup") public static boolean hideKarmaMessages;
    @Switch(title = "Tip Messages", category = "Cleanup") public static boolean removeTipMessages;
    @Switch(title = "Game Announcements", category = "Cleanup") public static boolean removeGameAnnouncements;
    @Switch(title = "Game Tips", category = "Cleanup") public static boolean removeGameTips;
    @Switch(title = "Guild MOTD", category = "Cleanup") public static boolean hideGuildMOTDEnabled;
    @Switch(title = "View Stats Prompts", category = "Cleanup") public static boolean removeViewStats;
    @Switch(title = "Replay Messages", category = "Cleanup") public static boolean removeReplayMessage;
    @Switch(title = "Gifted Rank Announcements", category = "Cleanup") public static boolean removeGiftedRanksAmount;
    @Switch(title = "Lobby Fishing Messages", category = "Cleanup") public static boolean removeLobbyFishingMsgs;
    @Switch(title = "Soul Well Announcements", category = "Cleanup") public static boolean removeSoulWellAnnouncements;
    @Switch(title = "Ticket Machine Announcements", category = "Cleanup") public static boolean removeTicketMachineAnnouncements;
    @Switch(title = "Hype Limit Reminder", category = "Cleanup") public static boolean removeHypeLimitReminder;
    @Switch(title = "Bridge Own Goal Messages", category = "Cleanup") public static boolean removeBridgeOwnGoalDeathMsg;
    @Switch(title = "Duels Cosmetic Restrictions", category = "Cleanup") public static boolean removeDuelsBlockTrailDisabled;
    @Switch(title = "Duels Stats Unchanged", category = "Cleanup") public static boolean removeDuelsNoStatsChange;
    @Switch(title = "Simulator Collection Messages", category = "Cleanup") public static boolean removeSimulatorCollectedMsgs;
    @Switch(title = "Hot Potato Announcements", category = "Cleanup") public static boolean removeHotPotato;
    @Switch(title = "Curse of Spam Messages", category = "Cleanup") public static boolean removeCurseOfSpam;
    @Switch(title = "Discord Safety Reminder", category = "Cleanup") public static boolean removeDiscordSafetyWarning;

    @Switch(title = "Short Channel Names", category = "Formatting") public static boolean shortChannelNames;
    @Switch(title = "Short Private Message Labels", category = "Formatting") public static boolean shortPMChannelNames;
    @Switch(title = "White Private Messages", category = "Formatting") public static boolean whitePrivateMessagesEnabled = true;
    @Switch(title = "White Unranked Messages", category = "Formatting") public static boolean whiteNoRankMessagesEnabled = true;
    @Switch(title = "Colored Status Changes", category = "Formatting") public static boolean coloredStatuses = true;
    @Switch(title = "Restyle Game Status", category = "Formatting") public static boolean gameStatusRestyle = true;
    @Switch(title = "Show Count on Player Leave", category = "Formatting") public static boolean playerCountOnPlayerLeave = true;
    @Switch(title = "Player Count Before Name", category = "Formatting") public static boolean playerCountBeforePlayerName = true;
    @Switch(title = "Align Player Counts", category = "Formatting") public static boolean padPlayerCount;
    @Switch(title = "Compact Game Countdown", category = "Formatting") public static boolean compactGameStartAnnouncements = true;
    @Switch(title = "Hypixel Emoji Shortcodes", category = "Formatting") public static boolean mvpEmojisEnabled = true;

    @Switch(title = "Auto Accept Friend Requests", category = "Social") public static boolean autoFriend;
    @Switch(title = "Confirm Party Warps", category = "Social") public static boolean autoPartyWarpConfirm;
    @Switch(title = "Notify When Kicked", category = "Social") public static boolean notifyWhenKick;
    @Switch(title = "Uppercase Kick Notifications", category = "Social") public static boolean notifyWhenKickInCaps;
    @Switch(title = "Broadcast Network Level Up", category = "Social") public static boolean broadcastLevelUp;
    @Switch(title = "Broadcast Achievements", category = "Social") public static boolean broadcastAchievements;
    @Switch(title = "Welcome New Guild Members", category = "Social") public static boolean guildWelcomeMessage;
    @Switch(title = "Thank Watchdog", category = "Social") public static boolean thankWatchdog;
    @Switch(title = "Block Shouts During Cooldown", category = "Cooldowns") public static boolean preventShoutingOnCooldown = true;
    @Switch(title = "Block Chat During Unranked Cooldown", category = "Cooldowns", description = "Only blocks a send while a server-reported chat cooldown is active. Messages are never queued.") public static boolean preventNonCooldown;

    @Dropdown(title = "Coordinate Channel", options = {"All", "Party", "Guild", "Co-op", "Team"}, category = "Waypoints") public static int sendcoordsMode = 1;
    @Switch(title = "Shared Coordinate Waypoints", category = "Waypoints") public static boolean coordsWaypointsEnabled = true;
    @Switch(title = "Dangerous Taunt Waypoints", category = "Waypoints") public static boolean dangerousTauntWaypointEnabled = true;
    @Slider(title = "Waypoint Lifetime", min = 5, max = 300, step = 1, category = "Waypoints") public static int coordsWaypointsTime = 60;
    @Switch(title = "Show Label", category = "Waypoints") public static boolean coordsWaypointsRenderText = true;
    @Switch(title = "Show Distance", category = "Waypoints") public static boolean coordsWaypointsRenderDistance = true;
    @Switch(title = "Show Owner", category = "Waypoints") public static boolean coordsWaypointsRenderOwner = true;
    @Switch(title = "Show Beam", category = "Waypoints") public static boolean coordsWaypointsRenderBeam = true;
    @Switch(title = "Show Box", category = "Waypoints") public static boolean coordsWaypointsRenderBox = true;
    @Slider(title = "Render Distance", min = 16, max = 2048, step = 16, category = "Waypoints") public static int waypointRenderDistance = 512;
    @Color(title = "Beam Color", category = "Waypoints") public static PolyColor coordsWaypointsBeamColor = new PolyColor(0xC0FFFFFF);
    @Color(title = "Box Color", category = "Waypoints") public static PolyColor coordsWaypointsBoxColor = new PolyColor(0xFFFFFFFF);
    @Color(title = "Text Color", category = "Waypoints") public static PolyColor coordsWaypointsTextColor = new PolyColor(0xFFFFFFFF);
    @Color(title = "Distance Color", category = "Waypoints") public static PolyColor coordsWaypointsDistanceTextColor = new PolyColor(0xFFFFFF00);
    @Color(title = "Owner Color", category = "Waypoints") public static PolyColor coordsWaypointsOwnerColor = new PolyColor(0xFFFFFFFF);

    private HypixelChatConfig() { super("hychatter.json", "/assets/arcadium/logo.svg", "Hypixel Chat", Category.HYPIXEL); }
    @Override protected void onInitialized(Tree tree) {
        disableAllUnless("enabled");
        ThemeAccent.setAnimatedChatPrefix(animatedPrefix);
        addCallback("animatedPrefix", (Boolean value) -> { ThemeAccent.setAnimatedChatPrefix(value); return false; });
    }
    @Button(title = "Share Coordinates", text = "Send coordinates", category = "Waypoints") public void sendCoordinates() { ModernHypixel.sendCoordinates(); }
    @Button(title = "Clear Waypoints", text = "Clear", category = "Waypoints") public void clearWaypoints() { ModernHypixel.clearWaypoints(); }
}
