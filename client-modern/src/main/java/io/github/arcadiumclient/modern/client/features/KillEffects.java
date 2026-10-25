// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KillEffects {
    private static final KillEffectTracker TRACKER = new KillEffectTracker();
    private static final int[] COLORS = {0xF2F5FF, 0xF05A5A, 0xFFA34D, 0x75E69A, 0x6BD9EA, 0xE88DBA};
    private static Object world;
    private static long ticks;
    private record Flash(double x, double y, double z, long born, long duration, int color, float[] positions) {}
    private static final java.util.ArrayList<Flash> FLASHES = new java.util.ArrayList<>();
    private KillEffects() {}

    public static void initialize() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (player == Minecraft.getInstance().player && KillEffectsConfig.getEnabled() && target instanceof LivingEntity &&
                    (!KillEffectsConfig.getPlayersOnly() || target instanceof Player)) TRACKER.hit(target.getId(), target.getUUID(), ticks);
            return InteractionResult.PASS;
        });
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; TRACKER.clear(); FLASHES.clear(); ticks = 0; }
        ticks++;
        if (!KillEffectsConfig.getEnabled() || mc.level == null) { TRACKER.clear(); FLASHES.clear(); return; }
        long now = System.nanoTime();
        FLASHES.removeIf(flash -> now - flash.born >= flash.duration);
        int window = Math.max(1, Math.min(10, KillEffectsConfig.getAttackWindow())) * 20;
        TRACKER.expire(ticks, window);
        for (var hit : TRACKER.pending()) {
            Entity target = mc.level.getEntity(hit.entityId());
            if (target instanceof LivingEntity living && living.getHealth() <= 0 && TRACKER.confirm(target.getId(), target.getUUID(), ticks, window)) play(target);
        }
    }
    private static void play(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        int mode = KillEffectsConfig.getEffect();
        if (mode == 0) {
            var random = new java.util.Random(target.getUUID().getLeastSignificantBits() ^ ticks);
            int count = Math.max(8, Math.min(64, KillEffectsConfig.getCount()));
            float[] points = new float[(count + 1) * 3];
            for (int i = 0; i <= count; i++) {
                points[i * 3] = i == 0 || i == count ? 0 : random.nextFloat() * .8f - .4f;
                points[i * 3 + 1] = i * 6f / count;
                points[i * 3 + 2] = i == 0 || i == count ? 0 : random.nextFloat() * .8f - .4f;
            }
            if (FLASHES.size() == 8) FLASHES.removeFirst();
            FLASHES.add(new Flash(target.getX(), target.getY(), target.getZ(), System.nanoTime(),
                (long) (Math.max(.3f, Math.min(2, KillEffectsConfig.getLifetime())) * 1_000_000_000L),
                COLORS[Math.max(0, Math.min(5, KillEffectsConfig.getColor()))], points));
        } else {
            int count = Math.max(8, Math.min(64, KillEffectsConfig.getCount()));
            ParticleOptions particle = switch (mode) {
                case 2 -> ParticleTypes.FLAME;
                case 3 -> ParticleTypes.HEART;
                default -> new DustParticleOptions(COLORS[Math.max(0, Math.min(5, KillEffectsConfig.getColor()))], 1);
            };
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count;
                double radius = mode == 4 ? 1 : level.random.nextDouble() * 0.8;
                double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
                var created = mc.particleEngine.createParticle(particle, target.getX() + x, target.getY() + (mode == 4 ? 0.1 : level.random.nextDouble() * target.getBbHeight()), target.getZ() + z,
                    mode == 4 ? 0 : x * 0.04, mode == 4 ? 0.01 : 0.05, mode == 4 ? 0 : z * 0.04);
                if (created != null) created.setLifetime(Math.max(6, Math.min(40, Math.round(KillEffectsConfig.getLifetime() * 20))));
            }
        }
        var sound = switch (KillEffectsConfig.getSound()) {
            case 1 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case 2 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 3 -> SoundEvents.NOTE_BLOCK_PLING.value();
            default -> null;
        };
        if (sound != null) level.playLocalSound(target.getX(), target.getY(), target.getZ(), sound, SoundSource.PLAYERS, Math.max(0, Math.min(1, KillEffectsConfig.getVolume())), 1, false);
    }
    public static void submit(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.state.LevelRenderState state, net.minecraft.client.renderer.SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (!KillEffectsConfig.getEnabled() || mc.options.hideGui || FLASHES.isEmpty() || state.cameraRenderState == null) return;
        var camera = state.cameraRenderState.pos;
        long now = System.nanoTime();
        for (var flash : FLASHES) {
            int alpha = Math.max(0, Math.min(255, (int) (255 * (1d - (double) (now - flash.born) / flash.duration))));
            if (alpha == 0) continue;
            pose.pushPose();
            pose.translate(flash.x - camera.x, flash.y - camera.y, flash.z - camera.z);
            collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (matrix, vertices) -> {
                for (int i = 0; i < flash.positions.length - 3; i += 3) {
                    float x = flash.positions[i], y = flash.positions[i + 1], z = flash.positions[i + 2];
                    float nx = flash.positions[i + 3], ny = flash.positions[i + 4], nz = flash.positions[i + 5];
                    NativeLines.segment(vertices, matrix, x, y, z, nx, ny, nz, flash.color & 0xFFFFFF | alpha << 24, 2f);
                }
            });
            pose.popPose();
        }
    }
}
