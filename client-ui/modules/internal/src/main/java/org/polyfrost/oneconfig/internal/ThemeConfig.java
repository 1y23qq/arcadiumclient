package org.polyfrost.oneconfig.internal;

import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.polyfrost.oneconfig.internal.ui.themes.GradientPresets;
import org.polyfrost.oneconfig.internal.ui.themes.ProviderKt;
import org.polyfrost.oneconfig.internal.ui.themes.ThemeAccent;

public class ThemeConfig extends Config {
    @Text(title = "Active Theme")
    public static String activeTheme = "Glass Dark";

    @Color(title = "Accent Color", description = "Used when gradient accents are off.", icon = "paintbrush")
    public static PolyColor accentColor = new PolyColor(0xFF2B4BFF);

    @Switch(title = "Use gradient accent color")
    public static boolean gradientAccent = true;

    @Dropdown(title = "Gradient Preset")
    public static int gradientPreset = 0;

    @Slider(title = "Gradient Speed", description = "Flow speed. 0 keeps the gradient still.", min = 0, max = 5, step = 0.05f)
    public static float gradientSpeed = 1f;

    @Slider(title = "Gradient Smoothness", description = "How gradually the two colors blend, from sharp to soft.", min = 0, max = 100, step = 1)
    public static float gradientSmoothness = 100f;

    @Switch(title = "Use custom gradient colors")
    public static boolean customGradient = false;

    @Color(title = "Gradient Start", alpha = false)
    public static int gradientStart = 0xFF3AA0FF;

    @Color(title = "Gradient End", alpha = false)
    public static int gradientEnd = 0xFF36F1B0;

    @Switch(title = "Animations", description = "Smooth, clean animations in the Arcadium Options Interface", icon = "refresh")
    public static boolean animations = true;

    public ThemeConfig() {
        super("themes.json", "assets/oneconfig/brand/oneconfig-icon.svg", "Themes", Category.QOL);
    }

    @Override
    protected void initialize(boolean byConfigManager) {
        super.initialize(byConfigManager);
        if (tree == null) return;
        hideIf("activeTheme", () -> true);
        getProperty("gradientPreset").addMetadata("options", GradientPresets.names());
        getProperty("gradientPreset").addMetadata("gradientPresets", true);
        hideIf("gradientPreset", () -> !gradientAccent || customGradient);
        hideIf("gradientSpeed", () -> !gradientAccent);
        hideIf("gradientSmoothness", () -> !gradientAccent);
        hideIf("customGradient", () -> !gradientAccent);
        hideIf("gradientStart", () -> !gradientAccent || !customGradient);
        hideIf("gradientEnd", () -> !gradientAccent || !customGradient);
        for (String option : new String[]{"accentColor", "gradientAccent", "gradientPreset", "gradientSpeed", "gradientSmoothness", "customGradient", "gradientStart", "gradientEnd", "animations"}) {
            addCallback(option, value -> {
                ProviderKt.updateAccent();
                return false;
            });
        }
        ThemeAccent.refresh();
        ProviderKt.updateAccent();
    }
}
