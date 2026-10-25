// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class SilentLobbyConfig extends Config {
    public static final SilentLobbyConfig INSTANCE = new SilentLobbyConfig();
    @Switch(title = "Silent Lobby", description = "Only affects Hypixel lobbies, never active games.") public static boolean silentLobby;
    @Switch(title = "Fireworks", subcategory = "Mute") public static boolean lobbyDisableFireworkSounds;
    @Switch(title = "Explosions", subcategory = "Mute") public static boolean lobbyDisableExplosionSounds;
    @Switch(title = "Primed TNT", subcategory = "Mute") public static boolean lobbyDisablePrimedTntSounds;
    @Switch(title = "Delivery Man", subcategory = "Mute") public static boolean lobbyDisableDeliveryManSounds;
    @Switch(title = "Footsteps", subcategory = "Mute") public static boolean lobbyDisableSteppingSounds;
    @Switch(title = "Portals", subcategory = "Mute") public static boolean lobbyDisablePortalSounds;
    @Switch(title = "Note Blocks", subcategory = "Mute") public static boolean lobbyDisableNoteBlockSounds;
    @Switch(title = "Doors", subcategory = "Mute") public static boolean lobbyDisableDoorSounds;
    @Switch(title = "Withers", subcategory = "Mute") public static boolean lobbyDisableWitherSounds;
    @Switch(title = "Bats", subcategory = "Mute") public static boolean lobbyDisableBatSounds;
    @Switch(title = "Slimes", subcategory = "Mute") public static boolean lobbyDisableSlimeSounds;
    @Switch(title = "Arrows", subcategory = "Mute") public static boolean lobbyDisableArrowSounds;
    @Switch(title = "Dragons", subcategory = "Mute") public static boolean lobbyDisableDragonSounds;
    @Switch(title = "Endermen", subcategory = "Mute") public static boolean lobbyDisableEndermanSounds;
    @Switch(title = "Item Pickup", subcategory = "Mute") public static boolean lobbyDisableItemPickupSounds;
    @Switch(title = "Experience Orbs", subcategory = "Mute") public static boolean lobbyDisableExperienceOrbSounds;
    @Switch(title = "Fire", subcategory = "Mute") public static boolean lobbyDisableFireSounds;
    @Switch(title = "Level Up", subcategory = "Mute") public static boolean lobbyDisableLevelupSounds;
    @Switch(title = "Middle Click Server Menus", description = "Use middle click as a normal left click in Hypixel server menus.") public static boolean middleClickGUIItems = true;
    private SilentLobbyConfig() { super("hylobby.json", "/assets/arcadium/logo.svg", "Hypixel Lobby", Category.HYPIXEL); }
    @Override protected void onInitialized(Tree tree) {
        tree.addMetadata("mod_card_toggle", "silentLobby");
        tree.onAllProps((path, property) -> {
            if (path.startsWith("lobbyDisable")) addDependency(path, "silentLobby");
        });
    }
}
