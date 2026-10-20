package org.polyfrost.overflowparticles.client.particles

import org.polyfrost.overflowparticles.utils.ParticleType
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation
*///?} else {
import net.minecraft.resource.Identifier as ResourceLocation
//?}

data class ParticleInfo(
    val name: String,
    val id: ParticleType<*>,
    val redirectsTo: ParticleInfo? = null,
    val isIgnored: Boolean = false,
    val isUnfair: Boolean = false
) {
    val location: ResourceLocation
        get() = redirectsTo?.location ?: ParticleRegistry.location(id)

    val isFireworkTriggered: Boolean
        get() = this in VanillaParticles.fireworkTriggered
}
