package org.polyfrost.vanillahud.util

//? if = 1.7.10 {
import net.minecraft.client.network.PlayerInfo

object TabListManager {
    private var preview: List<PlayerInfo>? = null
    val devInfo: List<PlayerInfo> get() {
        ensureLoaded()
        return preview!!
    }
    fun ensureLoaded() {
        if (preview == null) preview = listOf("Arcadium", "Player", "Steve").map(::PlayerInfo)
    }
}
//?}
