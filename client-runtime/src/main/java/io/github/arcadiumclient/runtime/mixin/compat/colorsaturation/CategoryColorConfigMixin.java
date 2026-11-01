// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.colorsaturation;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorOptions;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorProfile;
import org.polyfrost.oneconfig.api.config.v1.annotations.DependsOn;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@IfModLoaded("colorsaturation")
@Mixin(targets = "org.polyfrost.colorsaturation.client.ColorSaturationConfig", remap = false)
public abstract class CategoryColorConfigMixin implements CategoryColorOptions {
    @Shadow public static boolean isEnabled;
    @Shadow public static float strength;
    @Shadow public static float contrast;
    @Shadow public static float brightness;
    @Shadow public static float hue;

    @Unique
    @Switch(title = "Separate Entity Colors", subcategory = "Entities",
            description = "Use separate colors for players, mobs, particles and held items. The original sliders control blocks and the world.")
    public boolean arcadiumSeparateEntityColors;

    @Unique
    @DependsOn("arcadiumSeparateEntityColors")
    @Slider(title = "Entity Saturation Strength", subcategory = "Entities", min = -1, max = 5, step = 0.05f)
    public float arcadiumEntityStrength = 1;

    @Unique
    @DependsOn("arcadiumSeparateEntityColors")
    @Slider(title = "Entity Contrast", subcategory = "Entities", min = 0, max = 2, step = 0.05f)
    public float arcadiumEntityContrast = 1;

    @Unique
    @DependsOn("arcadiumSeparateEntityColors")
    @Slider(title = "Entity Brightness", subcategory = "Entities", min = 0, max = 2, step = 0.05f)
    public float arcadiumEntityBrightness = 1;

    @Unique
    @DependsOn("arcadiumSeparateEntityColors")
    @Slider(title = "Entity Hue Shift", subcategory = "Entities", min = -180, max = 180, step = 1)
    public float arcadiumEntityHue;

    @Override public boolean arcadium$colorEnabled() { return isEnabled; }
    @Override public boolean arcadium$separateEntityColors() { return arcadiumSeparateEntityColors; }
    @Override public void arcadium$readColors(CategoryColorProfile world, CategoryColorProfile entities) {
        world.set(strength, contrast, brightness, hue);
        entities.set(arcadiumEntityStrength, arcadiumEntityContrast, arcadiumEntityBrightness, arcadiumEntityHue);
    }
}
