// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class ChatImagePreviews {
    private static final ImagePreviewCache IMAGES = new ImagePreviewCache(new PublicImageFetcher());
    private static final LinkedHashMap<String, Uploaded> TEXTURES = new LinkedHashMap<>(4, .75f, true);
    private static long serial;
    private record Uploaded(BufferedImage source, DynamicTexture texture, Identifier id) { }

    private ChatImagePreviews() { }

    public static boolean render(Text text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!ChatTweaksConfig.getImagePreview() || text == null || screenWidth < 30 || screenHeight < 30) return false;
        ClickEvent click = text.getStyle().getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_URL) return false;
        String link = click.getValue();
        BufferedImage image = IMAGES.request(link);
        if (image == null) return false;
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        Uploaded uploaded = TEXTURES.get(link);
        if (uploaded == null || uploaded.source != image) {
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            DynamicTexture texture = new DynamicTexture(image);
            Identifier id = new Identifier("arcadiumclient", "chat-preview/" + serial++);
            manager.register(id, texture);
            GlStateManager.bindTexture(previous);
            if (uploaded != null) manager.close(uploaded.id);
            uploaded = new Uploaded(image, texture, id); TEXTURES.put(link, uploaded);
            while (TEXTURES.size() > 4) manager.close(TEXTURES.remove(TEXTURES.keySet().iterator().next()).id);
        }
        float setting = ChatTweaksConfig.getImagePreviewWidth();
        if (!Float.isFinite(setting)) setting = 25;
        double maximumWidth = Screen.isShiftDown() ? screenWidth - 24 : screenWidth * Math.max(5, Math.min(50, setting)) / 100.0;
        double scale = Math.min(maximumWidth / image.getWidth(), (screenHeight - 24.0) / image.getHeight());
        int width = Math.max(1, (int) (image.getWidth() * scale)), height = Math.max(1, (int) (image.getHeight() * scale));
        int x = Math.max(6, Math.min(screenWidth - width - 6, mouseX + 12));
        int y = Math.max(6, Math.min(screenHeight - height - 6, mouseY + 12));
        if (Screen.isShiftDown()) { x = (screenWidth - width) / 2; y = (screenHeight - height) / 2; }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(.04f, .05f, .06f, .96f);
            quad(x - 3, y - 3, width + 6, height + 6, false);
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, uploaded.texture.getGlId());
            GL11.glColor4f(1, 1, 1, 1); quad(x, y, width, height, true);
        } finally { GL11.glPopAttrib(); }
        return true;
    }

    private static void quad(int x, int y, int width, int height, boolean texture) {
        GL11.glBegin(GL11.GL_QUADS);
        if (texture) GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x, y, 0);
        if (texture) GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x, y + height, 0);
        if (texture) GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x + width, y + height, 0);
        if (texture) GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x + width, y, 0);
        GL11.glEnd();
    }
}
