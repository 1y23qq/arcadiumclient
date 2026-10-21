// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.runtime.client.gui.VideoSetting;
import io.github.arcadiumclient.runtime.client.gui.VideoSettingsModel;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiButton;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;

public final class ForgeMenuFlowQa {
    private int frames, next = 180, stage;
    private boolean done;
    private Map<String, Object> video;

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || ++frames < next) return;
        Minecraft mc = Minecraft.func_71410_x();
        try {
            switch (stage) {
                case 0 -> { if (hasText("Skip")) click("Skip"); }
                case 1 -> {
                    require(hasText("Singleplayer") && hasText("Multiplayer"), "The main menu is incomplete.");
                    ForgeRuntimeQa.capture(mc, "forge-title", frames);
                    click("Arcadium Options");
                }
                case 2 -> { require(hasText("Mods"), "Arcadium Options did not open."); ForgeRuntimeQa.capture(mc, "forge-options", frames); click("Preferences"); }
                case 3 -> {
                    require(hasText("Preferences"), "Preferences did not open.");
                    ForgeRuntimeQa.capture(mc, "forge-preferences", frames);
                    mc.func_147108_a(new ArcadiumMainMenu());
                }
                case 4 -> click("Options");
                case 5 -> vanillaButton(101);
                case 6 -> {
                    ForgeRuntimeQa.capture(mc, "forge-video", frames);
                    require(hasText("Video settings"), "The Arcadium video settings did not open.");
                    validateVideo(mc);
                    mc.func_147108_a(new GuiOptions(new ArcadiumMainMenu(), mc.field_71474_y));
                }
                case 7 -> vanillaButton(105);
                case 8 -> {
                    require(hasText("Resource Packs"), "The Arcadium resource pack menu did not open.");
                    ForgeRuntimeQa.capture(mc, "forge-packs", frames);
                    report(mc, true, null);
                    mc.func_71400_g(); done = true;
                }
                default -> throw new IllegalStateException("Unexpected Forge menu verification stage.");
            }
            stage++; next = frames + 60;
        } catch (Throwable error) {
            try { report(mc, false, error.toString()); } catch (Exception writeFailure) { error.addSuppressed(writeFailure); }
            error.printStackTrace(); done = true; mc.func_71400_g();
        }
    }

    private void report(Minecraft mc, boolean ok, String error) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("ok", ok); report.put("stage", stage); report.put("error", error);
        report.put("processId", ProcessHandle.current().pid()); report.put("capturedAt", java.time.Instant.now().toString());
        report.put("screen", mc.field_71462_r == null ? null : mc.field_71462_r.getClass().getName());
        report.put("videoSettings", video);
        report.put("configs", ConfigRegistry.INSTANCE.getConfigs().stream().map(config -> Map.of("id", config.getId(), "title", config.getTitle().toString())).toList());
        Files.writeString(mc.field_71412_D.toPath().resolve("forge-menu-flow.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private void validateVideo(Minecraft mc) throws Exception {
        var model = (VideoSettingsModel) field(mc.field_71462_r, "model");
        var keys = model.getOptions().stream().map(VideoSetting::getKey).toList();
        for (String required : List.of("graphics", "render_distance", "smooth_lighting", "max_framerate", "gui_scale", "brightness", "view_bobbing", "dynamic_fov"))
            require(keys.contains(required), "Missing Angelica setting " + required);
        var brightness = model.getOptions().stream().filter(option -> option.getKey().equals("brightness")).findFirst().orElseThrow();
        Object original = brightness.getValue();
        int wanted = ((Number) original).intValue() == 37 ? 47 : 37;
        brightness.change(wanted); require(model.getChanged(), "Changing brightness was ignored.");
        model.undo(); require(brightness.getValue().equals(original) && !model.getChanged(), "Undo did not restore Angelica settings.");
        brightness.change(wanted); model.apply();
        require(Math.abs(mc.field_71474_y.field_74333_Y - wanted / 100f) < .0001f, "Angelica brightness was not applied.");
        require(!model.getChanged(), "Applied Angelica changes remained dirty.");
        brightness.change(original); model.apply();
        video = Map.of("delegate", model.getDelegate().getClass().getName(), "count", keys.size(), "keys", keys, "applyAndUndo", true);
    }

    private static void vanillaButton(int id) throws Exception {
        Object screen = Minecraft.func_71410_x().field_71462_r;
        var buttonsField = net.minecraft.client.gui.GuiScreen.class.getDeclaredField("field_146292_n");
        buttonsField.setAccessible(true);
        GuiButton selected = ((List<?>) buttonsField.get(screen)).stream().filter(GuiButton.class::isInstance).map(GuiButton.class::cast)
            .filter(button -> button.field_146127_k == id).findFirst().orElseThrow();
        Method action = GuiOptions.class.getDeclaredMethod("func_146284_a", GuiButton.class);
        action.setAccessible(true); action.invoke(screen, selected);
    }

    private static boolean hasText(String text) throws Exception { return action(text, false) != null; }
    private static void click(String text) throws Exception {
        Object action = action(text, true);
        require(action != null, "No button named " + text);
        require(Boolean.TRUE.equals(invoke(action, "invoke")), "The button rejected " + text);
    }

    private static Object action(String text, boolean clickable) throws Exception {
        for (Object node : allNodes()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Object value : (Iterable<?>) invoke(node, "getConfig")) {
                var entry = (Map.Entry<?, ?>) value;
                values.put((String) invoke(entry.getKey(), "getName"), entry.getValue());
            }
            if (!(values.get("Text") instanceof Collection<?> strings)) continue;
            for (Object string : strings) if (text.equals(invoke(string, "getText"))) {
                Object click = values.get("OnClick");
                if (clickable && click == null) continue;
                return click != null ? invoke(click, "getAction") : Boolean.TRUE;
            }
        }
        return null;
    }

    private static List<Object> allNodes() throws Exception {
        Object screen = Minecraft.func_71410_x().field_71462_r;
        Class<?> type = screen.getClass();
        java.lang.reflect.Field sceneField;
        for (;;) { try { sceneField = type.getDeclaredField("sceneOrNull"); break; } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); } }
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        require(scene != null, "The Compose scene is not ready.");
        var result = nodes(invoke(invoke(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"));
        for (Object layer : (Collection<?>) field(scene, "layers"))
            result.addAll(nodes(invoke(invoke(invoke(layer, "getOwner"), "getSemanticsOwner"), "getRootSemanticsNode")));
        java.util.Collections.reverse(result);
        return result;
    }

    private static List<Object> nodes(Object node) throws Exception {
        var result = new ArrayList<Object>(); result.add(node);
        for (Object child : (Collection<?>) invoke(node, "getChildren")) result.addAll(nodes(child));
        return result;
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = java.util.stream.Stream.concat(java.util.Arrays.stream(target.getClass().getDeclaredMethods()), java.util.Arrays.stream(target.getClass().getMethods()))
            .filter(value -> value.getName().equals(name) && value.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(target, args);
    }
    private static void require(boolean value, String error) { if (!value) throw new IllegalStateException(error); }
}
