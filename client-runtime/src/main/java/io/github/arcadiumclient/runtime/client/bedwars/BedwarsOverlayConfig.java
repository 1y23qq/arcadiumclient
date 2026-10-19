// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Number;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

public final class BedwarsOverlayConfig extends Config {
    public static final BedwarsOverlayConfig INSTANCE = new BedwarsOverlayConfig();

    @Switch(title = "Enabled", category = "Overlay")
    public boolean enabled = true;
    @Switch(title = "Tab Stats", category = "Overlay")
    public boolean tabStats = true;
    @Switch(title = "Chat Stats", description = "Announce players after names are revealed at match start.", category = "Overlay")
    public boolean chatStats = true;
    @Switch(title = "Use Star Threshold", category = "Overlay", subcategory = "Chat Thresholds")
    public boolean useStarThreshold = true;
    @Number(title = "Star Threshold", min = 0, max = 10000, category = "Overlay", subcategory = "Chat Thresholds")
    public int starThreshold = 200;
    @Switch(title = "Use FKDR Threshold", category = "Overlay", subcategory = "Chat Thresholds")
    public boolean useFkdrThreshold = true;
    @Number(title = "FKDR Threshold", min = 0, max = 1000, category = "Overlay", subcategory = "Chat Thresholds")
    public float fkdrThreshold = 2.5f;
    @Switch(title = "Include Nicked Players", category = "Overlay", subcategory = "Chat Thresholds")
    public boolean includeNickedPlayers = true;
    @Switch(title = "API Key-Less", description = "Try Bordic, Nadeshiko and Abyss without a Hypixel API key.", category = "Overlay", subcategory = "Data Sources")
    public boolean keyless = true;
    @Switch(title = "Use Hypixel API Key", description = "Send your key only to api.hypixel.net. Keyless providers never receive it.", category = "Overlay", subcategory = "Data Sources")
    public boolean useHypixelApiKey = false;
    @Text(title = "Hypixel API Key", category = "Overlay", subcategory = "Data Sources")
    public String hypixelApiKey = "";
    @Switch(title = "Hide Invalid Key Warning", category = "Overlay", subcategory = "Data Sources")
    public boolean hideInvalidKeyWarning = false;
    @Switch(title = "Seraph Alerts", description = "Show Seraph's reported tags with their source.", category = "Overlay", subcategory = "Data Sources")
    public boolean seraphAlerts = false;
    @Text(title = "Seraph API Key", category = "Overlay", subcategory = "Data Sources")
    public String seraphApiKey = "";

    @Switch(title = "Enabled", category = "AutoQueue")
    public boolean autoQueue = false;
    @Number(title = "Queue Delay", min = 0, max = 10000, unit = "ms", category = "AutoQueue")
    public int queueDelay = 2000;
    @Switch(title = "On Win", category = "AutoQueue")
    public boolean queueOnWin = true;
    @Switch(title = "On Lose", category = "AutoQueue")
    public boolean queueOnLose = true;
    @Switch(title = "Auto Map Dodge", category = "AutoQueue", subcategory = "Maps")
    public boolean mapDodge = true;
    @Number(title = "Map Dodge Delay", min = 0, max = 10000, unit = "ms", category = "AutoQueue", subcategory = "Maps")
    public int mapDodgeDelay = 1500;
    @Text(title = "Map Blacklist", description = "One map per line, or separate with commas. Names are case insensitive; * matches any text.", multiline = true, category = "AutoQueue", subcategory = "Maps")
    public String mapBlacklist = "";

    @Switch(title = "Enabled", description = "Generate nicknames in a Hypixel lobby until a rule matches.", category = "Nick Bot")
    public boolean nickBot = false;
    @Number(title = "Nick Interval", min = 1, max = 100, unit = "ticks", category = "Nick Bot")
    public int nickInterval = 20;
    @Number(title = "Lobby Reset Interval", description = "Change lobby after this many attempts. 0 disables resets.", min = 0, max = 100, category = "Nick Bot")
    public int nickResetInterval = 0;
    @Switch(title = "Auto Claim", category = "Nick Bot")
    public boolean nickAutoClaim = true;
    @Switch(title = "4 Letters", category = "Nick Bot", subcategory = "Matching")
    public boolean nickFourLetters = true;
    @Switch(title = "Vowel Repeaters", description = "Match three repeated vowels, such as aaa.", category = "Nick Bot", subcategory = "Matching")
    public boolean nickVowels = true;
    @Switch(title = "Underscore Edges", category = "Nick Bot", subcategory = "Matching")
    public boolean nickUnderscores = true;
    @Switch(title = "Custom Checks", category = "Nick Bot", subcategory = "Matching")
    public boolean nickCustom = true;
    @Text(title = "Exact Names", multiline = true, category = "Nick Bot", subcategory = "Matching")
    public String nickExact = "";
    @Text(title = "Starts With", multiline = true, category = "Nick Bot", subcategory = "Matching")
    public String nickStartsWith = "";
    @Text(title = "Ends With", multiline = true, category = "Nick Bot", subcategory = "Matching")
    public String nickEndsWith = "";
    @Text(title = "Contains", multiline = true, category = "Nick Bot", subcategory = "Matching")
    public String nickContains = "";
    @Text(title = "Reset Lobbies", description = "Lobby commands used when returning from limbo.", category = "Nick Bot", subcategory = "Matching")
    public String nickLobbies = "mw, blitz, sw, bw, mm, bb, duels, s, classic, arcade, uhc, tnt, ww, prototype";

