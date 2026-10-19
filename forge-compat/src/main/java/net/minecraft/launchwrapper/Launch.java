package net.minecraft.launchwrapper;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.loader.impl.launch.FabricLauncher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Launch {
    public static File minecraftHome;
    public static File assetsDir;
    public static Map<String, Object> blackboard = Collections.synchronizedMap(new HashMap<>());
    public static LaunchClassLoader classLoader;

    private Launch() {}

    public static void configure(FabricLauncher launcher, ForgeMappings mappings, Path gameDir, List<Path> modJars) {
        configure(launcher, mappings, gameDir, gameDir.resolve("assets"), modJars);
    }

    public static synchronized void configure(FabricLauncher launcher, ForgeMappings mappings,
                                               Path gameDir, Path assets, List<Path> modJars) {
        Objects.requireNonNull(launcher, "launcher");
        Objects.requireNonNull(mappings, "mappings");
        Path game = gameDir.toAbsolutePath().normalize();
        Path resources = assets.toAbsolutePath().normalize();
        if (classLoader != null && (!classLoader.isFor(launcher, mappings)
                || !game.toFile().equals(minecraftHome) || !resources.toFile().equals(assetsDir))) {
            throw new IllegalStateException("LaunchWrapper is already configured for another Knot host or game directory");
        }
        if (classLoader == null) {
            List<URL> sources = new ArrayList<>();
            for (Path path : launcher.getClassPath()) sources.add(url(path));
            for (Path path : modJars) sources.add(url(path));
            LaunchClassLoader configured = new LaunchClassLoader(sources.toArray(URL[]::new), launcher, mappings);
            minecraftHome = game.toFile();
            assetsDir = resources.toFile();
            classLoader = configured;
        } else {
            for (Path path : modJars) classLoader.addURL(url(path));
        }
        Objects.requireNonNull(blackboard, "Launch.blackboard");
        blackboard.putIfAbsent("TweakClasses", new ArrayList<String>());
        blackboard.putIfAbsent("Tweaks", new ArrayList<ITweaker>());
        blackboard.putIfAbsent("ArgumentList", new ArrayList<String>());
    }

    private static URL url(Path path) {
        try {
            return path.toAbsolutePath().normalize().toUri().toURL();
        } catch (MalformedURLException cause) {
            throw new IllegalArgumentException("Invalid local LaunchWrapper class path: " + path, cause);
        }
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException("Arcadium already runs in Fabric Knot; LaunchWrapper.main cannot start a second game host");
    }
}
