// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.ui.compose.SkiaFontRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

final class NativeFont262Qa {
    private static final FontDescription MOD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcadium_builtin", "mod"));
    private static final List<String> PROBES = List.of("iiiiiiii", "WWWWWWWW", "0123456789");
    private record Baseline(int game, float mod, int modAlias, List<Integer> gameProbes, List<Float> modProbes) {}
    private record Preferences(boolean gameVanilla, boolean gameInter, boolean modVanilla, boolean modInter) {
        static Preferences current() {
            return new Preferences(OneConfigConfig.useVanillaFontInGame, OneConfigConfig.useInterInGame,
                    OneConfigConfig.useVanillaFontForModText, OneConfigConfig.useInterForModText);
        }
        void apply() {
            OneConfigConfig.useVanillaFontInGame = gameVanilla;
            OneConfigConfig.useInterInGame = gameInter;
            OneConfigConfig.useVanillaFontForModText = modVanilla;
            OneConfigConfig.useInterForModText = modInter;
            FontPreferences.refresh();
        }
    }

    static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        Preferences previous = Preferences.current();
        var repository = minecraft.getResourcePackRepository();
        List<String> selected = List.copyOf(repository.getSelectedIds());
        List<String> optionPacks = List.copyOf(minecraft.options.resourcePacks);
        List<String> incompatible = List.copyOf(minecraft.options.incompatibleResourcePacks);
        var previousScreen = minecraft.gui.screen();
        var results = new LinkedHashMap<String, Object>();
        var baseline = new AtomicReference<Baseline>();
        results.put("graphics", NativeGraphicsQa.snapshot());
        results.put("glyphProbes", PROBES);
        results.put("settleClientTicksPerPreference", 5);
        CompletableFuture<Void> measurement = step(new Preferences(true, false, true, false), () -> {
            var value = new Baseline(minecraft.font.width("AAAA"), SkiaFontRenderer.INSTANCE.measureWidth("AAAA"),
                    minecraft.font.width(modText()), gameProbes(minecraft), modProbes());
            baseline.set(value);
            results.put("baselineGameWidth", value.game);
            results.put("baselineModWidth", value.mod);
            results.put("baselineModAliasWidth", value.modAlias);
            results.put("baselineGameGlyphWidths", value.gameProbes);
            results.put("baselineModGlyphWidths", value.modProbes);
        }).thenComposeAsync(ignored -> {
            try {
                new Preferences(false, false, false, false).apply();
                var directory = minecraft.getResourcePackDirectory().resolve("Arcadium Font 26.2 QA");
                Files.createDirectories(directory.resolve("assets/minecraft/font"));
                var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
                var metadata = Map.of("pack", Map.of("description", "Arcadium native font QA", "min_format", List.of(format.major(), format.minor()), "max_format", List.of(format.major(), format.minor())));
                var json = new GsonBuilder().create();
                Files.writeString(directory.resolve("pack.mcmeta"), json.toJson(metadata));
                String definition = "{\"providers\":[{\"type\":\"space\",\"advances\":{\"A\":20}}]}";
                var jsonDefinition = JsonParser.parseString(definition).getAsJsonObject();
                var decoded = GlyphProviderDefinition.MAP_CODEC.codec().parse(JsonOps.INSTANCE, jsonDefinition.getAsJsonArray("providers").get(0)).getOrThrow();
                require(decoded.type() == GlyphProviderType.SPACE, "The actual native codec did not accept the synthetic space provider");
                results.put("nativeProviderCodecValidated", true);
                results.put("providerType", decoded.type().getSerializedName());
                Files.writeString(directory.resolve("assets/minecraft/font/default.json"), definition);
                repository.reload();
                String id = "file/Arcadium Font 26.2 QA";
                require(repository.isAvailable(id), "Synthetic font pack was not discovered");
                var withFontPack = new ArrayList<>(selected);
                withFontPack.remove(id);
                withFontPack.add(id);
                repository.setSelected(withFontPack);
                minecraft.options.resourcePacks.clear();
                minecraft.options.resourcePacks.addAll(withFontPack);
                return minecraft.reloadResourcePacks();
            } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
        }, QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(new Preferences(false, false, false, false), () -> {
            int game = minecraft.font.width("AAAA"), alias = minecraft.font.width(modText());
            float mod = SkiaFontRenderer.INSTANCE.measureWidth("AAAA");
            results.put("resourcePackGameWidth", game);
            results.put("resourcePackModWidth", mod);
            results.put("resourcePackModAliasWidth", alias);
            require(game == 80 && alias == 80 && close(mod, 80), "Resource pack advances were not used by all three real font paths");
            require(minecraft.font.width("\u00a7cAAAA") == game && close(SkiaFontRenderer.INSTANCE.measureWidth("\u00a7cAAAA"), mod),
                    "Minecraft formatting codes changed glyph advances");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> captureColors(minecraft, output, "fonts-resource-pack", results), QaTickScheduler.EXECUTOR)
                .thenComposeAsync(ignored -> step(new Preferences(true, false, false, false), () -> {
            int game = minecraft.font.width("AAAA");
            float mod = SkiaFontRenderer.INSTANCE.measureWidth("AAAA");
            results.put("vanillaGameWidth", game);
            results.put("modWidthWithVanillaGameFont", mod);
            require(game == baseline.get().game && close(mod, baseline.get().mod), "Vanilla game font did not suppress pack fonts in both game and mod text");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(new Preferences(true, false, true, false), () -> {
            float mod = SkiaFontRenderer.INSTANCE.measureWidth("AAAA");
            int alias = minecraft.font.width(modText());
            results.put("vanillaModWidth", mod);
            results.put("vanillaModAliasWidth", alias);
            require(close(mod, baseline.get().mod) && alias == baseline.get().modAlias, "Vanilla mod font did not restore the built-in font");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(new Preferences(false, true, true, false), () -> {
            int game = minecraft.font.width("AAAA");
            var probes = gameProbes(minecraft);
            results.put("interGameWidth", game);
            results.put("interGameGlyphWidths", probes);
            require(game > 0 && game != 80 && !probes.equals(baseline.get().gameProbes), "Native Inter did not replace the resource pack and built-in glyph advances");
            require(close(SkiaFontRenderer.INSTANCE.measureWidth("AAAA"), baseline.get().mod), "Game Inter changed the explicitly selected vanilla mod font");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> step(new Preferences(false, true, false, true), () -> {
            float mod = SkiaFontRenderer.INSTANCE.measureWidth("AAAA");
            int alias = minecraft.font.width(modText());
            var probes = modProbes();
            results.put("interModWidth", mod);
            results.put("interModAliasWidth", alias);
            results.put("interModGlyphWidths", probes);
            require(mod > 0 && !close(mod, 80) && alias > 0 && alias != 80, "Mod Inter retained the resource pack font");
            require(java.util.stream.IntStream.range(0, PROBES.size()).anyMatch(index -> !close(probes.get(index), baseline.get().modProbes.get(index))), "Mod Inter glyph advances match the built-in font across every probe");
        }), QaTickScheduler.EXECUTOR).thenComposeAsync(ignored -> captureColors(minecraft, output, "fonts-inter", results), QaTickScheduler.EXECUTOR)
                .thenComposeAsync(ignored -> step(new Preferences(false, false, false, true), () -> {
            require(minecraft.font.width("AAAA") == 80, "Mod preference changed the native game font");
            require(minecraft.font.width(modText()) == (int) results.get("interModAliasWidth"), "Deferred native mod alias changed when game Inter was switched off");
            results.put("independentPreferences", true);
            results.put("passed", true);
        }), QaTickScheduler.EXECUTOR);

        return measurement.handleAsync((ignored, failure) -> {
            previous.apply();
            minecraft.gui.setScreen(previousScreen);
            repository.setSelected(selected);
            minecraft.options.resourcePacks.clear();
            minecraft.options.resourcePacks.addAll(optionPacks);
            minecraft.options.incompatibleResourcePacks.clear();
            minecraft.options.incompatibleResourcePacks.addAll(incompatible);
            minecraft.options.save();
            return minecraft.reloadResourcePacks().<Map<String, Object>>handleAsync((restored, restoreFailure) -> {
                results.put("restored", restoreFailure == null);
                if (failure != null) { results.put("passed", false); results.put("error", failure.toString()); }
                if (restoreFailure != null) { results.put("passed", false); results.put("restoreError", restoreFailure.toString()); }
                try { Files.writeString(output.resolve("fonts-26.2.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results)); }
                catch (Exception writing) { throw new CompletionException(writing); }
                if (failure != null) throw new CompletionException(failure);
                if (restoreFailure != null) throw new CompletionException(restoreFailure);
                return results;
            }, QaTickScheduler.EXECUTOR);
        }, QaTickScheduler.EXECUTOR).thenCompose(future -> future);
    }

    private static CompletableFuture<Void> captureColors(Minecraft minecraft, Path output, String name, Map<String, Object> results) {
        var previous = minecraft.gui.screen();
        var screen = new FontProbe();
        minecraft.gui.setScreen(screen);
        return QaTickScheduler.afterFrames(5).thenComposeAsync(ignored -> {
            var captured = new CompletableFuture<Void>();
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    image.writeToFile(output.resolve(name + ".png"));
                    int[][] counts = new int[2][3];
                    float rx = image.getWidth() / (float) screen.width, ry = image.getHeight() / (float) screen.height;
                    for (int column = 0; column < 2; column++) {
                        int left = Math.round((20 + column * 100) * rx), right = Math.round((68 + column * 100) * rx);
                        for (int row = 0; row < 3; row++) {
                            int top = Math.round((24 + row * 18) * ry), bottom = Math.round((36 + row * 18) * ry);
                            for (int y = top; y < Math.min(bottom, image.getHeight()); y++) for (int x = left; x < Math.min(right, image.getWidth()); x++) {
                                int color = image.getPixel(x, y), red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
                                if ((row == 0 && red > 180 && green < 140 && blue < 140)
                                        || (row == 1 && green > 180 && red < 140 && blue < 140)
                                        || (row == 2 && green > 180 && blue > 180 && red < 140)) counts[column][row]++;
                            }
                            require(counts[column][row] >= 12, "The native " + (column == 0 ? "game" : "mod alias") + " font did not render formatting color row " + row + " (" + counts[column][row] + " pixels)");
                        }
                    }
                    results.put(name, Map.of("image", name + ".png", "nativeGameColors", counts[0], "nativeModAliasColors", counts[1]));
                    captured.complete(null);
                } catch (Throwable failure) { captured.completeExceptionally(failure); }
            });
            return captured;
        }, QaTickScheduler.EXECUTOR).whenCompleteAsync((ignored, failure) -> minecraft.gui.setScreen(previous), QaTickScheduler.EXECUTOR);
    }

    private static final class FontProbe extends Screen {
        private FontProbe() { super(Component.literal("Arcadium font verification")); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF080808);
            for (int row = 0; row < 3; row++) {
                String code = switch (row) { case 0 -> "\u00a7c"; case 1 -> "\u00a7a"; default -> "\u00a7b"; };
                graphics.text(minecraft.font, code + "MMMM", 20, 24 + row * 18, 0xFFFFFFFF, false);
                graphics.text(minecraft.font, Component.literal(code + "MMMM").withStyle(style -> style.withFont(MOD_FONT)),
                        120, 24 + row * 18, 0xFFFFFFFF, false);
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static CompletableFuture<Void> step(Preferences preferences, Runnable verify) {
        preferences.apply();
        return QaTickScheduler.afterTicks(5).thenRunAsync(verify, QaTickScheduler.EXECUTOR);
    }
    private static List<Integer> gameProbes(Minecraft minecraft) { return PROBES.stream().map(minecraft.font::width).toList(); }
    private static List<Float> modProbes() { return PROBES.stream().map(text -> SkiaFontRenderer.INSTANCE.measureWidth(text)).toList(); }
    private static Component modText() { return Component.literal("AAAA").withStyle(style -> style.withFont(MOD_FONT)); }
    private static boolean close(float first, float second) { return Math.abs(first - second) < .05f; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
