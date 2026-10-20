package dev.microcontrollers.droppeditemtweaks;

import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import dev.microcontrollers.droppeditemtweaks.config.DroppedItemTweaksConfig;
import net.fabricmc.api.ModInitializer;

@Entrypoint
public class DroppedItemTweaks implements ModInitializer {
	@Override
	public void onInitialize() {
		DroppedItemTweaksConfig.INSTANCE.preload();
	}
}
