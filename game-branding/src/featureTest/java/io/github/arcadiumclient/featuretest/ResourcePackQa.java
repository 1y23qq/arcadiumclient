package io.github.arcadiumclient.featuretest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

final class ResourcePackQa {
    private static final String ROOT = "Arcadium Pack QA/";
    private static final String CLASSIC = ROOT + "Classic/Pack.zip";
    private static final String PVP = ROOT + "PVP/Pack.zip";
    private static final String FOLDER = ROOT + "Unpacked";
    private static final String LATER = ROOT + "Later.zip";
    private static final String BROKEN = ROOT + "Broken";
    private static final String ASSET = "assets/minecraft/textures/arcadium_qa/marker.png";
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static int stage, ticks, width, height;
    private static boolean begun, done, restarted;
    private static Screen parent;
    private static Object screen;
    private static List<String> saved;
    private static byte[] classicTexture;

    private ResourcePackQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.resourcePackQa") || done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Resource pack QA requires the isolated offline account");
        if (!begun) {
            begun = true;
            checks.put("resourcePacks", CHECKS);
            width = mc.width;
            height = mc.height;
            parent = mc.screen;
            saved = reverse(mc.getResourcePacks().getApplied()).stream().map(ResourcePacks.Entry::getName).toList();
            Path checkpoint = checkpoint(mc);
            restarted = Files.isRegularFile(checkpoint);
            if (restarted) {
                JsonObject state = new Gson().fromJson(Files.readString(checkpoint), JsonObject.class);
                List<String> expected = strings(state.getAsJsonArray("selected"));
                require(saved.equals(expected), "A fresh game process did not restore the selected nested packs: " + saved);
                saved = strings(state.getAsJsonArray("prior"));
                classicTexture = texture(0xff2c82e8);
                CHECKS.put("restoredAfterProcessRestart", true);
            } else {
                createFixtures(mc.getResourcePacks().getDirectory().toPath());
            }
            mc.openScreen(new ResourcePacksScreen(parent));
            screen = mc.screen;
            require(screen != null && screen.getClass().getName().equals("io.github.arcadiumclient.runtime.client.gui.ArcadiumResourcePacksScreen"),
                "The vanilla resource pack entry point was not replaced");
            verifyDiscovery(mc);
            invoke(screen, "setExpanded", Set.of("Arcadium Pack QA", ROOT + "Classic", ROOT + "PVP"));
            CHECKS.put("nativeScreenReplaced", true);
        }
        if (++ticks < 25) return false;
        switch (stage) {
            case 0 -> {
                Set<?> before = (Set<?>) invoke(screen, "getExpanded");
                clickText("Arcadium Pack QA");
                require(!((Set<?>) invoke(screen, "getExpanded")).contains("Arcadium Pack QA"), "The folder UI did not collapse");
                invoke(screen, "setExpanded", before);
                capture(mc, "resource-packs-01-available.png");
                Object later = pack(LATER);
                invoke(screen, "add", later);
                require(invoke(screen, "getPending") == later, "An incompatible pack skipped its confirmation");
                invoke(screen, "setPending", (Object) null);
                require(!active().contains(LATER), "Cancelling an incompatible pack enabled it");
                invoke(screen, "add", later);
                invoke(screen, "confirmPending");
                require(active().contains(LATER), "Confirming an incompatible pack did not enable it");
                invoke(screen, "remove", LATER);
                invoke(screen, "add", pack(BROKEN));
                require(!active().contains(BROKEN), "A pack with invalid metadata was enabled");
                invoke(screen, "remove", CLASSIC);
                invoke(screen, "remove", PVP);
                invoke(screen, "remove", FOLDER);
                invoke(screen, "add", pack(CLASSIC));
                invoke(screen, "add", pack(PVP));
                invoke(screen, "add", pack(FOLDER));
                invoke(screen, "move", CLASSIC, -1);
                invoke(screen, "move", CLASSIC, -1);
                require(active().subList(0, 3).equals(List.of(CLASSIC, FOLDER, PVP)), "Pack priority controls did not reorder the selection");
                if (restarted) invoke(screen, "move", PVP, -1);
                CHECKS.put("incompatibleConfirmation", true);
                CHECKS.put("invalidMetadataBlocked", true);
            }
            case 1 -> {
                clickText("Apply");
            }
            case 2 -> {
                if (Boolean.TRUE.equals(invoke(screen, "getApplying"))) return false;
                require(invoke(screen, "getError") == null, "Resource pack application failed");
                List<String> selected = reverse(mc.getResourcePacks().getApplied()).stream().map(ResourcePacks.Entry::getName).toList();
                require(selected.equals(active()), "The native resource pack priority differs from the browser: " + selected + " vs " + active());
                try (var stream = mc.getResourceManager().getResource(new Identifier("minecraft", "textures/arcadium_qa/marker.png")).asStream()) {
                    require(java.util.Arrays.equals(classicTexture, stream.readAllBytes()), "The highest-priority nested pack did not supply the real texture");
                }
                String options = Files.readString(mc.gameDir.toPath().resolve("options.txt"));
                require(options.contains(CLASSIC) && options.contains(PVP) && options.contains(FOLDER), "Nested pack IDs were not saved in options.txt");
                if (!restarted) {
                    Files.writeString(checkpoint(mc), new Gson().toJson(Map.of("selected", selected, "prior", saved)));
                }
                CHECKS.put("nativeTexturePriority", true);
                CHECKS.put("selectedRelativeIds", selected);
                CHECKS.put("savedOptions", true);
                capture(mc, "resource-packs-02-active.png");
                setSearch("Later");
            }
            case 3 -> {
                capture(mc, "resource-packs-03-search.png");
                require("Later".equals(invoke(screen, "getQuery")), "The visible search input did not update the pack filter");
                setSearch("");
                Display.setDisplayMode(new DisplayMode(854, 480));
            }
            case 4 -> {
                if (mc.width != 854 || mc.height != 480) return false;
                CHECKS.put("smallScreenControls", verifyBounds(mc));
                capture(mc, "resource-packs-04-small.png");
                Display.setDisplayMode(new DisplayMode(width, height));
            }
            case 5 -> {
                if (mc.width != width || mc.height != height) return false;
                mc.openScreen(new ResourcePacksScreen(parent));
                screen = mc.screen;
                require(active().equals(reverse(mc.getResourcePacks().getApplied()).stream().map(ResourcePacks.Entry::getName).toList()),
                    "Reopening the pack browser lost the applied selection");
                if (restarted) invoke(screen, "setActive", saved);
            }
            case 6 -> {
                clickText("Done");
            }
            case 7 -> {
                if (mc.screen == screen) return false;
                require(mc.screen == parent, "Done did not return to the original screen");
                CHECKS.put("doneReturnsToParent", true);
                CHECKS.put("restartRequired", !restarted);
                if (restarted) Files.delete(checkpoint(mc));
                CHECKS.put("completed", true);
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown resource pack QA stage");
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static Path checkpoint(Minecraft mc) { return mc.gameDir.toPath().resolve("forge-qa/resource-pack-restart.json"); }

    private static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (var element : array) result.add(element.getAsString());
        return result;
    }

    private static Object semanticsRoot() throws Exception {
        Class<?> type = screen.getClass();
        java.lang.reflect.Field sceneField;
        for (;;) {
            try { sceneField = type.getDeclaredField("sceneOrNull"); break; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        require(scene != null, "The resource pack screen has no rendered Compose scene");
        var field = scene.getClass().getDeclaredField("mainOwner");
        field.setAccessible(true);
        return invoke(invoke(field.get(scene), "getSemanticsOwner"), "getRootSemanticsNode");
    }

    private static Map<String, Object> properties(Object node) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) invoke(node, "getConfig")) {
            var entry = (Map.Entry<?, ?>) value;
            result.put((String) invoke(entry.getKey(), "getName"), entry.getValue());
        }
        return result;
    }

    private static List<Object> semanticsNodes(Object node) throws Exception {
        List<Object> result = new ArrayList<>();
        result.add(node);
        for (Object child : (Collection<?>) invoke(node, "getChildren")) result.addAll(semanticsNodes(child));
        return result;
    }

    private static Object textNode(String text, boolean clickable) throws Exception {
        for (Object node : semanticsNodes(semanticsRoot())) {
            Map<String, Object> properties = properties(node);
            if (clickable && !properties.containsKey("OnClick")) continue;
            if (properties.get("Text") instanceof Collection<?> pieces) {
                for (Object piece : pieces) if (text.equals(invoke(piece, "getText"))) return node;
            }
        }
        throw new IllegalStateException("Visible Compose control was not found: " + text);
    }

    private static void clickText(String text) throws Exception {
        Object node = textNode(text, true);
        Object action = invoke(properties(node).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "The visible control did not accept a click: " + text);
        CHECKS.put("clicked" + text.replace(" ", ""), true);
    }

    private static void setSearch(String text) throws Exception {
        for (Object node : semanticsNodes(semanticsRoot())) {
            Object entry = properties(node).get("SetText");
            if (entry != null) {
                Object action = invoke(entry, "getAction");
                Object value = Class.forName("androidx.compose.ui.text.AnnotatedString")
                    .getConstructor(String.class, List.class, List.class).newInstance(text, List.of(), List.of());
                require(Boolean.TRUE.equals(invoke(action, "invoke", value)), "The visible search field rejected text");
                CHECKS.put("visibleSearchEdited", true);
                return;
            }
        }
        throw new IllegalStateException("The visible pack search input was not found");
    }

    private static Map<String, Object> verifyBounds(Minecraft mc) throws Exception {
        Map<String, Object> bounds = new LinkedHashMap<>();
        for (String text : List.of("Open folder", "Apply", "Done", "Available", "Active", "Resource Packs")) {
            Object rect = invoke(textNode(text, false), "getBoundsInRoot");
            float left = ((Number) invoke(rect, "getLeft")).floatValue();
            float right = ((Number) invoke(rect, "getRight")).floatValue();
            float top = ((Number) invoke(rect, "getTop")).floatValue();
            float bottom = ((Number) invoke(rect, "getBottom")).floatValue();
            require(right > left && bottom > top && left >= 0 && top >= 0 && right <= mc.width && bottom <= mc.height,
                "The small-screen control is clipped: " + text + " " + rect);
            bounds.put(text, List.of(left, top, right, bottom));
        }
        return bounds;
    }

    private static void verifyDiscovery(Minecraft mc) throws Exception {
        Map<String, ResourcePacks.Entry> packs = new LinkedHashMap<>();
        for (var entry : mc.getResourcePacks().getAvailable()) packs.put(entry.getName(), entry);
        require(packs.keySet().containsAll(List.of(CLASSIC, PVP, FOLDER, LATER, BROKEN)), "Nested pack discovery missed a fixture");
        require(!packs.get(CLASSIC).equals(packs.get(PVP)), "Same-named packs in separate folders compare equal");
        require(packs.get(FOLDER).get().hasResource(new Identifier("minecraft", "textures/arcadium_qa/marker.png")),
            "The unpacked directory is not a native resource pack");
        require(((Number) invoke(pack(BROKEN), "getFormat")).intValue() < 0, "Invalid metadata has a valid format");
        CHECKS.put("duplicateBasenamesDistinct", true);
        CHECKS.put("nestedFolderPackLoaded", true);
        CHECKS.put("discoveredFixtureIds", packs.keySet().stream().filter(id -> id.startsWith(ROOT)).toList());

        Class<?> file = Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumResourcePacksScreenKt");
        Object sort = invoke(screen, "getSort");
        List<?> views = (List<?>) invoke(screen, "getPacks");
        Method rows = java.util.Arrays.stream(file.getDeclaredMethods()).filter(method -> method.getName().equals("packRows")).findFirst().orElseThrow();
        rows.setAccessible(true);
        List<?> collapsed = (List<?>) rows.invoke(null, views, Set.of(), "", sort);
        require(collapsed.stream().anyMatch(row -> row.getClass().getSimpleName().equals("Folder")), "Organizing folders are not represented in the browser");
        List<?> search = (List<?>) rows.invoke(null, views, Set.of(), "Later", sort);
        long found = search.stream().filter(row -> row.getClass().getSimpleName().equals("Pack")).count();
        require(found == 1, "Searching did not find exactly one nested pack");
        CHECKS.put("folderAndSearchRows", true);
    }

    @SuppressWarnings("unchecked")
    private static List<String> active() throws Exception { return (List<String>) invoke(screen, "getActive"); }

    private static Object pack(String id) throws Exception {
        for (Object pack : (Collection<?>) invoke(screen, "getPacks")) {
            if (id.equals(invoke(pack, "getId"))) return pack;
        }
        throw new IllegalStateException("Pack view not found: " + id);
    }

    private static <T> List<T> reverse(List<T> list) {
        var copy = new ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        Method selected = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
            .filter(method -> method.getName().equals(name) && method.getParameterCount() == arguments.length).findFirst().orElseThrow();
        selected.setAccessible(true);
        try {
            return selected.invoke(target, arguments);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
    }

    private static void createFixtures(Path directory) throws Exception {
        Path root = directory.resolve("Arcadium Pack QA");
        require(!Files.exists(root), "Resource pack QA fixture directory already exists");
        byte[] classic = texture(0xff2c82e8);
        classicTexture = classic;
        writeZip(directory.resolve(CLASSIC), 1, "Classic blue texture", classic);
        writeZip(directory.resolve(PVP), 1, "PVP green texture", texture(0xff65a84f));
        writeZip(directory.resolve(LATER), 2, "Incompatible format fixture", texture(0xffcf8c42));
        Path unpacked = directory.resolve(FOLDER);
        Files.createDirectories(unpacked.resolve(ASSET).getParent());
        Files.writeString(unpacked.resolve("pack.mcmeta"), metadata(1, "A native unpacked resource pack"));
        Files.write(unpacked.resolve("pack.png"), texture(0xffd65a62));
        Files.write(unpacked.resolve(ASSET), texture(0xffd65a62));
        Path broken = directory.resolve(BROKEN);
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("pack.mcmeta"), "{}");
        Files.write(broken.resolve("pack.png"), texture(0xff808080));
    }

    private static void writeZip(Path path, int format, String description, byte[] texture) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : Map.of("pack.mcmeta", metadata(format, description).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "pack.png", texture, ASSET, texture).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private static String metadata(int format, String description) {
        return "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"" + description + "\"}}";
    }

    private static byte[] texture(int color) throws Exception {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, color);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
