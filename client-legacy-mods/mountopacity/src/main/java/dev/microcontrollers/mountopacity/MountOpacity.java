package dev.microcontrollers.mountopacity;

import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import dev.microcontrollers.mountopacity.config.MountOpacityConfig;
import net.fabricmc.api.ModInitializer;

@Entrypoint
public class MountOpacity implements ModInitializer {
	@Override
	public void onInitialize() {
		MountOpacityConfig.INSTANCE.preload();
	}
}
