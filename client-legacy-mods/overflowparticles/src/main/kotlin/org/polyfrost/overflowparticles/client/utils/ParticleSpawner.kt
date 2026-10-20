@file:JvmName("ParticleSpawner")

package org.polyfrost.overflowparticles.client.utils

//? if >1.8.9 {
/*import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.level.Level
*///?} else {
import net.minecraft.world.World
//?}
import org.polyfrost.overflowparticles.client.config.ParticleConfig
import kotlin.math.ceil

var isMultiplied = false

fun color(color: Int, targetColor: Float, config: ParticleConfig): Float {
    if (config.customColor) {
        val scalar = if (config.colorMode == 1) 1f else targetColor
        return color / 255f * scalar
    }

    return targetColor
}

fun colorInt(color: Int, targetColor: Float, cfg: ParticleConfig): Int {
    return (color(color, targetColor, cfg) * 255f).toInt()
}

fun spawn(
    type: String,
    config: ParticleConfig,
    level: World,
    x: Double, y: Double, z: Double,
    xOffset: Double, yOffset: Double, zOffset: Double,
) {
    val multiplier = config.multiplier.takeIf { it.isFinite() }?.coerceIn(0f, 10f) ?: 1f
    val previous = isMultiplied
    try {
        repeat(ceil(multiplier).toInt()) {
            isMultiplied = true
            level.addParticle(type, x - 0.5 + Math.random(), y - 0.5 + Math.random(), z - 0.5 + Math.random(),
                xOffset, yOffset, zOffset)
        }
    } finally {
        isMultiplied = previous
    }
}
