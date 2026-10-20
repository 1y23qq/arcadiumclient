package dev.microcontrollers.shaketweaks;

import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import dev.microcontrollers.shaketweaks.config.ShakeTweaksConfig;
import net.fabricmc.api.ModInitializer;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;

import java.util.Objects;

@Entrypoint
public class ShakeTweaks implements ModInitializer {
	@Override
	public void onInitialize() {
		ShakeTweaksConfig.INSTANCE.preload();

		// idea from https://github.com/jumpingpxl/NoBob-Fabric-1.17
		MinecraftClientEvents.TICK_END.register(minecraft -> {
			if (ShakeTweaksConfig.disableHorizontalBobbing && Objects.nonNull(minecraft.player)) {
                minecraft.player.walkDistance = 0.0F;
			}
        });
	}
}
