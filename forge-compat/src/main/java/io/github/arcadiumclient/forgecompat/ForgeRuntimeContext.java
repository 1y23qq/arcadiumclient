package io.github.arcadiumclient.forgecompat;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public final class ForgeRuntimeContext {
    private record Configuration(Path game, Path mods, Path originalForge, Path officialGame, Path runtimeGame, List<Path> runtimeMods) {}
    private static volatile Configuration configuration;

    private ForgeRuntimeContext() { }

    public static void configure(Path game, Path mods, Path originalForge) {
        configure(game, mods, originalForge, null, List.of());
    }

    public static void configure(Path game, Path mods, Path originalForge, Path runtimeGame, List<Path> runtimeMods) {
        configure(game, mods, originalForge, null, runtimeGame, runtimeMods);
    }

    public static void configure(Path game, Path mods, Path originalForge, Path officialGame, Path runtimeGame, List<Path> runtimeMods) {
        configuration = new Configuration(game.toAbsolutePath().normalize(), mods.toAbsolutePath().normalize(),
            originalForge.toAbsolutePath().normalize(), officialGame == null ? null : officialGame.toAbsolutePath().normalize(),
            runtimeGame == null ? null : runtimeGame.toAbsolutePath().normalize(),
            runtimeMods.stream().map(path -> path.toAbsolutePath().normalize()).toList());
    }

    public static void configureLoader(Object loader) {
        requirePreparedGame();
        Configuration current = configuration;
        try {
            Files.createDirectories(current.mods);
            Files.createDirectories(current.game.resolve("config"));
            setDirectory(loader, "canonicalModsDir", current.mods.toRealPath().toFile());
            setDirectory(loader, "canonicalConfigDir", current.game.resolve("config").toRealPath().toFile());
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new IllegalStateException("Forge mod directories could not be configured.", exception);
        }
    }

    private static void setDirectory(Object loader, String name, File directory) throws ReflectiveOperationException {
        Field field = loader.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(loader, directory);
    }

    public static LaunchClassLoader legacyClassLoader(ClassLoader current) {
        if (Launch.classLoader == null) throw new IllegalStateException("The Forge compatibility class loader is not ready.");
        if (current != Launch.classLoader && current != Launch.classLoader.getParent()) {
            throw new IllegalArgumentException("Forge ModClassLoader must delegate to the configured Knot host.");
        }
        return Launch.classLoader;
    }

    public static ClassLoader hostClassLoader() {
        if (Launch.classLoader == null) throw new IllegalStateException("The Forge compatibility class loader is not ready.");
        return Launch.classLoader.getParent();
    }

    public static File[] parentSources() {
        requirePreparedGame();
        Configuration current = configuration;
        LinkedHashSet<File> sources = new LinkedHashSet<>();
        if (current.runtimeGame == null) throw new IllegalStateException("The host Minecraft runtime origin is not configured.");
        try {
            sources.add(current.runtimeGame.toRealPath().toFile());
            for (Path mod : current.runtimeMods) sources.add(mod.toRealPath().toFile());
        } catch (java.io.IOException cause) {
            throw new IllegalStateException("A prepared Forge classpath source is missing.", cause);
        }
        return sources.toArray(File[]::new);
    }

    public static void handleLaunch(File game, LaunchClassLoader loader, Object tweaker) {
        requirePreparedGame();
        Configuration current = configuration;
        if (!current.mods.getFileName().toString().equals("mods") || !game.toPath().toAbsolutePath().normalize().equals(current.game)) {
            throw new IllegalStateException("Forge coremod discovery requires the prepared session and real game directory.");
        }
        try {
            Class<?> manager = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager", true, hostClassLoader());
            Class<?> type = Class.forName("net.minecraftforge.fml.common.launcher.FMLTweaker", false, hostClassLoader());
            Field mcDir = manager.getDeclaredField("mcDir");
            mcDir.setAccessible(true);
            try {
                manager.getMethod("handleLaunch", File.class, LaunchClassLoader.class, type)
                    .invoke(null, current.mods.getParent().toFile(), loader, tweaker);
            } finally {
                mcDir.set(null, current.game.toFile());
            }
        } catch (InvocationTargetException cause) {
            Throwable failure = cause.getCause();
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Forge coremod discovery failed.", failure);
        } catch (ReflectiveOperationException cause) {
            throw new IllegalStateException("The Forge coremod launch API is incompatible.", cause);
        }
    }

    public static String createTransformerWrapper(LaunchClassLoader loader, String parent, String coreMod) {
        verifyRuntimeSource();
        if (LaunchClassLoader.getHostHandledTransformers().contains(parent)) return parent;
        try {
            Class<?> wrapper = Class.forName("net.minecraftforge.fml.common.asm.ASMTransformerWrapper", true, hostClassLoader());
            synchronized (wrapper) {
                var name = wrapper.getDeclaredMethod("getWrapperName", String.class);
                name.setAccessible(true);
                String generated = (String) name.invoke(null, parent);
                String resource = generated.replace('.', '/') + ".class";
                for (var entry : java.util.Map.of("wrapperModMap", coreMod, "wrapperParentMap", parent).entrySet()) {
                    Field field = wrapper.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked") var values = (java.util.Map<String, String>) field.get(null);
                    values.put(resource, entry.getValue());
                }
                var make = wrapper.getDeclaredMethod("makeWrapper", String.class);
                make.setAccessible(true);
                byte[] bytes = (byte[]) make.invoke(null, resource);
                loader.defineLegacyHelperClass(generated, bytes);
                return generated;
            }
        } catch (InvocationTargetException cause) {
            Throwable failure = cause.getCause();
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("The real Forge transformer wrapper generator failed.", failure);
        } catch (ReflectiveOperationException cause) {
            throw new IllegalStateException("The Forge transformer wrapper API is incompatible.", cause);
        }
    }

    // FML field metadata needs Notch bytes; ordinary coremods retain the SRG facade.
    public static byte[] readOfficialClass(Object patchManager, String name, String transformedName,
                                           LaunchClassLoader loader) throws java.io.IOException {
        requirePreparedGame();
        Configuration current = configuration;
        if (current.officialGame == null) throw new IllegalStateException("The officially patched Minecraft source is not configured.");
        if (loader != Launch.classLoader) throw new IllegalArgumentException("Official Forge metadata must use the configured legacy loader.");
        String internal = name.replace('.', '/');
        if (!internal.matches("[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*")) {
            throw new java.io.IOException("Invalid official Forge metadata class name.");
        }
        for (Path source : List.of(current.officialGame, current.originalForge)) {
            try (var zip = new java.util.zip.ZipFile(source.toFile())) {
                var entry = zip.getEntry(internal + ".class");
                if (entry == null) continue;
                if (entry.getSize() > 16 * 1024 * 1024) throw new java.io.IOException("Official Forge metadata class exceeds its size limit.");
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(16 * 1024 * 1024 + 1); }
                if (bytes.length > 16 * 1024 * 1024) throw new java.io.IOException("Official Forge metadata class exceeds its size limit.");
                try {
                    if (!new org.objectweb.asm.ClassReader(bytes).getClassName().equals(internal)) {
                        throw new java.io.IOException("Official Forge metadata class has a different name.");
                    }
                } catch (IllegalArgumentException | IndexOutOfBoundsException cause) {
                    throw new java.io.IOException("Official Forge metadata contains an invalid class.", cause);
                }
                return bytes;
            }
        }
        return null;
    }

    public static void verifyRuntimeSource() {
        requirePreparedGame();
        try {
            if (!ForgeModScanner.sha256(configuration.originalForge)
                    .equals(ForgeCompatBootstrap.FORGE_SHA256)) {
                throw new IllegalStateException("The original Forge runtime failed verification.");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("The original Forge runtime could not be verified.", exception);
        }
    }

    public static void requirePreparedGame() {
        if (!Boolean.getBoolean("arcadium.forge.prepared") || configuration == null) {
            throw new IllegalStateException("Forge game patches have not been prepared by the Arcadium launcher.");
        }
    }
}
