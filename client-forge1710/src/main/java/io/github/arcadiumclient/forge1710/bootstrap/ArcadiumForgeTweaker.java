// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import cpw.mods.fml.common.launcher.FMLTweaker;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ArcadiumForgeTweaker extends FMLTweaker {
    @Override public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        File runtime = new File(System.getProperty("arcadium.runtimeGameDir", gameDir.getAbsolutePath()));
        List<String> isolatedArgs = new ArrayList<>(args);
        int position = isolatedArgs.indexOf("--gameDir");
        if (position >= 0 && position + 1 < isolatedArgs.size()) isolatedArgs.set(position + 1, runtime.getAbsolutePath());
        super.acceptOptions(isolatedArgs, runtime, assetsDir, profile);
        try {
            var location = FMLTweaker.class.getDeclaredField("jarLocation");
            location.setAccessible(true);
            location.set(null, FMLTweaker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Class.forName("net.minecraft.launchwrapper.Launch").getField("minecraftHome").set(null, runtime);
        } catch (ReflectiveOperationException | java.net.URISyntaxException error) {
            throw new IllegalStateException("Could not separate Forge mod storage from shared Minecraft data.", error);
        }
    }

    @Override public String[] getLaunchArguments() {
        List<String> arguments = new ArrayList<>(Arrays.asList(super.getLaunchArguments()));
        String shared = System.getProperty("arcadium.userGameDir");
        if (shared != null) {
            int position = arguments.indexOf("--gameDir");
            if (position >= 0 && position + 1 < arguments.size()) arguments.set(position + 1, shared);
            else { arguments.add("--gameDir"); arguments.add(shared); }
        }
        return arguments.toArray(String[]::new);
    }
}
