// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy.*

object NoRenderConfig : Config("arcadium-no-render.json", "NoRender", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Apply the selected render filters. World behavior and interactions stay the same.")
    var enabled = false

    @JvmStatic @Switch(title = "Grass", category = "Plants and Blocks")
    var grass = false

    @JvmStatic @Switch(title = "Tall Grass", category = "Plants and Blocks")
    var tallGrass = false

    @JvmStatic @Switch(title = "Ferns", category = "Plants and Blocks")
    var ferns = false

    @JvmStatic @Switch(title = "Flowers", category = "Plants and Blocks")
    var flowers = false

    @JvmStatic @Switch(title = "Mushrooms", category = "Plants and Blocks")
    var mushrooms = false

    @JvmStatic @Switch(title = "Dead Bushes", category = "Plants and Blocks")
    var deadBushes = false

    @JvmStatic @Switch(title = "Saplings", category = "Plants and Blocks")
    var saplings = false

    @JvmStatic @Switch(title = "Vines", category = "Plants and Blocks")
    var vines = false

    @JvmStatic @Switch(title = "Lily Pads", category = "Plants and Blocks")
    var lilyPads = false

    @JvmStatic @Switch(title = "Sugar Cane", category = "Plants and Blocks")
    var sugarCane = false

    @JvmStatic @Switch(title = "Crops", category = "Plants and Blocks")
    var crops = false

    @JvmStatic @Switch(title = "Cobwebs", category = "Plants and Blocks", description = "Hide cobweb models. They still slow movement.")
    var cobwebs = false

    @JvmStatic @Switch(title = "Fire Blocks", category = "Plants and Blocks")
    var fireBlocks = false

    @JvmStatic @Switch(title = "Torches", category = "Plants and Blocks")
    var torches = false

    @JvmStatic @Switch(title = "Leaves", category = "Plants and Blocks", description = "Hide leaf models. Their collision and light blocking stay the same.")
    var leaves = false

    @JvmStatic @Switch(title = "Snow Layers", category = "Plants and Blocks")
    var snowLayers = false

    @JvmStatic @Switch(title = "Carpets", category = "Plants and Blocks")
    var carpets = false

    @JvmStatic @Switch(title = "Rails", category = "Plants and Blocks")
    var rails = false

    @JvmStatic @Switch(title = "Redstone Wire", category = "Plants and Blocks")
    var redstoneWire = false

    @JvmStatic @Switch(title = "Other Players", category = "Entities", description = "Hide other players, including player-based NPCs. Does not hide your own model.")
    var players = false

    @JvmStatic @Switch(title = "Hostile Mobs", category = "Entities")
    var hostileMobs = false

    @JvmStatic @Switch(title = "Animals", category = "Entities")
    var animals = false

    @JvmStatic @Switch(title = "Villagers", category = "Entities")
    var villagers = false

    @JvmStatic @Switch(title = "Golems", category = "Entities")
    var golems = false

    @JvmStatic @Switch(title = "Bats", category = "Entities")
    var bats = false

    @JvmStatic @Switch(title = "Squids", category = "Entities")
    var squids = false

    @JvmStatic @Switch(title = "Armor Stands", category = "Entities")
    var armorStands = false

    @JvmStatic @Switch(title = "Item Frames", category = "Entities")
    var itemFrames = false

    @JvmStatic @Switch(title = "Paintings", category = "Entities")
    var paintings = false

    @JvmStatic @Switch(title = "Dropped Items", category = "Entities")
    var droppedItems = false

    @JvmStatic @Switch(title = "Experience Orbs", category = "Entities")
    var experienceOrbs = false

    @JvmStatic @Switch(title = "Arrows", category = "Entities")
    var arrows = false

    @JvmStatic @Switch(title = "Fishing Bobbers", category = "Entities")
    var fishingBobbers = false

    @JvmStatic @Switch(title = "Snowballs", category = "Entities")
    var snowballs = false

    @JvmStatic @Switch(title = "Eggs", category = "Entities")
    var eggs = false

    @JvmStatic @Switch(title = "Ender Pearls", category = "Entities")
    var enderPearls = false

    @JvmStatic @Switch(title = "Thrown Potions and XP Bottles", category = "Entities")
    var potions = false

    @JvmStatic @Switch(title = "Fireballs and Wither Skulls", category = "Entities")
    var fireballs = false

    @JvmStatic @Switch(title = "Primed TNT", category = "Entities")
    var tnt = false

    @JvmStatic @Switch(title = "Falling Blocks", category = "Entities")
    var fallingBlocks = false

    @JvmStatic @Switch(title = "Boats", category = "Entities")
    var boats = false

    @JvmStatic @Switch(title = "Minecarts", category = "Entities")
    var minecarts = false

    @JvmStatic @Switch(title = "Lightning", category = "Entities")
    var lightning = false

    @JvmStatic @Switch(title = "Firework Rockets", category = "Entities")
    var fireworks = false

    @JvmStatic @Switch(title = "End Crystals", category = "Entities")
    var endCrystals = false

    @JvmStatic @Switch(title = "All Particles", category = "Particles")
    var allParticles = false

    @JvmStatic @Switch(title = "Smoke", category = "Particles")
    var smokeParticles = false

    @JvmStatic @Switch(title = "Explosions", category = "Particles")
    var explosionParticles = false

    @JvmStatic @Switch(title = "Firework Particles", category = "Particles")
    var fireworkParticles = false

    @JvmStatic @Switch(title = "Water and Rain Splashes", category = "Particles")
    var waterParticles = false

    @JvmStatic @Switch(title = "Critical Hits", category = "Particles")
    var criticalParticles = false

    @JvmStatic @Switch(title = "Magic Critical Hits", category = "Particles")
    var magicCriticalParticles = false

    @JvmStatic @Switch(title = "Potion Effects", category = "Particles")
    var potionParticles = false

    @JvmStatic @Switch(title = "Portal", category = "Particles")
    var portalParticles = false

    @JvmStatic @Switch(title = "Enchanting", category = "Particles")
    var enchantingParticles = false

    @JvmStatic @Switch(title = "Flames", category = "Particles")
    var flameParticles = false

    @JvmStatic @Switch(title = "Lava and Lava Drips", category = "Particles")
    var lavaParticles = false

    @JvmStatic @Switch(title = "Redstone", category = "Particles")
    var redstoneParticles = false

    @JvmStatic @Switch(title = "Hearts", category = "Particles")
    var heartParticles = false

    @JvmStatic @Switch(title = "Villager Effects", category = "Particles")
    var villagerParticles = false

    @JvmStatic @Switch(title = "Music Notes", category = "Particles")
    var noteParticles = false

    @JvmStatic @Switch(title = "Snow", category = "Particles")
    var snowParticles = false

    @JvmStatic @Switch(title = "Slime", category = "Particles")
    var slimeParticles = false

    @JvmStatic @Switch(title = "Block Breaking and Digging", category = "Particles")
    var blockParticles = false

    @JvmStatic @Switch(title = "Item Debris", category = "Particles")
    var itemParticles = false

    @JvmStatic @Switch(title = "Footsteps", category = "Particles")
    var footstepParticles = false

    @JvmStatic @Switch(title = "Cloud Puffs", category = "Particles")
    var cloudParticles = false

    @JvmStatic @Switch(title = "Ambient Specks", category = "Particles")
    var suspendedParticles = false

    @JvmStatic @Switch(title = "Guardian Appearance", category = "Particles")
    var mobAppearanceParticles = false

    @JvmStatic @Switch(title = "Barrier Markers", category = "Particles")
    var barrierParticles = false

    @JvmStatic @Switch(title = "Clouds", category = "World")
    var clouds = false

    @JvmStatic @Switch(title = "Sky, Sun, Moon and Stars", category = "World")
    var sky = false

    @JvmStatic @Switch(title = "Rain and Snow", category = "World")
    var weather = false

    @JvmStatic @Switch(title = "World Border", category = "World")
    var worldBorder = false

    @JvmStatic @Switch(title = "Block Selection Outline", category = "World")
    var blockOutline = false

    @JvmStatic @Switch(title = "Block Breaking Overlay", category = "World")
    var blockBreaking = false

    @JvmStatic @Switch(title = "First Person Hand and Item", category = "Screen Effects")
    var firstPersonHand = false

    @JvmStatic @Switch(title = "Fire Overlay", category = "Screen Effects")
    var fireOverlay = false

    @JvmStatic @Switch(title = "Underwater Overlay", category = "Screen Effects")
    var waterOverlay = false

    @JvmStatic @Switch(title = "Inside Block Overlay", category = "Screen Effects")
    var inWallOverlay = false

    @JvmStatic @Switch(title = "Pumpkin Overlay", category = "Screen Effects")
    var pumpkinOverlay = false

    @JvmStatic @Switch(title = "Portal Overlay", category = "Screen Effects")
    var portalOverlay = false

    @JvmStatic @Switch(title = "Vignette", category = "Screen Effects")
    var vignette = false

    @JvmStatic @Switch(title = "Hurt Camera Shake", category = "Screen Effects")
    var hurtCamera = false

    @JvmStatic @Switch(title = "View Bobbing", category = "Screen Effects")
    var viewBobbing = false

    @JvmStatic @Switch(title = "Entity Shadows", category = "Entity Effects")
    var entityShadows = false

    @JvmStatic @Switch(title = "Burning Entity Flames", category = "Entity Effects")
    var entityFire = false

    @JvmStatic @Switch(title = "Name Tags", category = "Entity Effects")
    var nameTags = false

    fun snapshot(): NoRenderPolicy {
        if (!enabled) return NoRenderPolicy.OFF
        var blocks = 0L
        if (grass) blocks = blocks or NoRenderPolicy.bit(BlockPart.GRASS)
        if (tallGrass) blocks = blocks or NoRenderPolicy.bit(BlockPart.TALL_GRASS)
        if (ferns) blocks = blocks or NoRenderPolicy.bit(BlockPart.FERNS)
        if (flowers) blocks = blocks or NoRenderPolicy.bit(BlockPart.FLOWERS)
        if (mushrooms) blocks = blocks or NoRenderPolicy.bit(BlockPart.MUSHROOMS)
        if (deadBushes) blocks = blocks or NoRenderPolicy.bit(BlockPart.DEAD_BUSHES)
        if (saplings) blocks = blocks or NoRenderPolicy.bit(BlockPart.SAPLINGS)
        if (vines) blocks = blocks or NoRenderPolicy.bit(BlockPart.VINES)
        if (lilyPads) blocks = blocks or NoRenderPolicy.bit(BlockPart.LILY_PADS)
        if (sugarCane) blocks = blocks or NoRenderPolicy.bit(BlockPart.SUGAR_CANE)
        if (crops) blocks = blocks or NoRenderPolicy.bit(BlockPart.CROPS)
        if (cobwebs) blocks = blocks or NoRenderPolicy.bit(BlockPart.COBWEBS)
        if (fireBlocks) blocks = blocks or NoRenderPolicy.bit(BlockPart.FIRE_BLOCKS)
        if (torches) blocks = blocks or NoRenderPolicy.bit(BlockPart.TORCHES)
        if (leaves) blocks = blocks or NoRenderPolicy.bit(BlockPart.LEAVES)
        if (snowLayers) blocks = blocks or NoRenderPolicy.bit(BlockPart.SNOW_LAYERS)
        if (carpets) blocks = blocks or NoRenderPolicy.bit(BlockPart.CARPETS)
        if (rails) blocks = blocks or NoRenderPolicy.bit(BlockPart.RAILS)
        if (redstoneWire) blocks = blocks or NoRenderPolicy.bit(BlockPart.REDSTONE_WIRE)
        var entities = 0L
        if (players) entities = entities or NoRenderPolicy.bit(EntityPart.PLAYERS)
        if (hostileMobs) entities = entities or NoRenderPolicy.bit(EntityPart.HOSTILE_MOBS)
        if (animals) entities = entities or NoRenderPolicy.bit(EntityPart.ANIMALS)
        if (villagers) entities = entities or NoRenderPolicy.bit(EntityPart.VILLAGERS)
        if (golems) entities = entities or NoRenderPolicy.bit(EntityPart.GOLEMS)
        if (bats) entities = entities or NoRenderPolicy.bit(EntityPart.BATS)
        if (squids) entities = entities or NoRenderPolicy.bit(EntityPart.SQUIDS)
        if (armorStands) entities = entities or NoRenderPolicy.bit(EntityPart.ARMOR_STANDS)
        if (itemFrames) entities = entities or NoRenderPolicy.bit(EntityPart.ITEM_FRAMES)
        if (paintings) entities = entities or NoRenderPolicy.bit(EntityPart.PAINTINGS)
        if (droppedItems) entities = entities or NoRenderPolicy.bit(EntityPart.DROPPED_ITEMS)
        if (experienceOrbs) entities = entities or NoRenderPolicy.bit(EntityPart.EXPERIENCE_ORBS)
        if (arrows) entities = entities or NoRenderPolicy.bit(EntityPart.ARROWS)
        if (fishingBobbers) entities = entities or NoRenderPolicy.bit(EntityPart.FISHING_BOBBERS)
        if (snowballs) entities = entities or NoRenderPolicy.bit(EntityPart.SNOWBALLS)
        if (eggs) entities = entities or NoRenderPolicy.bit(EntityPart.EGGS)
        if (enderPearls) entities = entities or NoRenderPolicy.bit(EntityPart.ENDER_PEARLS)
        if (potions) entities = entities or NoRenderPolicy.bit(EntityPart.POTIONS)
        if (fireballs) entities = entities or NoRenderPolicy.bit(EntityPart.FIREBALLS)
        if (tnt) entities = entities or NoRenderPolicy.bit(EntityPart.TNT)
        if (fallingBlocks) entities = entities or NoRenderPolicy.bit(EntityPart.FALLING_BLOCKS)
        if (boats) entities = entities or NoRenderPolicy.bit(EntityPart.BOATS)
        if (minecarts) entities = entities or NoRenderPolicy.bit(EntityPart.MINECARTS)
        if (lightning) entities = entities or NoRenderPolicy.bit(EntityPart.LIGHTNING)
        if (fireworks) entities = entities or NoRenderPolicy.bit(EntityPart.FIREWORKS)
        if (endCrystals) entities = entities or NoRenderPolicy.bit(EntityPart.END_CRYSTALS)
        var particles = 0L
        if (allParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.ALL)
        if (smokeParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.SMOKE)
        if (explosionParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.EXPLOSIONS)
        if (fireworkParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.FIREWORKS)
        if (waterParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.WATER)
        if (criticalParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.CRITICAL_HITS)
        if (magicCriticalParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.MAGIC_CRITICAL_HITS)
        if (potionParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.POTIONS)
        if (portalParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.PORTAL)
        if (enchantingParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.ENCHANTING)
        if (flameParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.FLAME)
        if (lavaParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.LAVA)
        if (redstoneParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.REDSTONE)
        if (heartParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.HEARTS)
        if (villagerParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.VILLAGERS)
        if (noteParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.NOTES)
        if (snowParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.SNOW)
        if (slimeParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.SLIME)
        if (blockParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.BLOCK_DEBRIS)
        if (itemParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.ITEM_DEBRIS)
        if (footstepParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.FOOTSTEPS)
        if (cloudParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.CLOUDS)
        if (suspendedParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.SUSPENDED)
        if (mobAppearanceParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.MOB_APPEARANCE)
        if (barrierParticles) particles = particles or NoRenderPolicy.bit(ParticlePart.BARRIERS)
        var effects = 0L
        if (clouds) effects = effects or NoRenderPolicy.bit(Effect.CLOUDS)
        if (sky) effects = effects or NoRenderPolicy.bit(Effect.SKY)
        if (weather) effects = effects or NoRenderPolicy.bit(Effect.WEATHER)
        if (worldBorder) effects = effects or NoRenderPolicy.bit(Effect.WORLD_BORDER)
        if (blockOutline) effects = effects or NoRenderPolicy.bit(Effect.BLOCK_OUTLINE)
        if (blockBreaking) effects = effects or NoRenderPolicy.bit(Effect.BLOCK_BREAKING)
        if (firstPersonHand) effects = effects or NoRenderPolicy.bit(Effect.FIRST_PERSON_HAND)
        if (fireOverlay) effects = effects or NoRenderPolicy.bit(Effect.FIRE_OVERLAY)
        if (waterOverlay) effects = effects or NoRenderPolicy.bit(Effect.WATER_OVERLAY)
        if (inWallOverlay) effects = effects or NoRenderPolicy.bit(Effect.IN_WALL_OVERLAY)
        if (pumpkinOverlay) effects = effects or NoRenderPolicy.bit(Effect.PUMPKIN_OVERLAY)
        if (portalOverlay) effects = effects or NoRenderPolicy.bit(Effect.PORTAL_OVERLAY)
        if (vignette) effects = effects or NoRenderPolicy.bit(Effect.VIGNETTE)
        if (hurtCamera) effects = effects or NoRenderPolicy.bit(Effect.HURT_CAMERA)
        if (viewBobbing) effects = effects or NoRenderPolicy.bit(Effect.VIEW_BOBBING)
        if (entityShadows) effects = effects or NoRenderPolicy.bit(Effect.ENTITY_SHADOWS)
        if (entityFire) effects = effects or NoRenderPolicy.bit(Effect.ENTITY_FIRE)
        if (nameTags) effects = effects or NoRenderPolicy.bit(Effect.NAME_TAGS)
        val current = NoRender.policy()
        return if (current.matches(blocks, entities, particles, effects)) current
            else NoRenderPolicy.create(true, blocks, entities, particles, effects)
    }
}
