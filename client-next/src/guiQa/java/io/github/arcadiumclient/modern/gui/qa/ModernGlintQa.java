// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.arcadiumclient.modern.client.glint.GlintConfig;
import io.github.arcadiumclient.modern.client.glint.GlintEffectOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.polyfrost.compose.render.PolyColor;

final class ModernGlintQa {
    private record Color(int argb, boolean chroma, float speed) {
        Color(PolyColor color) { this(color.getRawArgb(), color.getChroma(), color.getChromaSpeed()); }
        void restore(PolyColor color) { color.setArgb(argb); color.setChroma(chroma); color.setChromaSpeed(speed); }
    }
    private record Effect(boolean individual, Color first, Color second, float speed, float firstRotation, float secondRotation, float scale) {
        Effect(GlintEffectOptions effect) { this(effect.getIndividualStrokes(), new Color(effect.getStrokeOneColor()), new Color(effect.getStrokeTwoColor()), effect.getSpeed(), effect.getStrokeRotOne(), effect.getStrokeRotTwo(), effect.getScale()); }
        void restore(GlintEffectOptions effect) {
            effect.setIndividualStrokes(individual); first.restore(effect.getStrokeOneColor()); second.restore(effect.getStrokeTwoColor());
            effect.setSpeed(speed); effect.setStrokeRotOne(firstRotation); effect.setStrokeRotTwo(secondRotation); effect.setScale(scale);
        }
    }

