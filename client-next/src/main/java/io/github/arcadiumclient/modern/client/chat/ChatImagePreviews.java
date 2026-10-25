// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.chat;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ChatImagePreviews {
    private record Uploaded(BufferedImage source, Identifier id) {}
    private static final ImagePreviewCache IMAGES = new ImagePreviewCache(new PublicImageFetcher());
    private static final LinkedHashMap<String, Uploaded> TEXTURES = new LinkedHashMap<>(4, .75f, true);
    private static long serial;
    private ChatImagePreviews() {}

    public static void render(GuiGraphicsExtractor graphics, Style style, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (style == null || style.getClickEvent() == null) return;
        var click = style.getClickEvent();
        if (ChatTweaksConfig.getSafeChatClicks()) {
            String target = switch (click) {
                case ClickEvent.OpenUrl link -> link.uri().toString();
                case ClickEvent.RunCommand command -> command.command();
                case ClickEvent.SuggestCommand command -> command.command();
                default -> null;
            };
            if (target != null) graphics.setTooltipForNextFrame(Component.literal(target), mouseX, mouseY);
        }
        if (!ChatTweaksConfig.getImagePreview() || !(click instanceof ClickEvent.OpenUrl link)) return;
        String url = link.uri().toString();
        BufferedImage image = IMAGES.request(url);
        if (image == null) return;
        Minecraft mc = Minecraft.getInstance();
        Uploaded uploaded = TEXTURES.get(url);
        if (uploaded == null || uploaded.source != image) {
            NativeImage pixels = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels.setPixel(x, y, image.getRGB(x, y));
            Identifier id = Identifier.fromNamespaceAndPath("arcadium", "chat-preview/" + serial++);
            mc.getTextureManager().register(id, new DynamicTexture(() -> "Arcadium chat preview", pixels));
            if (uploaded != null) mc.getTextureManager().release(uploaded.id);
            uploaded = new Uploaded(image, id);
            TEXTURES.put(url, uploaded);
            while (TEXTURES.size() > 4) mc.getTextureManager().release(TEXTURES.remove(TEXTURES.keySet().iterator().next()).id);
        }
        boolean large = GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        float percent = ChatTweaksConfig.getImagePreviewWidth();
        if (!Float.isFinite(percent)) percent = 25;
        double maximum = large ? screenWidth - 24 : screenWidth * Math.max(5, Math.min(50, percent)) / 100d;
        double scale = Math.min(maximum / image.getWidth(), (screenHeight - 24d) / image.getHeight());
        int width = Math.max(1, (int) (image.getWidth() * scale)), height = Math.max(1, (int) (image.getHeight() * scale));
        int x = Math.max(6, Math.min(screenWidth - width - 6, mouseX + 12)), y = Math.max(6, Math.min(screenHeight - height - 6, mouseY + 12));
        if (large) { x = (screenWidth - width) / 2; y = (screenHeight - height) / 2; }
        graphics.nextStratum();
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xF50A0D10);
        graphics.blit(RenderPipelines.GUI_TEXTURED, uploaded.id, x, y, 0, 0, width, height, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
    }
    public static void close() {
        IMAGES.close();
        TEXTURES.values().forEach(value -> Minecraft.getInstance().getTextureManager().release(value.id));
        TEXTURES.clear();
    }
}
