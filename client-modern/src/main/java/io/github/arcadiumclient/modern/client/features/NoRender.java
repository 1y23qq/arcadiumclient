// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.state.BlockState;
import static io.github.arcadiumclient.modern.client.features.NoRenderPolicy.*;

public final class NoRender {
    private static volatile NoRenderPolicy current = NoRenderPolicy.OFF;
    private static final Map<net.minecraft.world.level.block.Block, Long> BLOCKS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.world.entity.EntityType<?>, Long> ENTITIES = new ConcurrentHashMap<>();
    private static final RebuildTracker REBUILDS = new RebuildTracker();
    private NoRender() {}
    public static void initialize() { NoRenderConfig.INSTANCE.preload(); current = NoRenderConfig.INSTANCE.snapshot(); }
    public static NoRenderPolicy policy() { return current; }
    public static void tick() {
        current = NoRenderConfig.INSTANCE.snapshot();
        Minecraft mc = Minecraft.getInstance();
        if (REBUILDS.update(mc.level, current.chunkKey())) mc.levelRenderer.allChanged();
    }
    public static boolean effect(Effect part) { return current.hides(part); }
    public static boolean block(BlockState state) {
        NoRenderPolicy snapshot = current;
        if (snapshot.blocks == 0 || state == null) return false;
        long mask = BLOCKS.computeIfAbsent(state.getBlock(), block -> {
            BlockPart part = classifyBlock(BuiltInRegistries.BLOCK.getKey(block).getPath());
            return part == null ? 0L : bit(part);
        });
        return (snapshot.blocks & mask) != 0;
    }
    public static BlockPart classifyBlock(String id) {
        if (id.equals("short_grass") || id.equals("bush") || id.equals("dry_short_grass")) return BlockPart.GRASS;
        if (id.equals("tall_grass") || id.equals("dry_tall_grass")) return BlockPart.TALL_GRASS;
        if (id.contains("fern")) return BlockPart.FERNS;
        if (id.endsWith("_leaves")) return BlockPart.LEAVES;
        if (id.endsWith("_sapling") || id.equals("mangrove_propagule")) return BlockPart.SAPLINGS;
        if (id.contains("vine")) return BlockPart.VINES;
        if (id.equals("dead_bush")) return BlockPart.DEAD_BUSHES;
        if (id.equals("lily_pad")) return BlockPart.LILY_PADS;
        if (id.equals("sugar_cane")) return BlockPart.SUGAR_CANE;
        if (id.equals("cobweb")) return BlockPart.COBWEBS;
        if (id.equals("fire") || id.equals("soul_fire")) return BlockPart.FIRE_BLOCKS;
        if (id.contains("torch")) return BlockPart.TORCHES;
        if (id.equals("snow")) return BlockPart.SNOW_LAYERS;
        if (id.endsWith("_carpet")) return BlockPart.CARPETS;
        if (id.endsWith("rail")) return BlockPart.RAILS;
        if (id.equals("redstone_wire")) return BlockPart.REDSTONE_WIRE;
        if (id.equals("wheat") || id.equals("carrots") || id.equals("potatoes") || id.equals("beetroots") || id.endsWith("_stem") || id.equals("nether_wart") || id.equals("cocoa")) return BlockPart.CROPS;
        if (id.endsWith("mushroom") || id.endsWith("_fungus")) return BlockPart.MUSHROOMS;
        if (id.endsWith("tulip") || id.endsWith("orchid") || id.endsWith("flower") || id.endsWith("daisy") || id.equals("dandelion") || id.equals("poppy") || id.equals("allium") || id.equals("azure_bluet") || id.equals("lilac") || id.equals("peony") || id.equals("rose_bush") || id.equals("lily_of_the_valley") || id.equals("pink_petals") || id.equals("wildflowers") || id.equals("cactus_flower")) return BlockPart.FLOWERS;
        return null;
    }
    public static boolean entity(Entity entity) {
        NoRenderPolicy snapshot = current;
        if (snapshot.entities == 0 || entity == null) return false;
        long mask = ENTITIES.computeIfAbsent(entity.getType(), type -> {
            EntityPart part = classifyEntity(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), type.getCategory());
            return part == null ? 0L : bit(part);
        });
        return (snapshot.entities & mask) != 0;
    }
    private static EntityPart classifyEntity(String id, MobCategory category) {
        return switch (id) {
            case "player" -> EntityPart.PLAYERS;
            case "villager", "wandering_trader" -> EntityPart.VILLAGERS;
            case "iron_golem", "snow_golem", "copper_golem" -> EntityPart.GOLEMS;
            case "bat" -> EntityPart.BATS;
            case "squid", "glow_squid" -> EntityPart.SQUIDS;
            case "armor_stand" -> EntityPart.ARMOR_STANDS;
            case "item_frame", "glow_item_frame" -> EntityPart.ITEM_FRAMES;
            case "painting" -> EntityPart.PAINTINGS;
            case "item" -> EntityPart.DROPPED_ITEMS;
            case "experience_orb" -> EntityPart.EXPERIENCE_ORBS;
            case "arrow", "spectral_arrow", "trident" -> EntityPart.ARROWS;
            case "fishing_bobber" -> EntityPart.FISHING_BOBBERS;
            case "snowball" -> EntityPart.SNOWBALLS;
            case "egg" -> EntityPart.EGGS;
            case "ender_pearl" -> EntityPart.ENDER_PEARLS;
            case "potion", "lingering_potion", "splash_potion" -> EntityPart.POTIONS;
            case "fireball", "small_fireball", "dragon_fireball", "wind_charge", "breeze_wind_charge" -> EntityPart.FIREBALLS;
            case "tnt" -> EntityPart.TNT;
            case "falling_block" -> EntityPart.FALLING_BLOCKS;
            case "lightning_bolt" -> EntityPart.LIGHTNING;
            case "firework_rocket" -> EntityPart.FIREWORKS;
            case "end_crystal" -> EntityPart.END_CRYSTALS;
            default -> {
                if (id.endsWith("boat") || id.endsWith("raft")) yield EntityPart.BOATS;
                if (id.endsWith("minecart")) yield EntityPart.MINECARTS;
                if (category == MobCategory.MONSTER) yield EntityPart.HOSTILE_MOBS;
                if (category == MobCategory.CREATURE || category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT || category == MobCategory.AXOLOTLS || category == MobCategory.UNDERGROUND_WATER_CREATURE) yield EntityPart.ANIMALS;
                yield null;
            }
        };
    }
    public static boolean particle(ParticleOptions options) {
        if (current.particles == 0) return false;
        if ((current.particles & bit(ParticlePart.ALL)) != 0) return true;
        String id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).getPath();
        ParticlePart part = null;
        if (id.contains("smoke")) part = ParticlePart.SMOKE;
        else if (id.contains("explosion") || id.equals("flash")) part = ParticlePart.EXPLOSIONS;
        else if (id.contains("firework")) part = ParticlePart.FIREWORKS;
        else if (id.contains("bubble") || id.contains("splash") || id.contains("rain") || id.contains("dripping_water")) part = ParticlePart.WATER;
        else if (id.equals("crit")) part = ParticlePart.CRITICAL_HITS;
        else if (id.equals("enchanted_hit")) part = ParticlePart.MAGIC_CRITICAL_HITS;
        else if (id.contains("effect") || id.equals("entity_effect") || id.equals("instant_effect") || id.equals("witch")) part = ParticlePart.POTIONS;
        else if (id.contains("portal")) part = ParticlePart.PORTAL;
        else if (id.equals("enchant")) part = ParticlePart.ENCHANTING;
        else if (id.contains("flame")) part = ParticlePart.FLAME;
        else if (id.contains("lava")) part = ParticlePart.LAVA;
        else if (id.equals("block_marker")) part = ParticlePart.BARRIERS;
        else if (id.equals("block") || id.equals("block_crumble") || id.equals("falling_dust") || id.equals("dust_pillar")) part = ParticlePart.BLOCK_DEBRIS;
        else if (id.contains("dust")) part = ParticlePart.REDSTONE;
        else if (id.contains("heart")) part = ParticlePart.HEARTS;
        else if (id.contains("villager")) part = ParticlePart.VILLAGERS;
        else if (id.equals("note")) part = ParticlePart.NOTES;
        else if (id.contains("snow")) part = ParticlePart.SNOW;
        else if (id.equals("slime")) part = ParticlePart.SLIME;
        else if (id.contains("block") || id.equals("falling_dust")) part = ParticlePart.BLOCK_DEBRIS;
        else if (id.contains("item")) part = ParticlePart.ITEM_DEBRIS;
        else if (id.equals("cloud")) part = ParticlePart.CLOUDS;
        else if (id.contains("suspended") || id.contains("underwater")) part = ParticlePart.SUSPENDED;
        else if (id.equals("elder_guardian")) part = ParticlePart.MOB_APPEARANCE;
        else if (id.equals("block_marker")) part = ParticlePart.BARRIERS;
        return part != null && (current.particles & bit(part)) != 0;
    }
}
