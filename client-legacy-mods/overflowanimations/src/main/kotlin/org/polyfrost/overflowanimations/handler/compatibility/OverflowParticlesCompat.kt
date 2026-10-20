/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern minecraft versions.
 * Brings back animations from the 1.7/1.8 era and more.
 * <p>
 * Copyright (C) 2024-2027 lowercasebtw
 * Copyright (C) 2024-2027 mixces
 * Copyright (C) 2024-2027 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package org.polyfrost.overflowanimations.handler.compatibility

//? if <=1.8.9 {
import net.minecraft.entity.Entity
import net.minecraft.entity.living.player.PlayerEntity as Player
//?} else {
/*import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
*///?}
import org.polyfrost.oneconfig.api.event.v1.EventManager
//? if =1.7.10 {
import org.polyfrost.oneconfig.api.event.v1.events.Event
//?} else {
/*import org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig
import org.polyfrost.overflowparticles.client.event.AttackEntityEvent
*///?}

object OverflowParticlesCompat {
    //? if =1.7.10 {
    private val legacyAttack by lazy {
        runCatching { Class.forName("org.polyfrost.overflowparticles.client.event.AttackEntityEvent").constructors.firstOrNull { it.parameterCount == 2 } }.getOrNull()
    }
    private val legacyCheck by lazy {
        runCatching { Class.forName("org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig").getField("checkInvulnerable") }.getOrNull()
    }
    //?}

    @JvmStatic
    fun postAttack(player: Player, target: Entity) {
        //? if =1.7.10 {
        runCatching {
            if (legacyCheck?.getBoolean(null) == true) return
            val event = legacyAttack?.newInstance(player, target) as? Event ?: return
            EventManager.INSTANCE.post(event)
        }
        //?} else {
        /*if (OverflowParticlesConfig.checkInvulnerable) {
            return
        }
        EventManager.INSTANCE.post(AttackEntityEvent(player, target))
        *///?}
    }
}
