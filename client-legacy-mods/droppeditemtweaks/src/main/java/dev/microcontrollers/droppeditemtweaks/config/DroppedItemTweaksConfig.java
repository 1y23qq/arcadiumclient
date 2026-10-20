package dev.microcontrollers.droppeditemtweaks.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.ItemList;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

import java.util.ArrayList;
import java.util.List;

public class DroppedItemTweaksConfig extends Config {
    public static final DroppedItemTweaksConfig INSTANCE = new DroppedItemTweaksConfig();

    @Switch(
            title = "dropped-item-tweaks.static-dropped-items.name",
            description = "dropped-item-tweaks.static-dropped-items.description"
    )
    public static boolean staticItems = false;

    @Slider(
            title = "dropped-item-tweaks.dropped-stack-item-count.name",
            description = "dropped-item-tweaks.dropped-stack-item-count.description",
            max = 64
    )
    public static int dropStackCount = 0;

    @Slider(
            title = "dropped-item-tweaks.item-scale.name",
            description = "dropped-item-tweaks.item-scale.description",
            max = 3F,
            step = 0.1F
    )
    public static float itemScale = 1F;

    @Slider(
            title = "dropped-item-tweaks.custom-item-scale.name",
            description = "dropped-item-tweaks.custom-item-scale.description",
            max = 3F,
            step = 0.1F
    )
    public static float uhcOverlay = 0F;

    // TODO: legacy/modern config migration?
    @ItemList(
            title = "dropped-item-tweaks.custom-item-scale-list.name",
            description = "dropped-item-tweaks.custom-item-scale-list.description"
            // keeping reorder just for user cleanliness sake
    )
    public static List<String> uhcItemListLegacy = new ArrayList<>(List.of("minecraft:apple", "minecraft:golden_apple", "minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:gold_block", "minecraft:skull"));

    public DroppedItemTweaksConfig() {
        super("droppeditemtweaksv2.json", "/assets/droppeditemtweaks/icon.png", "Dropped Item Tweaks", Category.QOL);
        save();
    }
}