    @Switch(title = "Enabled", category = "Denick")
    public boolean denick = false;
    @Switch(title = "Skin Lookup", description = "Read profile identity from a custom skin. Shared nick skins are excluded.", category = "Denick")
    public boolean denickSkin = true;
    @Switch(title = "UUID Lookup", description = "Look up the real profile when duplicate names expose a regular UUID.", category = "Denick")
    public boolean denickUuid = false;
    @Switch(title = "Bedwars Cosmetic Lookup", category = "Denick")
    public boolean denickCosmetic = true;
    @Switch(title = "Show in Tab", category = "Denick")
    public boolean denickTab = true;
    @Switch(title = "Show Failed Lookups", category = "Denick")
    public boolean denickShowFailed = true;
    @Text(title = "Cosmetic API URL", description = "Your HTTPS lookup endpoint. /denick <name> submits observed kill-message data and returns candidates.", category = "Denick", subcategory = "Cosmetics")
    public String cosmeticApiUrl = "";

    @Switch(title = "Animated Arcadium Prefix", description = "Animate only Arcadium in local client messages.", category = "Style")
    public boolean animatedPrefix = true;

    private BedwarsOverlayConfig() {
        super("arcadium-bedwars-overlay.json", "Bedwars Overlay", Category.HYPIXEL);
        hideIf("hypixelApiKey", () -> !useHypixelApiKey);
        hideIf("seraphApiKey", () -> !seraphAlerts);
        hideIf("useStarThreshold", () -> !chatStats);
        hideIf("starThreshold", () -> !chatStats || !useStarThreshold);
        hideIf("useFkdrThreshold", () -> !chatStats);
        hideIf("fkdrThreshold", () -> !chatStats || !useFkdrThreshold);
        hideIf("includeNickedPlayers", () -> !chatStats);
        hideIf("queueDelay", () -> !autoQueue);
        hideIf("queueOnWin", () -> !autoQueue);
        hideIf("queueOnLose", () -> !autoQueue);
        hideIf("mapDodge", () -> !autoQueue);
        hideIf("mapDodgeDelay", () -> !autoQueue || !mapDodge);
        hideIf("mapBlacklist", () -> !autoQueue || !mapDodge);
        hideIf("cosmeticApiUrl", () -> !denickCosmetic);
    }

    @Button(title = "Reload Overlay", text = "Reload", description = "Clear cached results and reload the current roster. Also available with /reload.", category = "Overlay", subcategory = "Data Sources")
    public void reload() { BedwarsOverlay.reload(); }

    @Button(title = "Clear Hypixel API Key", text = "Clear", category = "Overlay", subcategory = "Data Sources")
    public void clearHypixelKey() { hypixelApiKey = ""; save(); }

    @Button(title = "Clear Seraph API Key", text = "Clear", category = "Overlay", subcategory = "Data Sources")
    public void clearSeraphKey() { seraphApiKey = ""; save(); }

    @Button(title = "Cancel Pending Queue", text = "Cancel", category = "AutoQueue")
    public void cancelQueue() { BedwarsOverlay.cancelQueue(); }

    @Button(title = "Import Nick Bot Rules", text = "Import", description = "Import names, startsWith, endsWith, contains and lobbies from config/arcadium/nickbot.json.", category = "Nick Bot", subcategory = "Matching")
    public void importRules() { BedwarsOverlay.importNickRules(); }

    public boolean hasStatsProvider() {
        return keyless || (useHypixelApiKey && hypixelApiKey != null && !hypixelApiKey.trim().isEmpty());
    }
}
