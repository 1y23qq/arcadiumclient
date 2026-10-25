// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import java.util.Map;
import java.util.LinkedHashMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;

final class NativeGraphicsQa {
    private NativeGraphicsQa() {}
    static Map<String, Object> snapshot() {
        var device = RenderSystem.getDevice();
        var info = device.getDeviceInfo();
        var result = new LinkedHashMap<String, Object>();
        result.putAll(Map.of("graphicsBackend", info.backendName(), "gpuName", info.name(), "gpuVendor", info.vendorName(),
                "gpuDriver", info.driverInfo(), "gpuDevice", device.getClass().getName()));
        boolean loaded = FabricLoader.getInstance().isModLoaded("nvidium"), compatible = false, enabled = false;
        result.put("nvidiumLoaded", loaded);
        if (loaded) try {
            var type = Class.forName("me.cortex.nvidium.Nvidium");
            compatible = type.getField("IS_COMPATIBLE").getBoolean(null);
            enabled = type.getField("IS_ENABLED").getBoolean(null);
            result.put("nvidiumCompatible", compatible); result.put("nvidiumEnabled", enabled);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not inspect native Nvidium activation", failure); }
        if (Boolean.getBoolean("arcadium.guiQa.requireNvidium") && (!loaded || !compatible || !enabled))
            throw new IllegalStateException("Nvidium activation was required but loaded=" + loaded + ", compatible=" + compatible + ", enabled=" + enabled);
        return result;
    }
}
