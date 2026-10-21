// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Mod(modid = "arcadium_qa", name = "Arcadium Forge Verification", version = "1", acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class ForgeRuntimeQa {
    private int frames;
    private boolean captured;

    @Mod.EventHandler public void initialize(FMLInitializationEvent event) {
        Path root = Minecraft.func_71410_x().field_71412_D.toPath();
        if (!Files.isRegularFile(root.resolve(".arcadium-forge-probe")))
            throw new IllegalStateException("Forge verification requires an isolated test profile.");
        FMLCommonHandler.instance().bus().register(this);
        if (Boolean.getBoolean("arcadium.qa.menuFlow")) FMLCommonHandler.instance().bus().register(new ForgeMenuFlowQa());
        if (Boolean.getBoolean("arcadium.qa.worldFlow")) FMLCommonHandler.instance().bus().register(new ForgeWorldFlowQa());
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (captured || event.phase != TickEvent.Phase.END || ++frames < 120) return;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71462_r == null) return;
        captured = true;
        try {
            capture(mc, "forge-main-menu", frames);
        } catch (Exception error) { throw new IllegalStateException("Could not capture the Forge verification frame.", error); }
    }

    static void capture(Minecraft mc, String stem, int frames) throws Exception {
            int width = mc.field_71443_c, height = mc.field_71440_d;
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
            int framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                GL11.glReadBuffer(GL11.GL_FRONT);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
                GL11.glReadBuffer(readBuffer);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                image.setRGB(x, height - y - 1, (pixels.get(offset + 3) & 255) << 24 | (pixels.get(offset) & 255) << 16
                    | (pixels.get(offset + 1) & 255) << 8 | (pixels.get(offset + 2) & 255));
            }
            Path root = mc.field_71412_D.toPath();
            ImageIO.write(image, "PNG", root.resolve(stem + ".png").toFile());
            var report = new LinkedHashMap<String, Object>();
            report.put("processId", ProcessHandle.current().pid());
            report.put("capturedAt", java.time.Instant.now().toString());
            report.put("screen", mc.field_71462_r == null ? null : mc.field_71462_r.getClass().getName());
            report.put("width", width); report.put("height", height); report.put("renderedFrames", frames);
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            report.put("nativeForgeMods", Loader.instance().getActiveModList().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).toList());
            Files.writeString(root.resolve(stem + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
