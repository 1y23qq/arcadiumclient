// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess;
import io.github.arcadiumclient.runtime.mixin.client.optimizations.HologramNameTagAccess;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ArmorStandRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.ArmorStandModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class FastInvisibleHolograms {
    private static ArmorStandRenderer knownRenderer;
    private static Model knownModel;
    private static Object armor, hand, skull;
    private static boolean knownSupported;
    public static long accelerated, equipmentFallbacks, rendererFallbacks, stateFallbacks;

    private FastInvisibleHolograms() { }

    public static boolean render(EntityRenderer<?> renderer, Entity entity, double x, double y, double z, float yaw, float delta) {
        if (!BetaOptimizationsConfig.getFastInvisibleHolograms()) return false;
        if (entity == null || entity.getClass() != ArmorStandEntity.class) return false;
        if (renderer == null || renderer.getClass() != ArmorStandRenderer.class) { rendererFallbacks++; return false; }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(delta)) {
            stateFallbacks++; return false;
        }
        ArmorStandEntity stand = (ArmorStandEntity) entity;
        ArmorStandRenderer nativeRenderer = (ArmorStandRenderer) renderer;
        if (!eligible(nativeRenderer, stand, Minecraft.getInstance().player)) return false;

        // Match the state at the original living renderer's name-tag call.
        GlStateManager.enableAlphaTest();
        GlStateManager.depthMask(true);
        GlStateManager.disableRescaleNormal();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableCull();
        if(RenderHookAllocationControl.enabled&&HologramNamePreparation.preparedSites==2){
            Object previous=HologramNamePreparation.enter(stand);
            try{((HologramNameTagAccess) nativeRenderer).arcadium$renderOriginalNameTag(stand,x,y,z);}
            finally{HologramNamePreparation.exit(previous);}
        }else ((HologramNameTagAccess) nativeRenderer).arcadium$renderOriginalNameTag(stand,x,y,z);
        accelerated++;
        return true;
    }

    public static boolean eligible(ArmorStandRenderer renderer, ArmorStandEntity stand, PlayerEntity viewer) {
        try { return nativeEntity(renderer, stand, viewer); }
        catch (RuntimeException malformed) { stateFallbacks++; return false; }
    }

    private static boolean nativeEntity(ArmorStandRenderer renderer, ArmorStandEntity stand, PlayerEntity viewer) {
        if (renderer == null || renderer.getClass() != ArmorStandRenderer.class || stand == null || stand.getClass() != ArmorStandEntity.class
                || !(renderer instanceof LivingPipelineAccess access) || !(renderer instanceof HologramNameTagAccess)
                || access.arcadium$outline() || GpuPlayerPipeline.hasForgeHooks()
                || !stockRenderer(renderer, access.arcadium$layers())) {
            rendererFallbacks++; return false;
        }
        if (viewer == null || !stand.isInvisible() || !stand.isInvisibleTo(viewer) || !stand.hasCustomName() || !stand.isCustomNameVisible()
                || stand.shouldRenderOnFire() || stand.damagedTimer != 0 || stand.deathTicks != 0 || stand.vehicle != null || stand.rider != null
                || !nativeSize(stand.width, stand.height) || !Double.isFinite(stand.x) || !Double.isFinite(stand.y) || !Double.isFinite(stand.z)
                || !Float.isFinite(stand.bodyYaw) || !Float.isFinite(stand.lastBodyYaw) || !Float.isFinite(stand.headYaw)
                || !Float.isFinite(stand.lastHeadYaw) || !Float.isFinite(stand.pitch) || !Float.isFinite(stand.lastPitch)) {
            stateFallbacks++; return false;
        }
        ItemStack[] equipment = stand.getEquipment();
        if (equipment == null || equipment.length != 5) { equipmentFallbacks++; return false; }
        for (ItemStack stack : equipment) if (stack != null) { equipmentFallbacks++; return false; }
        return true;
    }

    static boolean nativeSize(float width, float height) {
        return width == .5f && height == 1.975f || width == .25f && height == .9875f || width == 0f && height == 0f;
    }

    private static boolean stockRenderer(ArmorStandRenderer renderer, List<?> layers) {
        if (layers == null || layers.size() != 3) return false;
        Model model = renderer.getModel();
        Object currentArmor = layers.get(0), currentHand = layers.get(1), currentSkull = layers.get(2);
        if (knownRenderer == renderer && knownModel == model && armor == currentArmor && hand == currentHand && skull == currentSkull) return knownSupported;
        knownRenderer = renderer; knownModel = model; armor = currentArmor; hand = currentHand; skull = currentSkull;
        knownSupported = model != null && model.getClass() == ArmorStandModel.class
                && currentArmor != null && currentArmor.getClass().getSuperclass() == ArmorLayer.class
                && currentArmor.getClass().getEnclosingClass() == ArmorStandRenderer.class
                && currentHand != null && currentHand.getClass() == ItemInHandLayer.class
                && currentSkull != null && currentSkull.getClass() == WornSkullLayer.class;
        return knownSupported;
    }

    public static String status() {
        return !BetaOptimizationsConfig.getFastInvisibleHolograms() ? "Invisible holograms: Off"
                : "Invisible holograms: " + accelerated + " name-only | " + (equipmentFallbacks + rendererFallbacks + stateFallbacks) + " original | Typed names: " + HologramNamePreparation.batchTypedCalls + "/" + HologramNamePreparation.layoutTypedCalls;
    }
}
