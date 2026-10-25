// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import eu.midnightdust.blur.BlurConfig.Easing;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.TextList;

public final class LegacyBlurConfig extends LegacyNativeConfig {
	private static final String GENERAL = "General";
	private static final String SCREENS = "Screens";
	private static final String STYLE = "Style";
	private static final String ANIMATION = "Animation";
	private static final String ADVANCED = "Advanced";

	private static final String ONECONFIG_SCREEN = "org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen";

	public static final LegacyBlurConfig INSTANCE = new LegacyBlurConfig();

	@Include
	public static int configVersion = 4;

	@Switch(title = "Enable GUI blur", description = "Disables blur on every screen without changing the individual screen settings.", category = GENERAL)
	public static boolean enabled = true;

	@Switch(title = "Blur containers", category = SCREENS)
	public static boolean blurContainers = true;

	@Switch(title = "Blur books", category = SCREENS)
	public static boolean blurBooks = true;

	@Switch(title = "Blur signs", category = SCREENS)
	public static boolean blurSigns = true;

	@Switch(title = "Blur command blocks", category = SCREENS)
	public static boolean blurCommandBlocks = true;

	@Switch(title = "Blur death screen", category = SCREENS)
	public static boolean blurDeathScreen = false;

	@Switch(title = "Blur title screen", category = SCREENS)
	public static boolean blurTitleScreen = false;

	@Switch(title = "Darken title screen", category = SCREENS)
	public static boolean darkenTitleScreen = false;

	@Switch(title = "Reduce container blur", category = SCREENS)
	public static boolean reduceInGameBlur = false;

	@Switch(title = "Show screen ID", category = ADVANCED)
	public static boolean showScreenID = false;

	@TextList(title = "Always blur these screens", category = ADVANCED)
	public static String[] forceEnabledScreens = {
		"dev.emi.emi.screen.RecipeScreen",
		"mezz.jei.gui.recipes.RecipesGui",
		"me.shedaniel.rei.impl.client.gui.screen.DefaultDisplayViewingScreen"
	};

	@TextList(title = "Never blur these screens", category = ADVANCED)
	public static String[] forceDisabledScreens = {
		"net.irisshaders.iris.gui.screen.ShaderPackScreen",
		ONECONFIG_SCREEN
	};

	@Switch(title = "Use gradient", category = STYLE)
	public static boolean useGradient = true;

    @Switch(title = "Rainbow gradient", category = STYLE)
    public static boolean rainbowMode = false;

	@Slider(title = "Blur radius", description = "This version renders up to 20. Larger saved legacy values are retained.", category = STYLE, min = 0, max = 20)
	public static int radius = 8;

	@Color(title = "Gradient start color", category = STYLE, alpha = true)
	public static PolyColor gradientStartColor = new PolyColor(75 << 24);

	@Color(title = "Gradient end color", category = STYLE, alpha = true)
	public static PolyColor gradientEndColor = new PolyColor(75 << 24);

	@Slider(title = "Gradient rotation", category = STYLE, min = 0, max = 360)
	public static int gradientRotation = 0;

	@Slider(title = "Fade in", description = "Milliseconds until blur and background reach full strength.", category = ANIMATION, min = 1, max = 2000)
	public static int fadeTimeMillis = 300;

	@Slider(title = "Fade out", description = "Milliseconds until blur and background disappear.", category = ANIMATION, min = 1, max = 2000)
	public static int fadeOutTimeMillis = 300;

	@Dropdown(title = "Blur easing", category = ANIMATION, options = {"Flat", "Sine", "Quad", "Cubic", "Quart", "Quint", "Expo", "Circ", "Back", "Elastic"})
	public static Easing blurAnimationCurve = Easing.FLAT;

	@Dropdown(title = "Background easing", category = ANIMATION, options = {"Flat", "Sine", "Quad", "Cubic", "Quart", "Quint", "Expo", "Circ", "Back", "Elastic"})
	public static Easing backgroundAnimationCurve = Easing.FLAT;

	private LegacyBlurConfig() {
		super("blur.json", "GUI Blur", Category.VISUALS);
	}

	@Override protected void onInitialized(Tree tree) {
        ModernLegacyNativeProfiles.importNativeBlurSchema(hadLegacyDocument());
        normalize();
        tree.onAllProps((path, property) -> property.addCallback(value -> {
            ModernLegacyNativeProfiles.applyBlur();
            return false;
        }));
        ModernLegacyNativeProfiles.applyBlur();
        if (!hadLegacyDocument()) save();
    }

	private static void normalize() {
		configVersion = 4;
		if (gradientStartColor == null) gradientStartColor = new PolyColor(75 << 24);
		if (gradientEndColor == null) gradientEndColor = new PolyColor(75 << 24);
		radius = clamp(radius, 0, 100);
		gradientRotation = clamp(gradientRotation, 0, 360);
		fadeTimeMillis = clamp(fadeTimeMillis, 1, 2000);
		fadeOutTimeMillis = clamp(fadeOutTimeMillis, 1, 2000);
		if (forceEnabledScreens == null) forceEnabledScreens = new String[0];
		if (forceDisabledScreens == null) forceDisabledScreens = new String[0];
		if (blurAnimationCurve == null) blurAnimationCurve = Easing.FLAT;
		if (backgroundAnimationCurve == null) backgroundAnimationCurve = Easing.FLAT;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

}
