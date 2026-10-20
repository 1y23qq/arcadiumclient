// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class LegacyGuiBlur040Qa {
    private LegacyGuiBlur040Qa() {}

    static void afterCapture(Minecraft client, String name, BufferedImage image, Map<String, Object> result) throws Exception {
        if (!name.equals("040-1710-inventory-scale.png")) return;
        Class<?> renderer = Class.forName("eu.midnightdust.blur.BlurRenderer");
        Class<?> config = Class.forName("eu.midnightdust.blur.BlurConfig");
        Object shader = field(renderer, "shader");
        require(shader != null, "The real inventory GUI blur shader did not initialize");
        int passes = ((Collection<?>) call(shader, "blur$getPasses")).size();
        require(passes == 4, "The native general GUI blur did not execute its four authored passes");
        float progress = ((Number) field(renderer, "blurProgress")).floatValue();
        require(progress > .95f, "The inventory GUI blur did not finish its actual fade");
        require((boolean) stat(renderer.getName(), "shouldBlur", client.screen), "The active inventory was not selected for general blur");
        boolean containers = config.getField("blurContainers").getBoolean(null);
        try {
            config.getField("blurContainers").setBoolean(null, false);
            require(!(boolean) stat(renderer.getName(), "shouldBlur", client.screen), "The general container blur switch was ignored");
        } finally { config.getField("blurContainers").setBoolean(null, containers); }
        BufferedImage world = ImageIO.read(client.gameDir.toPath().resolve("screenshots/040-1710-world-hud.png").toFile());
        double visible = meanLuminance(image);
        require(visible > 4, "The inventory framebuffer was empty after general GUI blur: " + visible);
        double background = backgroundLuminance(image);
        require(background > 4, "The world background became empty after general GUI blur: " + background);
        double before = textureContrast(world), after = textureContrast(image);
        require(before > .00001 && after > .00001 && after < before * .93, "The actual inventory background did not soften world texture detail: " + before + " -> " + after);
        client.getRenderTarget().bindWrite(true);
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean enabled = config.getField("enabled").getBoolean(null);
        try {
            config.getField("enabled").setBoolean(null, false);
            stat(renderer.getName(), "render");
            require(field(renderer, "shader") == null && GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == framebuffer,
                "Disabling general GUI blur did not close its pipeline and restore the real Minecraft framebuffer");
        } finally { config.getField("enabled").setBoolean(null, enabled); }
        int error = GL11.glGetError();
        require(error == GL11.GL_NO_ERROR, "General GUI blur introduced an OpenGL error: " + error);
        result.put("nativeGeneralGuiBlur", Map.of("shaderPasses", passes, "fadeProgress", progress,
            "containersToggleHonored", true, "worldTextureContrast", before, "inventoryTextureContrast", after,
            "contrastRatio", after / before, "frameMeanLuminance", visible, "backgroundMeanLuminance", background,
            "disableClosesAndRestoresFramebuffer", true, "screenshot", name));
    }

    private static double textureContrast(BufferedImage image) {
        double total = 0; int samples = 0;
        int top = image.getHeight() * 55 / 100, bottom = image.getHeight() * 75 / 100;
        for (int y = top; y < bottom; y++) for (int x = 16; x < Math.min(220, image.getWidth() / 4); x++) {
            double a = luminance(image.getRGB(x, y)), b = luminance(image.getRGB(x + 1, y));
            total += Math.abs(a - b) / Math.max(1, a + b); samples++;
        }
        return total / Math.max(1, samples);
    }

    private static double luminance(int color) { return ((color >> 16 & 255) * 3 + (color >> 8 & 255) * 6 + (color & 255)) / 10.; }

    private static double meanLuminance(BufferedImage image) {
        double sum = 0;
        for (int y = 0; y < image.getHeight(); y += 4) for (int x = 0; x < image.getWidth(); x += 4) sum += luminance(image.getRGB(x, y));
        return sum / (((image.getHeight() + 3) / 4) * ((image.getWidth() + 3) / 4));
    }

    private static double backgroundLuminance(BufferedImage image) {
        double sum = 0; int count = 0;
        for (int y = image.getHeight() * 55 / 100; y < image.getHeight() * 75 / 100; y++) for (int x = 16; x < Math.min(220, image.getWidth() / 4); x++) {
            sum += luminance(image.getRGB(x, y)); count++;
        }
        return sum / Math.max(1, count);
    }
}
