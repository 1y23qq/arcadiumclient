// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.particle.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.mob.GolemEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.water.WaterMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.world.WorldView;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy.EntityPart;

public final class NoRender {
    private static volatile NoRenderPolicy policy = NoRenderPolicy.OFF;
    private static final NoRenderPolicy.RebuildTracker chunks = new NoRenderPolicy.RebuildTracker();

    private NoRender() {}

    public static void initialize() {
        NoRenderConfig.INSTANCE.preload();
        refresh();
        EventManager.register(TickEvent.End.class, event -> refresh());
    }

    public static NoRenderPolicy policy() { return policy; }

    public static boolean hidesLeaves() {
        return (policy.blocks & NoRenderPolicy.bit(NoRenderPolicy.BlockPart.LEAVES)) != 0;
    }

    public static boolean hideOpaqueLeaves(Block block) {
        return hidesLeaves() && (block == Blocks.LEAVES || block == Blocks.LEAVES2);
    }

    public static void refresh() {
        NoRenderPolicy next = NoRenderConfig.INSTANCE.snapshot();
        policy = next;
        Minecraft minecraft = Minecraft.getInstance();
        if (chunks.update(minecraft.world, next.chunkKey()) && minecraft.worldRenderer != null) minecraft.worldRenderer.reload();
    }

    public static boolean hideBlock(Block block, int x, int y, int z, WorldView world) {
        NoRenderPolicy current = policy;
        if (current.blocks == 0) return false;
        int id = Block.getId(block);
        if (id != 31 && id != 175) return current.hides(NoRenderPolicy.blockPart(id, 0));
        int metadata = world.getBlockMetadata(x, y, z);
        if (id == 175 && (metadata & 8) != 0 && world.getBlock(x, y - 1, z) == block)
            metadata = world.getBlockMetadata(x, y - 1, z);
        return current.hides(NoRenderPolicy.blockPart(id, metadata));
    }

    public static boolean hideEntity(Entity entity) {
        NoRenderPolicy current = policy;
        if (current.entities == 0 || entity == Minecraft.getInstance().player) return false;
        return current.hides(entityPart(entity));
    }

    public static EntityPart entityPart(Entity entity) {
        if (entity instanceof PlayerEntity) return EntityPart.PLAYERS;
        if (entity instanceof VillagerEntity) return EntityPart.VILLAGERS;
        if (entity instanceof GolemEntity) return EntityPart.GOLEMS;
        if (entity instanceof BatEntity) return EntityPart.BATS;
        if (entity instanceof WaterMobEntity) return EntityPart.SQUIDS;
        if (entity instanceof Monster || entity instanceof EnderDragonEntity) return EntityPart.HOSTILE_MOBS;
        if (entity instanceof AnimalEntity) return EntityPart.ANIMALS;
        if (entity instanceof ItemFrameEntity) return EntityPart.ITEM_FRAMES;
        if (entity instanceof PaintingEntity) return EntityPart.PAINTINGS;
        if (entity instanceof ItemEntity) return EntityPart.DROPPED_ITEMS;
        if (entity instanceof ExperienceOrbEntity) return EntityPart.EXPERIENCE_ORBS;
        if (entity instanceof ArrowEntity) return EntityPart.ARROWS;
        if (entity instanceof FishingBobberEntity) return EntityPart.FISHING_BOBBERS;
        if (entity instanceof SnowballEntity) return EntityPart.SNOWBALLS;
        if (entity instanceof EggEntity) return EntityPart.EGGS;
        if (entity instanceof EnderPearlEntity) return EntityPart.ENDER_PEARLS;
        if (entity instanceof PotionEntity || entity instanceof ExperienceBottleEntity) return EntityPart.POTIONS;
        if (entity instanceof FireballEntity || entity instanceof WitherSkullEntity) return EntityPart.FIREBALLS;
        if (entity instanceof PrimedTntEntity) return EntityPart.TNT;
        if (entity instanceof FallingBlockEntity) return EntityPart.FALLING_BLOCKS;
        if (entity instanceof BoatEntity) return EntityPart.BOATS;
        if (entity instanceof MinecartEntity) return EntityPart.MINECARTS;
        if (entity instanceof LightningBoltEntity) return EntityPart.LIGHTNING;
        if (entity instanceof FireworksEntity) return EntityPart.FIREWORKS;
        if (entity instanceof EnderCrystalEntity) return EntityPart.END_CRYSTALS;
        return null;
    }

    public static boolean hideParticle(Particle particle) {
        NoRenderPolicy current = policy;
        if (current.particles == 0) return false;
        int type = ((NoRenderParticle) particle).arcadium$particleType();
        if (type < 0) type = directParticleType(particle);
        return current.hidesParticle(type);
    }

    private static int directParticleType(Particle particle) {
        if (particle instanceof FireworksParticles__Spark || particle instanceof FireworksParticles__Overlay) return 3;
        if (particle instanceof FootstepParticle) return 28;
        if (particle instanceof EntityPickupParticle) return 40;
        if (particle instanceof BlockParticle) return 37;
        if (particle instanceof RainSplashParticle) return 39;
        if (particle instanceof WaterBubbleParticle) return 4;
        if (particle instanceof WaterSplashParticle) return 5;
        if (particle instanceof WakeParticle) return 6;
        if (particle instanceof SmokeParticle) return 11;
        if (particle instanceof HugeExplosionParticle) return 2;
        if (particle instanceof LargeExplosionParticle) return 1;
        if (particle instanceof ExplosionParticle) return 0;
        if (particle instanceof EnchantingParticle) return 25;
        if (particle instanceof FlameParticle) return 26;
        if (particle instanceof LavaParticle) return 27;
        if (particle instanceof NoteParticle) return 23;
        if (particle instanceof PortalParticle) return 24;
        if (particle instanceof RedstoneParticle) return 30;
        if (particle instanceof SnowShovelParticle) return 32;
        if (particle instanceof SuspendedParticle) return 7;
        if (particle instanceof DepthSuspendParticle) return 8;
        if (particle instanceof CloudParticle) return 29;
        if (particle instanceof SpellParticle) return 13;
        return -1;
    }

    public static boolean hide(NoRenderPolicy.Effect effect) { return policy.hides(effect); }
}
