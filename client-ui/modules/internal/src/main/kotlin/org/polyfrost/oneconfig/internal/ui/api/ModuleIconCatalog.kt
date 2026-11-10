package org.polyfrost.oneconfig.internal.ui.api

import java.util.Locale

object ModuleIconCatalog {
    private const val PREFIX = "assets/arcadium/lucide/"
    private val separators = Regex("[^a-z0-9]")
    private val modules = mapOf(
        "3d-skin" to "box",
        "animations" to "clapperboard",
        "auto-text" to "text-cursor-input",
        "bedwars-overlay" to "bed-double",
        "betternightvision" to "moon-star",
        "betterscreens" to "panels-top-left",
        "blur" to "blend",
        "cape" to "flag",
        "chat-filters" to "list-filter",
        "chatting" to "messages-square",
        "confirmdisconnect" to "unplug",
        "crosshairs" to "crosshair",
        "custom-block-highlight" to "box",
        "custom-crosshair" to "crosshair",
        "custom-f3" to "square-terminal",
        "cute-visuals" to "sparkles",
        "droppeditemtweaksv2" to "package",
        "glintcolorizer" to "paintbrush",
        "gui-scale" to "scaling",
        "health" to "heart",
        "health-hud" to "heart",
        "height-limit" to "ruler",
        "height-limit-hud" to "ruler",
        "hychatter" to "messages-square",
        "hylobby" to "door-open",
        "kill-effects" to "flame",
        "level-head" to "chart-column-increasing",
        "light-level" to "lightbulb",
        "menus" to "panels-top-left",
        "mountopacity" to "eye",
        "nick-hider" to "venetian-mask",
        "no-hit-delay" to "mouse-pointer-click",
        "no-render" to "eye-off",
        "numericalenchantments" to "book-open",
        "optimizations-beta" to "gauge",
        "overflowparticles" to "sparkles",
        "overlaytweaks" to "panels-top-left",
        "password-hider" to "key-round",
        "polyhitbox" to "scan",
        "polytime" to "clock",
        "polyweather" to "cloud-sun",
        "rendertweaksv2" to "monitor-cog",
        "rod-thickness" to "fishing-hook",
        "sciophobia" to "sun",
        "scrolltweaks" to "mouse",
        "shaketweaks" to "vibrate",
        "smoothscrollingrefurbished" to "mouse",
        "smoothskies" to "cloud-sun",
        "star-hider" to "star-off",
        "tab-background" to "table-properties",
        "tipper" to "hand-coins",
        "toggle-sprint-hud" to "footprints",
        "zoom-keys" to "zoom-in",
    )
    private val huds = mapOf(
        "actionbar" to "type",
        "armor" to "shield",
        "armorstatus" to "shield",
        "bedwarsresources" to "boxes",
        "biome" to "trees",
        "blockcounter" to "boxes",
        "blockinfo" to "box",
        "bossbar" to "activity",
        "clock" to "clock",
        "combo" to "swords",
        "compass" to "compass",
        "coordinates" to "map-pin",
        "coords" to "map-pin",
        "cps" to "mouse-pointer-click",
        "day" to "sun",
        "direction" to "compass",
        "fps" to "gauge",
        "health" to "heart",
        "heightlimit" to "ruler",
        "hotbar" to "rows-3",
        "itemcounter" to "boxes",
        "keystrokes" to "keyboard",
        "lightlevel" to "lightbulb",
        "memory" to "memory-stick",
        "ping" to "wifi",
        "potioneffects" to "flask-conical",
        "potions" to "flask-conical",
        "reach" to "ruler",
        "resourcedisplay" to "boxes",
        "scoreboard" to "list-ordered",
        "serveraddress" to "server",
        "serverip" to "server",
        "speed" to "gauge",
        "time" to "clock",
        "togglesprint" to "footprints",
        "weather" to "cloud-sun",
    )

    @JvmStatic
    fun forConfig(id: String): String? {
        val key = id.lowercase(Locale.ROOT).substringBefore('/').removeSuffix(".json").removePrefix("arcadium-")
        return modules[key]?.let { "$PREFIX$it.svg" }
    }

    @JvmStatic
    fun forHud(id: String, title: String): String? {
        fun key(value: String) = value.lowercase(Locale.ROOT).replace(separators, "").removeSuffix("hud").removePrefix("ukus")
        return (huds[key(id)] ?: huds[key(title)])?.let { "$PREFIX$it.svg" }
    }
}
