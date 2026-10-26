package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class ForgeStartupQa {
    private int frames;
    private boolean done;

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        OneConfigConfig.limitUnfocusedFps = false;
        mc.field_71474_y.field_82881_y = false;
        if (mc.field_71462_r == null || ++frames < 30) return;
        done = true;
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", true);
            result.put("uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
            result.put("renderedTitleFrames", frames);
            result.put("screen", mc.field_71462_r.getClass().getName());
            result.put("heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            result.put("loadedClasses", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
            result.put("mods", Loader.instance().getActiveModList().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).toList());
            result.put("processId", ProcessHandle.current().pid());
            Files.writeString(Path.of(System.getProperty("arcadium.startupQa.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (Boolean.getBoolean("arcadium.qa.startupOnly")) mc.func_71400_g();
        } catch (Exception error) { throw new IllegalStateException("Startup verification failed", error); }
    }
}
