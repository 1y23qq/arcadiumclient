// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import androidx.compose.runtime.MutableState;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.node.RootNodeOwner;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.MouseButtonInfo;
import androidx.compose.ui.graphics.ImageBitmap;
import io.github.arcadiumclient.modern.client.MenuPreferences;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.PackType;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

public final class ResourcePackPreviewQa {
    private static final String FOLDER = "QA Previews/Favorites/Large Folder";
    private static final String ZIP = "QA Previews/Favorites/Large Zip.zip";
    private static final String OLD = "QA Previews/Legacy Format.zip";
    private ResourcePackPreviewQa() { }

    public static void prepare(Minecraft minecraft) throws Exception {
        Path root = minecraft.getResourcePackDirectory();
        var version = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        String format = "[" + version.major() + "," + version.minor() + "]";
        String metadata = "{\"pack\":{\"description\":\"Large icon preview\",\"min_format\":" + format + ",\"max_format\":" + format + "}}";
        Path folder = root.resolve(FOLDER);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("pack.mcmeta"), metadata);
        Files.write(folder.resolve("pack.png"), icon(1024, 512, new Color(36, 178, 72)));
        zip(root.resolve(ZIP), metadata, icon(512, 1024, new Color(215, 126, 29)));
        zip(root.resolve(OLD), "{\"pack\":{\"description\":\"Incompatible old pack\",\"pack_format\":1}}", icon(64, 64, Color.RED));
        minecraft.getResourcePackRepository().reload();
    }

    public static Map<String, Object> verify(Screen screen) throws Exception {
        @SuppressWarnings("unchecked") List<Object> views = (List<Object>) state(screen, "packs").getValue();
        var result = new LinkedHashMap<String, Object>();
        result.put("defaultCompatibilityFilter", MenuPreferences.getCompatibleResourcePacksOnly());
        if (!Boolean.TRUE.equals(state(screen, "onlyCompatible").getValue())) throw new IllegalStateException("Compatibility filter should default on in the native QA profile");
        var previews = new ArrayList<Map<String, Object>>();
        for (String id : List.of(FOLDER, ZIP)) {
            Object view = find(views, id);
            if (!Boolean.TRUE.equals(property(view, "getCompatible"))) throw new IllegalStateException("Compatible nested pack was filtered: " + id);
            ImageBitmap bitmap = (ImageBitmap) property(view, "getIcon");
            if (bitmap == null || Math.max(bitmap.getWidth(), bitmap.getHeight()) != 128) throw new IllegalStateException("Large pack icon is missing or not normalized: " + id);
            if (Math.min(bitmap.getWidth(), bitmap.getHeight()) != 64) throw new IllegalStateException("Pack icon aspect ratio changed: " + id);
            previews.add(Map.of("id", id, "width", bitmap.getWidth(), "height", bitmap.getHeight()));
        }
        Object old = find(views, OLD);
        if (!Boolean.FALSE.equals(property(old, "getCompatible"))) throw new IllegalStateException("Old pack passed the compatibility filter");
        var available = new ArrayList<Object>();
        for (Object view : views) if (Boolean.TRUE.equals(property(view, "getCompatible"))) available.add(view);
        List<String> visible = rowKeys(available, "large");
        for (String key : List.of("folder:QA Previews", "folder:QA Previews/Favorites", "pack:" + FOLDER, "pack:" + ZIP))
            if (!visible.contains(key)) throw new IllegalStateException("Compatible nested pack search lost its parent hierarchy: " + key);
        if (rowKeys(available, "Legacy Format").contains("pack:" + OLD)) throw new IllegalStateException("Incompatible pack remains in the filtered tree");
        if (!rowKeys(views, "Legacy Format").contains("pack:" + OLD)) throw new IllegalStateException("Disabling the filter does not reveal incompatible packs");
        result.put("compatibleNestedSearch", visible);
        result.put("incompatibleVisibleWithFilterOff", true);
        @SuppressWarnings("unchecked") List<String> initial = (List<String>) state(screen, "active").getValue();
        var changed = new ArrayList<>(initial);
        changed.add(OLD);
        state(screen, "active").setValue(changed);
        try {
            var remove = screen.getClass().getDeclaredMethod("remove", String.class);
            remove.setAccessible(true);
            remove.invoke(screen, OLD);
            if (((List<?>) state(screen, "active").getValue()).contains(OLD)) throw new IllegalStateException("Filtered incompatible active pack cannot be removed");
        } finally { state(screen, "active").setValue(initial); }
        result.put("largeNestedIcons", previews);
        result.put("incompatibleActivePackRemovable", true);
        result.put("passed", true);
        return result;
    }

    private static List<String> rowKeys(List<Object> views, String query) throws Exception {
        Class<?> sort = Class.forName("io.github.arcadiumclient.modern.gui.PackSort");
        var rows = Class.forName("io.github.arcadiumclient.modern.gui.ArcadiumResourcePacksScreenKt")
                .getDeclaredMethod("packRows", List.class, java.util.Set.class, String.class, sort);
        rows.setAccessible(true);
        var result = new ArrayList<String>();
        for (Object row : (List<?>) rows.invoke(null, views, java.util.Set.of(), query, sort.getEnumConstants()[0]))
            result.add((String) property(row, "getKey"));
        return result;
    }
    private static Object find(List<Object> values, String id) throws Exception {
        for (Object value : values) if (id.equals(property(value, "getId"))) return value;
        throw new IllegalStateException("Native resource pack repository did not discover " + id);
    }
    private static Object property(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    @SuppressWarnings("unchecked") private static MutableState<Object> state(Object screen, String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name + "$delegate");
        field.setAccessible(true);
        return (MutableState<Object>) field.get(screen);
    }
    private static void zip(Path path, String metadata, byte[] icon) throws Exception {
        Files.createDirectories(path.getParent());
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("pack.png"));
            output.write(icon);
            output.closeEntry();
        }
    }
    private static byte[] icon(int width, int height, Color color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("No PNG writer");
            return output.toByteArray();
        } finally { image.flush(); }
    }

    public static CompletableFuture<Map<String, Object>> run(Minecraft minecraft, Path output) {
        var pointer = new PointerProof(minecraft, output);
        return pointer.completion;
    }

    private static final class PointerProof {
        private final Minecraft minecraft;
        private final Screen screen;
        private final Path output;
        private final CompletableFuture<Map<String, Object>> completion = new CompletableFuture<>();
        private final Map<String, Object> report = new LinkedHashMap<>();
        private final long started = System.nanoTime();
        private List<String> initialActive;
        private int phase, ticks, clicks;
        private boolean complete, waiting;

        private PointerProof(Minecraft minecraft, Path output) {
            this.minecraft = minecraft;
            this.screen = minecraft.screen;
            this.output = output;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (complete || waiting) return;
                try { tick(); } catch (Throwable failure) { finish(failure); }
            });
        }
        private void tick() throws Exception {
            require(System.nanoTime() - started < 45_000_000_000L, "Pack pointer proof timed out at " + phase);
            require(minecraft.screen == screen, "Pack pointer proof lost its screen");
            if (++ticks < 12 || minecraft.getOverlay() != null) return;
            switch (phase) {
                case 0 -> {
                    report.putAll(verify(screen));
                    initialActive = List.copyOf(active());
                    state(screen, "expanded").setValue(java.util.Set.of("QA Previews", "QA Previews/Favorites"));
                    state(screen, "query").setValue("Large");
                    next();
                }
                case 1 -> {
                    var cards = List.of(card(FOLDER, false), card(ZIP, false));
                    capture("packs-large-icons", cards, new int[]{0x24b248, 0xd77e1d});
                    next();
                }
                case 2 -> { click("Only compatible packs"); next(); }
                case 3 -> {
                    require(Boolean.FALSE.equals(state(screen, "onlyCompatible").getValue()), "Native click did not disable the compatibility filter");
                    require(!MenuPreferences.getCompatibleResourcePacksOnly(), "Compatibility filter choice was not persisted");
                    state(screen, "query").setValue("Legacy Format");
                    next();
                }
                case 4 -> { capture("packs-filter-off", List.of(card(OLD, false)), null); next(); }
                case 5 -> { click("Enable pack"); next(); }
                case 6 -> {
                    require(state(screen, "pending").getValue() != null, "Incompatible pack did not request confirmation");
                    click("Use pack"); next();
                }
                case 7 -> {
                    require(active().contains(OLD), "Native pack confirmation did not activate the pack");
                    click("Only compatible packs"); next();
                }
                case 8 -> {
                    require(Boolean.TRUE.equals(state(screen, "onlyCompatible").getValue()), "Native click did not restore the compatibility filter");
                    require(MenuPreferences.getCompatibleResourcePacksOnly(), "Restored filter choice was not persisted");
                    capture("packs-filter-on-active", List.of(card(OLD, true)), null); next();
                }
                case 9 -> { click("Disable pack"); next(); }
                case 10 -> {
                    require(active().equals(initialActive), "Native remove did not restore the original active selection");
                    report.put("nativePointerClicks", clicks);
                    report.put("filterToggledAndPersisted", true);
                    report.put("filteredActivePackRemovedWithNativePointer", true);
                    state(screen, "query").setValue("");
                    next();
                }
                case 11 -> finish(null);
                default -> throw new IllegalStateException("Unexpected pack pointer phase " + phase);
            }
        }
        @SuppressWarnings("unchecked") private List<String> active() throws Exception { return (List<String>) state(screen, "active").getValue(); }
        private Rect card(String id, boolean active) throws Exception {
            var type = Class.forName("io.github.arcadiumclient.modern.gui.PackDrag");
            var constructor = type.getDeclaredConstructor(String.class, boolean.class);
            constructor.setAccessible(true);
            var cards = (Map<?, ?>) field(screen, "cardBounds");
            var bounds = (Rect) cards.get(constructor.newInstance(id, active));
            require(bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0, "Native pack card is not visible: " + id);
            return bounds;
        }
        private SemanticsNode root() throws Exception {
            Object scene = field(screen, "sceneOrNull");
            require(scene != null, "Pack screen has no live Compose scene");
            return ((RootNodeOwner) field(scene, "mainOwner")).getSemanticsOwner().getRootSemanticsNode();
        }
        private SemanticsNode find(SemanticsNode node, String label) {
            var config = node.getConfig();
            var text = SemanticsProperties.INSTANCE.getText();
            var description = SemanticsProperties.INSTANCE.getContentDescription();
            if (config.contains(text) && config.get(text).stream().anyMatch(value -> value.getText().equals(label))
                    || config.contains(description) && config.get(description).contains(label)) return node;
            for (var child : node.getChildren()) {
                var match = find(child, label);
                if (match != null) return match;
            }
            return null;
        }
        private void click(String label) throws Exception {
            var node = find(root(), label);
            require(node != null, "Pack control is absent: " + label);
            Rect bounds = node.getBoundsInWindow();
            double x = (bounds.getLeft() + bounds.getRight()) / 2;
            double y = (bounds.getTop() + bounds.getBottom()) / 2;
            var mouse = (MouseQaAccess) minecraft.mouseHandler;
            long window = minecraft.getWindow().handle();
            mouse.arcadium$nativeMove(window, x, y);
            var button = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
            mouse.arcadium$nativeButton(window, button, InputConstants.PRESS);
            mouse.arcadium$nativeButton(window, button, InputConstants.RELEASE);
            clicks++;
        }
        private void capture(String name, List<Rect> cards, int[] colors) {
            waiting = true;
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
                try (image) {
                    var pixels = new ArrayList<Integer>();
                    if (colors != null) for (int i = 0; i < colors.length; i++) {
                        Rect bounds = cards.get(i);
                        int count = 0;
                        double sx = (double) image.getWidth() / minecraft.getWindow().getScreenWidth();
                        double sy = (double) image.getHeight() / minecraft.getWindow().getScreenHeight();
                        for (int y = Math.max(0, (int) (bounds.getTop() * sy)); y < Math.min(image.getHeight(), (int) Math.ceil(bounds.getBottom() * sy)); y++)
                            for (int x = Math.max(0, (int) (bounds.getLeft() * sx)); x < Math.min(image.getWidth(), (int) Math.ceil(bounds.getRight() * sx)); x++) {
                                int color = image.getPixel(x, y);
                                if (Math.abs((color >>> 16 & 255) - (colors[i] >>> 16 & 255)) < 16
                                        && Math.abs((color >>> 8 & 255) - (colors[i] >>> 8 & 255)) < 16
                                        && Math.abs((color & 255) - (colors[i] & 255)) < 16) count++;
                            }
                        require(count >= 30, "Large native resource-pack icon pixels are missing: " + name + " index " + i + " count " + count);
                        pixels.add(count);
                    }
                    image.writeToFile(output.resolve(name + ".png"));
                    QaTickScheduler.execute(() -> { report.put(name, Map.of("image", name + ".png", "iconPixels", pixels)); waiting = false; });
                } catch (Throwable failure) { QaTickScheduler.execute(() -> finish(failure)); }
            });
        }
        private void next() { phase++; ticks = 0; }
        private void finish(Throwable failure) {
            if (complete) return;
            complete = true;
            try {
                if (initialActive != null) state(screen, "active").setValue(initialActive);
                state(screen, "query").setValue("");
                state(screen, "onlyCompatible").setValue(true);
                MenuPreferences.setCompatibleResourcePacksOnly(true);
                MenuPreferences.INSTANCE.save();
            } catch (Throwable restore) { if (failure == null) failure = restore; else failure.addSuppressed(restore); }
            report.put("passed", failure == null);
            if (failure != null) report.put("error", failure.toString());
            try { Files.writeString(output.resolve("resource-pack-previews.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Throwable writing) { if (failure == null) failure = writing; else failure.addSuppressed(writing); }
            if (failure == null) completion.complete(report); else completion.completeExceptionally(failure);
        }
        private static Object field(Object target, String name) throws Exception {
            Class<?> type = target.getClass();
            while (type != null) {
                try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
                catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
            }
            throw new NoSuchFieldException(name);
        }
        private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    }
}
