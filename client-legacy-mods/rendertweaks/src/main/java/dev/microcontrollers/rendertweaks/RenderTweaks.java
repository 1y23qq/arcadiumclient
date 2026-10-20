package dev.microcontrollers.rendertweaks;

import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.fabricmc.api.ModInitializer;

public class RenderTweaks implements ModInitializer {
	@Override
	public void onInitialize() {
		RenderTweaksConfig.INSTANCE.preload();
	}
}
