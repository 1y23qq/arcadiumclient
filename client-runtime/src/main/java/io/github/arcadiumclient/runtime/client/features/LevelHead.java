// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.runtime.client.bedwars.BedwarsData;
import io.github.arcadiumclient.runtime.client.bedwars.BedwarsStats;
import io.github.arcadiumclient.runtime.client.bedwars.HypixelDataService;
import io.github.arcadiumclient.runtime.client.bedwars.PlayerNameUtil;
import io.github.arcadiumclient.runtime.client.branding.NametagText;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Formatting;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.BufferUtils;

public final class LevelHead {
    private static int ticks;
    private static BedwarsData.Policy lastPolicy;
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(4);
    private LevelHead() { }

    public static boolean onHypixel() {
        var entry = Minecraft.getInstance().getCurrentServerEntry();
        if (entry == null) return false;
        String host = entry.ip.toLowerCase(java.util.Locale.ROOT).trim().split(":", 2)[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public static void tick() {
        if (++ticks % 20 != 0 || !LevelHeadConfig.getEnabled() || !onHypixel()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || mc.player == null) return;
        BedwarsData.Policy policy = new BedwarsData.Policy(true, LevelHeadConfig.getUseHypixelApiKey(), LevelHeadConfig.getHypixelApiKey());
        BedwarsData data = HypixelDataService.data();
        if (!policy.same(lastPolicy)) { data.retryUnavailable(); lastPolicy = policy; }
        ArrayList<PlayerEntity> players = new ArrayList<>();
        for (Object value : mc.world.players)
            if (value instanceof PlayerEntity player && (player != mc.player || LevelHeadConfig.getShowSelf())) players.add(player);
        players.sort(Comparator.comparingDouble(player -> distanceSquared(player, mc.player)));
        int count = 0, requests = 0;
        int distance = Math.max(8, Math.min(64, LevelHeadConfig.getDistance()));
        for (PlayerEntity player : players) {
            if (distanceSquared(player, mc.player) > distance * distance || ++count > 64) break;
            if (data.present(player.getUuid()) != null) continue;
            if (requests++ >= 8 || data.pendingCount() >= 24) break;
            data.get(player.getUuid(), player.getGameProfile().getName(), policy);
        }
    }

    private static double distanceSquared(Entity a, Entity b) {
        double x = a.x - b.x, y = a.y - b.y, z = a.z - b.z;
        return x * x + y * y + z * z;
    }

    public static String label(BedwarsStats stats, int mode) {
        if (stats == null || stats.isUnavailable() || stats.isNicked()) return null;
        int value = mode == 1 ? stats.getStar() : stats.getNetworkLevel();
        return value < 0 ? null : prefix(mode) + value;
    }

    public static String prefix(int mode) { return mode == 1 ? "Bedwars Level " : "Level "; }

    public static String labelFor(BedwarsStats stats, UUID id, String name, int mode) {
        return stats != null && id != null && id.equals(stats.getUuid()) && PlayerNameUtil.sameAccountName(name, stats.getName())
                ? label(stats, mode) : null;
    }

    public static void render(Entity entity, String name, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (!LevelHeadConfig.getEnabled() || mc.options.hideGui || !onHypixel() || !(entity instanceof PlayerEntity player)
                || player == mc.player && !LevelHeadConfig.getShowSelf()
                || player.isSneaking() || player.isInvisible() || NoRender.hideEntity(entity)
                || NoRender.hide(NoRenderPolicy.Effect.NAME_TAGS) || !playerLabel(name, entity.getDisplayName().getFormattedString())) return;
        int distance = Math.max(8, Math.min(64, LevelHeadConfig.getDistance()));
        if (x * x + y * y + z * z > distance * distance) return;
        String text = labelFor(HypixelDataService.data().present(entity.getUuid()), entity.getUuid(),
                player.getGameProfile().getName(), LevelHeadConfig.getLevel());
        if (text == null) return;
        var camera = mc.getEntityRenderDispatcher();
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), targetRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), targetAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        float red = COLOR.get(0), green = COLOR.get(1), blue = COLOR.get(2), opacity = COLOR.get(3);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GlStateManager.pushMatrix();
        try {
            float scale = 0.02666667f * Math.max(0.5f, Math.min(2, LevelHeadConfig.getScale()));
            GlStateManager.translated(x, y + entity.height + 0.5 + 9 * scale + Math.max(0, Math.min(1, LevelHeadConfig.getHeightOffset())), z);
            GlStateManager.rotatef(-camera.cameraYaw, 0, 1, 0);
            GlStateManager.rotatef(camera.cameraPitch, 1, 0, 0);
            GlStateManager.scalef(-scale, -scale, scale);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.depthMask(false);
            float left = -mc.textRenderer.getWidth(text) / 2f;
            if (LevelHeadConfig.getBackground()) {
                int color = LevelHeadConfig.getBackgroundColor().getRGB();
                GlStateManager.disableTexture();
                GlStateManager.color4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex3f(left - 2, -1, 0.01f); GL11.glVertex3f(left - 2, 9, 0.01f);
                GL11.glVertex3f(-left + 2, 9, 0.01f); GL11.glVertex3f(-left + 2, -1, 0.01f);
                GL11.glEnd();
            }
            GlStateManager.enableTexture();
            GlStateManager.clearColor();
            GlStateManager.color4f(1, 1, 1, 1);
            String prefix = prefix(LevelHeadConfig.getLevel());
            draw(mc, prefix, left, LevelHeadConfig.getPrefixColor().getRGB());
            draw(mc, text.substring(prefix.length()), left + mc.textRenderer.getWidth(prefix), LevelHeadConfig.getSuffixColor().getRGB());
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GlStateManager.bindTexture(texture);
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (textured) GlStateManager.enableTexture(); else GlStateManager.disableTexture();
            GlStateManager.depthMask(depthMask);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.blendFuncSeparate(sourceRgb, targetRgb, sourceAlpha, targetAlpha);
            if (alpha) GlStateManager.enableAlphaTest(); else GlStateManager.disableAlphaTest();
            GlStateManager.clearColor();
            GlStateManager.color4f(red, green, blue, opacity);
        }
    }

    private static void draw(Minecraft mc, String text, float x, int color) {
        mc.textRenderer.draw(text, x, 0, color, LevelHeadConfig.getTextShadow());
    }

    private static boolean playerLabel(String label, String expected) {
        if (label == null || expected == null) return false;
        if (label.equals(expected)) return true;
        return Formatting.strip(NametagText.withoutTrailingPadding(NickHider.display(label)))
                .equals(Formatting.strip(NametagText.withoutTrailingPadding(NickHider.display(expected))));
    }
}