    private final Minecraft minecraft;
    private final Path output;
    private final GlintConfig config = GlintConfig.INSTANCE;
    private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final boolean enabled = config.getEnabled();
    private final boolean armorOff = config.getArmorGlintToggle();
    private final Color armorColor = new Color(config.getArmorColor());
    private final boolean[] potions = {config.getPotionGlint(), config.getPotionGlintBackground(), config.getPotionGlintSize(), config.getPotionGlintForeground(), config.getPotionBasedColor()};
    private final Effect gui = new Effect(config.getGuiItem());
    private final Effect shiny = new Effect(config.getShinyPots());
    private final ItemStack oldChest;
    private final Screen oldScreen;
    private final long started = System.nanoTime();
    private Fixture screen;
    private int phase;
    private int ticks;
    private boolean waiting;
    private boolean complete;
    private int[] plain;
    private int[] baselineSword;
    private int[] maskedPotion;
    private int[] coloredArmor;
    private int[] originalArmor;

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var test = new ModernGlintQa(minecraft, output);
        test.start();
        return test.completion;
    }

    private ModernGlintQa(Minecraft minecraft, Path output) {
        this.minecraft = minecraft;
        this.output = output;
        oldChest = minecraft.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        oldScreen = minecraft.gui.screen();
    }

    private void start() {
        try {
            NativeRuntimeProbe.beginGlint();
            config.setEnabled(false);
            minecraft.player.setItemSlot(EquipmentSlot.CHEST, foil(Items.DIAMOND_CHESTPLATE));
            screen = new Fixture();
            minecraft.gui.setScreen(screen);
            report.put("nativePipeline", "GuiGraphicsExtractor.item and InventoryScreen.extractEntityInInventoryFollowsMouse");
            report.put("fixtureFoil", "DataComponents.ENCHANTMENT_GLINT_OVERRIDE");
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private void tick() {
        require(System.nanoTime() - started < 50_000_000_000L, "Glint GPU QA timed out");
        if (minecraft.gui.overlay() != null || ++ticks < 20) return;
        switch (phase) {
            case 0 -> capture("glint-native", image -> {
                plain = crop(image, screen.itemX(1), 50, 16 * screen.scale(), 16 * screen.scale());
                baselineSword = crop(image, screen.itemX(0), 50, 16 * screen.scale(), 16 * screen.scale());
                originalArmor = armorCrop(image);
                config.setEnabled(true);
                configure(config.getGuiItem()); configure(config.getShinyPots());
                config.getArmorColor().setArgb(0xFF20FF60); config.getArmorColor().setChroma(false);
                config.setArmorGlintToggle(false);
                config.setPotionGlint(true); config.setPotionGlintBackground(true); config.setPotionGlintSize(false);
                config.setPotionGlintForeground(false); config.setPotionBasedColor(false);
            });
            case 1 -> capture("glint-red-cyan-green-armor", image -> {
                int[] actual = crop(image, screen.itemX(0), 50, 16 * screen.scale(), 16 * screen.scale());
                int red = shifted(actual, plain, 0), cyan = shifted(actual, plain, 1);
                report.put("itemStrokeBaseline", "Adjacent native unenchanted sword with identical geometry, lighting, scale and texture");
                report.put("nativeGlintChangedPixels", differences(actual, baselineSword));
                report.put("itemStrokePixels", Map.of("redShift", red, "cyanShift", cyan));
                report.put("actualGuiStrokeVertices", Map.of("redStroke0", NativeRuntimeProbe.guiVertices(0, 0xFFFF2020), "cyanStroke1", NativeRuntimeProbe.guiVertices(1, 0xFF20E0FF)));
                require(NativeRuntimeProbe.guiVertices(0, 0xFFFF2020) > 0 && NativeRuntimeProbe.guiVertices(1, 0xFF20E0FF) > 0,
                    "Both configured GUI stroke tints did not reach actual native vertex buffers");
                require(red >= 3 && cyan >= 3, "Custom item glint did not produce both red and cyan native GPU strokes: " + red + "/" + cyan);
                verifyPlain(image);
                maskedPotion = crop(image, screen.itemX(2), 50, 16 * screen.scale(), 16 * screen.scale());
                coloredArmor = armorCrop(image);
                int green = shifted(coloredArmor, originalArmor, 2);
                report.put("armorGreenShiftPixels", green);
                require(green >= 3, "Worn armor did not render the configured green glint");
                config.setArmorGlintToggle(true);
            });
            case 2 -> capture("glint-armor-disabled", image -> {
                verifyPlain(image);
                int[] hiddenArmor = armorCrop(image);
                int changed = differences(hiddenArmor, coloredArmor);
                report.put("armorOffChangedPixels", changed);
                require(changed >= 8, "Disable Armor Glint did not affect the native equipped armor image");
                config.setPotionGlintSize(true);
            });
            case 3 -> capture("glint-potion-full-slot", image -> {
                verifyPlain(image);
                int[] full = crop(image, screen.itemX(2), 50, 16 * screen.scale(), 16 * screen.scale());
                int changed = differences(full, maskedPotion);
                report.put("fullSlotPotionChangedPixels", changed);
                require(changed >= 8, "Full-slot shiny potion did not change the native potion image");
            });
            case 4 -> finish(null);
            default -> throw new IllegalStateException("Unknown Glint QA phase " + phase);
        }
    }

    private void configure(GlintEffectOptions effect) {
        effect.setIndividualStrokes(true);
        effect.getStrokeOneColor().setArgb(0xFFFF2020); effect.getStrokeOneColor().setChroma(false);
        effect.getStrokeTwoColor().setArgb(0xFF20E0FF); effect.getStrokeTwoColor().setChroma(false);
        effect.setStrokeRotOne(-50); effect.setStrokeRotTwo(10); effect.setSpeed(2); effect.setScale(1);
    }

    private int[] armorCrop(NativeImage image) { return crop(image, screen.width - 145, 25, 130, screen.height - 40); }

    private void verifyPlain(NativeImage image) {
        int changed = differences(plain, crop(image, screen.itemX(1), 50, 16 * screen.scale(), 16 * screen.scale()));
        report.put("normalItemChangedPixels", changed);
        require(changed == 0, "Glint preferences changed a normal unenchanted item texture");
    }

    private int[] crop(NativeImage image, int x, int y, int width, int height) {
        float ratioX = image.getWidth() / (float) screen.width, ratioY = image.getHeight() / (float) screen.height;
        int left = Math.clamp(Math.round(x * ratioX), 0, image.getWidth() - 1);
        int top = Math.clamp(Math.round(y * ratioY), 0, image.getHeight() - 1);
        int w = Math.min(Math.round(width * ratioX), image.getWidth() - left);
        int h = Math.min(Math.round(height * ratioY), image.getHeight() - top);
        int[] pixels = new int[w * h];
        int redSample = image.getPixel(Math.round(22 * ratioX), Math.round(14 * ratioY));
        boolean swap = (redSample & 255) > ((redSample >>> 16) & 255);
        for (int row = 0; row < h; row++) for (int column = 0; column < w; column++) {
            int color = image.getPixel(left + column, top + row);
            pixels[row * w + column] = swap ? (color & 0xFF00FF00) | (color >>> 16 & 255) | (color & 255) << 16 : color;
        }
        return pixels;
    }

    private void capture(String name, java.util.function.Consumer<NativeImage> validate) {
        waiting = true;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(output.resolve(name + ".png"));
                captures.add(Map.of("image", name + ".png", "phase", phase, "framebuffer", List.of(image.getWidth(), image.getHeight()), "liveConfiguration", liveConfiguration()));
                validate.accept(image);
                QaTickScheduler.execute(() -> { waiting = false; phase++; ticks = 0; });
            } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
        });
    }

    private Map<String, Object> liveConfiguration() {
        var effect = config.getGuiItem();
        return Map.of("profile", org.polyfrost.oneconfig.api.config.v1.ConfigManager.activeProfile(), "enabled", config.getEnabled(),
            "individualStrokes", effect.getIndividualStrokes(), "firstArgb", String.format("%08X", effect.getStrokeOneColor().getArgb()),
            "secondArgb", String.format("%08X", effect.getStrokeTwoColor().getArgb()), "firstChroma", effect.getStrokeOneColor().getChroma(),
            "secondChroma", effect.getStrokeTwoColor().getChroma(), "speed", effect.getSpeed(), "scale", effect.getScale());
    }

    private void finish(Throwable failure) {
        if (complete) return;
        complete = true;
        try {
            config.setEnabled(enabled); config.setArmorGlintToggle(armorOff); armorColor.restore(config.getArmorColor());
            config.setPotionGlint(potions[0]); config.setPotionGlintBackground(potions[1]); config.setPotionGlintSize(potions[2]);
            config.setPotionGlintForeground(potions[3]); config.setPotionBasedColor(potions[4]);
            gui.restore(config.getGuiItem()); shiny.restore(config.getShinyPots());
            minecraft.player.setItemSlot(EquipmentSlot.CHEST, oldChest);
            minecraft.gui.setScreen(oldScreen);
            var drawn = NativeRuntimeProbe.glint();
            if (failure == null) require(((Number) drawn.get("ownDrawCalls")).intValue() > 0, "The configured glint pipeline was never submitted to the native GPU");
            report.put("nativeGpuDraws", drawn);
            report.put("captures", captures); report.put("passed", failure == null); report.put("restored", true);
            if (failure != null) report.put("error", failure.toString());
            Files.writeString(output.resolve("glint.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        } catch (Throwable restoring) { completion.completeExceptionally(restoring); }
    }

    private static int differences(int[] first, int[] second) {
        require(first.length == second.length, "Glint QA image dimensions changed");
        int count = 0;
        for (int index = 0; index < first.length; index++) if (first[index] != second[index]) count++;
        return count;
    }

    private static int shifted(int[] actual, int[] baseline, int channel) {
        require(actual.length == baseline.length, "Glint QA image dimensions changed");
        int count = 0;
        for (int index = 0; index < actual.length; index++) {
            int red = (actual[index] >>> 16 & 255) - (baseline[index] >>> 16 & 255);
            int green = (actual[index] >>> 8 & 255) - (baseline[index] >>> 8 & 255);
            int blue = (actual[index] & 255) - (baseline[index] & 255);
            if (channel == 0 && red > 15 && red > green + 8 && red > blue + 8 ||
                    channel == 1 && Math.min(green, blue) > 10 && Math.min(green, blue) > red + 8 ||
                    channel == 2 && green > 15 && green > red + 8 && green > blue + 8) count++;
        }
        return count;
    }

    private static ItemStack foil(net.minecraft.world.item.Item item) {
        var stack = new ItemStack(item); stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); return stack;
    }

    private final class Fixture extends Screen {
        private final ItemStack sword = foil(Items.DIAMOND_SWORD);
        private final ItemStack plainSword = new ItemStack(Items.DIAMOND_SWORD);
        private final ItemStack potion = foil(Items.POTION);
        Fixture() { super(Component.literal("Glint GPU QA")); }
        int scale() { return width < 400 ? 2 : 3; }
        int itemX(int index) { return 20 + index * 60; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF101820);
            graphics.fill(20, 12, 28, 20, 0xFFFF2020);
            graphics.fill(32, 12, 40, 20, 0xFF20E0FF);
            ItemStack[] items = {sword, plainSword, potion};
            String[] labels = {"Enchanted", "Normal", "Potion"};
            for (int index = 0; index < items.length; index++) {
                graphics.text(minecraft.font, labels[index], itemX(index), 32, 0xFFFFFFFF);
                graphics.pose().pushMatrix();
                graphics.pose().translate(itemX(index), 50);
                graphics.pose().scale(scale(), scale());
                graphics.item(items[index], 0, 0);
                graphics.pose().popMatrix();
            }
            int left = width - 145, right = width - 15;
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, 25, right, height - 15, 65, .0625f,
                    (left + right) / 2f, (height + 10) / 2f, minecraft.player);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
