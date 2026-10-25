// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

public final class ModernBrand {
    private ModernBrand() {}
    public static void installIcon(Minecraft mc) {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) return;
        try (var input = ModernBrand.class.getResourceAsStream("/assets/arcadiumclient/icon.png")) {
            if (input == null) return;
            BufferedImage source = ImageIO.read(input);
            int[] sizes = {16, 32, 48, 64, 128};
            ByteBuffer[] pixels = new ByteBuffer[sizes.length];
            try (GLFWImage.Buffer icons = GLFWImage.malloc(sizes.length)) {
                for (int index = 0; index < sizes.length; index++) {
                    int size = sizes[index];
                    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    var graphics = scaled.createGraphics();
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, size, size, null);
                    graphics.dispose();
                    ByteBuffer rgba = pixels[index] = MemoryUtil.memAlloc(size * size * 4);
                    for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                        int color = scaled.getRGB(x, y);
                        rgba.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
                    }
                    rgba.flip();
                    icons.position(index).width(size).height(size).pixels(rgba);
                }
                icons.position(0);
                GLFW.glfwSetWindowIcon(mc.getWindow().handle(), icons);
            } finally { for (ByteBuffer image : pixels) if (image != null) MemoryUtil.memFree(image); }
        } catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("Arcadium").warn("Could not apply the game window icon", failure); }
    }
}
