// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

public final class NoRenderPolicy {
    public enum BlockPart { GRASS, TALL_GRASS, FERNS, FLOWERS, MUSHROOMS, DEAD_BUSHES, SAPLINGS, VINES, LILY_PADS, SUGAR_CANE, CROPS, COBWEBS, FIRE_BLOCKS, TORCHES, LEAVES, SNOW_LAYERS, CARPETS, RAILS, REDSTONE_WIRE }
    public enum EntityPart { PLAYERS, HOSTILE_MOBS, ANIMALS, VILLAGERS, GOLEMS, BATS, SQUIDS, ARMOR_STANDS, ITEM_FRAMES, PAINTINGS, DROPPED_ITEMS, EXPERIENCE_ORBS, ARROWS, FISHING_BOBBERS, SNOWBALLS, EGGS, ENDER_PEARLS, POTIONS, FIREBALLS, TNT, FALLING_BLOCKS, BOATS, MINECARTS, LIGHTNING, FIREWORKS, END_CRYSTALS }
    public enum ParticlePart { ALL, SMOKE, EXPLOSIONS, FIREWORKS, WATER, CRITICAL_HITS, MAGIC_CRITICAL_HITS, POTIONS, PORTAL, ENCHANTING, FLAME, LAVA, REDSTONE, HEARTS, VILLAGERS, NOTES, SNOW, SLIME, BLOCK_DEBRIS, ITEM_DEBRIS, FOOTSTEPS, CLOUDS, SUSPENDED, MOB_APPEARANCE, BARRIERS }
    public enum Effect { CLOUDS, SKY, WEATHER, WORLD_BORDER, BLOCK_OUTLINE, BLOCK_BREAKING, FIRST_PERSON_HAND, FIRE_OVERLAY, WATER_OVERLAY, IN_WALL_OVERLAY, PUMPKIN_OVERLAY, PORTAL_OVERLAY, VIGNETTE, HURT_CAMERA, VIEW_BOBBING, ENTITY_SHADOWS, ENTITY_FIRE, NAME_TAGS }

    public static final NoRenderPolicy OFF = new NoRenderPolicy(0, 0, 0, 0);
    public final long blocks;
    public final long entities;
    public final long particles;
    public final long effects;

    private NoRenderPolicy(long blocks, long entities, long particles, long effects) {
        this.blocks = blocks;
        this.entities = entities;
        this.particles = particles;
        this.effects = effects;
    }

    public static long bit(Enum<?> option) { return 1L << option.ordinal(); }

    public static NoRenderPolicy create(boolean enabled, long blocks, long entities, long particles, long effects) {
        return enabled ? new NoRenderPolicy(blocks, entities, particles, effects) : OFF;
    }

    public boolean matches(long blocks, long entities, long particles, long effects) {
        return this.blocks == blocks && this.entities == entities && this.particles == particles && this.effects == effects;
    }

    public boolean hides(Effect effect) { return (effects & bit(effect)) != 0; }
    public boolean hides(EntityPart entity) { return entity != null && (entities & bit(entity)) != 0; }
    public boolean hides(BlockPart block) { return block != null && (blocks & bit(block)) != 0; }

    public boolean hidesParticle(int type) {
        return (particles & bit(ParticlePart.ALL)) != 0 ||
            (particles & particleMask(type)) != 0;
    }

    public long chunkKey() {
        return blocks | (((entities & bit(EntityPart.ITEM_FRAMES)) != 0 ? 1L : 0L) << 32);
    }

    public static final class RebuildTracker {
        private Object world;
        private long key;

        public boolean update(Object nextWorld, long nextKey) {
            boolean rebuild = nextWorld != null && world == nextWorld && key != nextKey;
            world = nextWorld;
            key = nextKey;
            return rebuild;
        }
    }

    public static BlockPart blockPart(int blockId, int metadata) {
        return switch (blockId) {
            case 6 -> BlockPart.SAPLINGS;
            case 18, 161 -> BlockPart.LEAVES;
            case 27, 28, 66, 157 -> BlockPart.RAILS;
            case 30 -> BlockPart.COBWEBS;
            case 31 -> switch (metadata) {
                case 0 -> BlockPart.DEAD_BUSHES;
                case 1 -> BlockPart.GRASS;
                case 2 -> BlockPart.FERNS;
                default -> null;
            };
            case 32 -> BlockPart.DEAD_BUSHES;
            case 37, 38 -> BlockPart.FLOWERS;
            case 39, 40 -> BlockPart.MUSHROOMS;
            case 50, 75, 76 -> BlockPart.TORCHES;
            case 51 -> BlockPart.FIRE_BLOCKS;
            case 55 -> BlockPart.REDSTONE_WIRE;
            case 59, 104, 105, 115, 141, 142 -> BlockPart.CROPS;
            case 78 -> BlockPart.SNOW_LAYERS;
            case 83 -> BlockPart.SUGAR_CANE;
            case 106 -> BlockPart.VINES;
            case 111 -> BlockPart.LILY_PADS;
            case 171 -> BlockPart.CARPETS;
            case 175 -> switch (metadata & 7) {
                case 2 -> BlockPart.TALL_GRASS;
                case 3 -> BlockPart.FERNS;
                case 0, 1, 4, 5 -> BlockPart.FLOWERS;
                default -> null;
            };
            default -> null;
        };
    }

    public static long particleMask(int type) {
        ParticlePart part = switch (type) {
            case 0, 1, 2 -> ParticlePart.EXPLOSIONS;
            case 3 -> ParticlePart.FIREWORKS;
            case 4, 5, 6, 18, 39 -> ParticlePart.WATER;
            case 7, 8 -> ParticlePart.SUSPENDED;
            case 9 -> ParticlePart.CRITICAL_HITS;
            case 10 -> ParticlePart.MAGIC_CRITICAL_HITS;
            case 11, 12 -> ParticlePart.SMOKE;
            case 13, 14, 15, 16, 17 -> ParticlePart.POTIONS;
            case 19, 27 -> ParticlePart.LAVA;
            case 20, 21, 22 -> ParticlePart.VILLAGERS;
            case 23 -> ParticlePart.NOTES;
            case 24 -> ParticlePart.PORTAL;
            case 25 -> ParticlePart.ENCHANTING;
            case 26 -> ParticlePart.FLAME;
            case 28 -> ParticlePart.FOOTSTEPS;
            case 29 -> ParticlePart.CLOUDS;
            case 30 -> ParticlePart.REDSTONE;
            case 31, 32 -> ParticlePart.SNOW;
            case 33 -> ParticlePart.SLIME;
            case 34 -> ParticlePart.HEARTS;
            case 35 -> ParticlePart.BARRIERS;
            case 36, 40 -> ParticlePart.ITEM_DEBRIS;
            case 37, 38 -> ParticlePart.BLOCK_DEBRIS;
            case 41 -> ParticlePart.MOB_APPEARANCE;
            default -> null;
        };
        return part == null ? 0 : bit(part);
    }
}
