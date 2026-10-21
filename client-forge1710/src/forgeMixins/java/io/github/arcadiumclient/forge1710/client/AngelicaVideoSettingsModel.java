// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.client;

import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumVideoSettingsScreen;
import io.github.arcadiumclient.runtime.client.gui.VideoChoice;
import io.github.arcadiumclient.runtime.client.gui.VideoControl;
import io.github.arcadiumclient.runtime.client.gui.VideoPage;
import io.github.arcadiumclient.runtime.client.gui.VideoSetting;
import io.github.arcadiumclient.runtime.client.gui.VideoSettingsModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import jss.notfine.core.Settings;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.options.ActionOption;
import me.jellysquid.mods.sodium.client.gui.options.ButtonOption;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public final class AngelicaVideoSettingsModel implements VideoSettingsModel {
    private static final Map<String, String> STANDARD_KEYS = Map.ofEntries(
        Map.entry("options.graphics", "graphics"), Map.entry("options.renderDistance", "render_distance"),
        Map.entry("options.ao", "smooth_lighting"), Map.entry("options.framerateLimit", "max_framerate"),
        Map.entry("options.guiScale", "gui_scale"), Map.entry("options.gamma", "brightness"),
        Map.entry("options.viewBobbing", "view_bobbing"), Map.entry("options.fullscreen", "fullscreen"),
        Map.entry("options.particles", "particles"), Map.entry("options.clouds", "clouds"),
        Map.entry("options.dynamiclights", "dynamic_lights")
    );
    private final ReeseSodiumVideoOptionsScreen delegate;
    private final List<VideoSetting> options = new ArrayList<>();
    private boolean dynamicFov = ClientPreferences.getDynamicFov();

    public AngelicaVideoSettingsModel(ReeseSodiumVideoOptionsScreen delegate) {
        this.delegate = delegate;
        var keys = new LinkedHashMap<String, String>();
        STANDARD_KEYS.forEach((translation, key) -> keys.put(I18n.func_135052_a(translation), key));
        List<OptionPage> pages = field(delegate, "pages");
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            OptionPage page = pages.get(pageIndex);
            VideoPage category = switch (pageIndex) {
                case 0, 5 -> VideoPage.DISPLAY;
                case 1, 3, 4 -> VideoPage.PERFORMANCE;
                case 2 -> VideoPage.QUALITY;
                case 6, 8 -> VideoPage.DETAILS;
                default -> VideoPage.OTHER;
            };
            int index = 0;
            for (Option<?> raw : page.getOptions()) {
                if (raw instanceof ActionOption && raw.getName().equals(I18n.func_135052_a("options.angelica.renderer.link"))) continue;
                String key = keys.getOrDefault(raw.getName(), "angelica/" + pageIndex + "/" + index++);
                if (raw == Settings.DYNAMIC_LIGHTS.option) key = "dynamic_lights";
                options.add(adapt(raw, key, category, page.getName()));
            }
        }
        options.add(new VideoSetting("dynamic_fov", "Dynamic FOV", "Field-of-view changes while sprinting and using items.",
            VideoPage.ANIMATIONS, "", VideoControl.TOGGLE, List.of(), 0, 1, 1, false,
            () -> dynamicFov, value -> { dynamicFov = (Boolean) value; return Unit.INSTANCE; }, () -> true,
            value -> (Boolean) value ? "On" : "Off"));
    }

    public static GuiScreen replace(ReeseSodiumVideoOptionsScreen screen) {
        try {
            var constructor = ArcadiumVideoSettingsScreen.class.getDeclaredConstructor(VideoSettingsModel.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new AngelicaVideoSettingsModel(screen));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not create the Arcadium Angelica settings screen", error);
        }
    }

    @SuppressWarnings("unchecked")
    private VideoSetting adapt(Option<?> raw, String key, VideoPage page, String group) {
        Option<Object> option = (Option<Object>) raw;
        Object control = option.getControl();
        boolean button = raw instanceof ButtonOption;
        VideoControl kind = button ? VideoControl.ACTION : control instanceof SliderControl ? VideoControl.SLIDER
            : control instanceof CyclingControl<?> ? VideoControl.CHOICE : option.getValue() instanceof Boolean ? VideoControl.TOGGLE : null;
        if (kind == null) throw new IllegalStateException("Unsupported Angelica setting: " + option.getName() + " (" + control.getClass().getName() + ")");
        var choices = new ArrayList<VideoChoice>();
        if (control instanceof CyclingControl<?>) {
            Object[] values = field(control, "allowedValues");
            String[] names = field(control, "names");
            for (int i = 0; i < values.length; i++) choices.add(new VideoChoice(values[i], names[i]));
        }
        int min = control instanceof SliderControl ? field(control, "min") : 0;
        int max = control instanceof SliderControl ? field(control, "max") : 1;
        int step = control instanceof SliderControl ? field(control, "interval") : 1;
        ControlValueFormatter formatter = control instanceof SliderControl ? field(control, "mode") : null;
        return new VideoSetting(key, option.getName(), option.getTooltip(), page, group, kind, choices, min, max, step,
            option.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART),
            () -> button ? Unit.INSTANCE : option.getValue(),
            value -> { if (button) { apply(); invoke(option, "invoke"); } else option.setValue(value); return Unit.INSTANCE; },
            () -> option.isAvailable() && (!button || (Boolean) invoke(option, "clickable")),
            value -> formatter != null ? formatter.format(((Number) value).intValue())
                : choices.stream().filter(choice -> choice.getValue().equals(value)).map(VideoChoice::getLabel).findFirst()
                    .orElseGet(() -> value instanceof Boolean flag ? flag ? "On" : "Off" : value.toString()));
    }

    @Override public GuiScreen getDelegate() { return delegate; }
    @Override public GuiScreen getParent() { return delegate.prevScreen; }
    @Override public List<VideoSetting> getOptions() { return options; }

    @Override public void apply() {
        invoke(delegate, "applyChanges");
        if (dynamicFov != ClientPreferences.getDynamicFov()) {
            ClientPreferences.setDynamicFov(dynamicFov);
            ClientPreferences.INSTANCE.save();
        }
        options.forEach(VideoSetting::refresh);
    }

    @Override public void undo() {
        invoke(delegate, "undoChanges");
        dynamicFov = ClientPreferences.getDynamicFov();
        options.forEach(VideoSetting::refresh);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Missing Angelica field " + name, error); }
    }

    private static Object invoke(Object owner, String name) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not invoke Angelica " + name, error); }
        }
        throw new IllegalStateException("Missing Angelica method " + name);
    }
}
