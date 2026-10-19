package io.github.arcadiumclient.forgecompat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;

public final class ArcadiumLaunch {
    private static final String KNOT_CLIENT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    private ArcadiumLaunch() { }

    public static void main(String[] arguments) throws Throwable {
        Path game = argumentDirectory(arguments, "--gameDir", Path.of("."));
        Path active = Path.of(System.getProperty("fabric.modsFolder", game.resolve("mods").toString()));
        if (!active.isAbsolute()) active = game.resolve(active);
        var candidates = new ForgeModScanner().discover(game, active);
        if (candidates.isEmpty()) {
            invoke(Thread.currentThread().getContextClassLoader(), arguments);
            return;
        }
        Path cache = game.resolve(".arcadium/forge-compat");
        Files.createDirectories(cache);
        Path patches = ForgeRuntimeArtifacts.prepare(cache).patches();
        List<Path> classpath = classpath();
        Path client = findClient(classpath);
        Path patched = new ForgeGamePatcher().patch(client, patches, cache.resolve("game"));
        List<URL> urls = new ArrayList<>();
        List<String> effectiveClasspath = new ArrayList<>();
        for (Path path : classpath) {
            Path effective = path.equals(client) ? patched : path;
            urls.add(effective.toUri().toURL());
            effectiveClasspath.add(effective.toString());
        }
        System.setProperty("java.class.path", String.join(File.pathSeparator, effectiveClasspath));
        System.setProperty("fabric.gameJarPath.client", patched.toString());
        System.setProperty("arcadium.forge.originalClient", client.toString());
        System.setProperty("arcadium.forge.prepared", "true");
        System.setProperty("arcadium.forge.assetsDir", argumentDirectory(arguments, "--assetsDir", game.resolve("assets")).toString());
        System.out.println("[Arcadium] Preparing Forge 1.8.9 compatibility for " + candidates.size() + " mod archives.");
        try (ForgeGameCacheClassLoader isolated = new ForgeGameCacheClassLoader(urls.toArray(URL[]::new), ForgeModScanner.sha256(patched))) {
            Thread.currentThread().setContextClassLoader(isolated);
            invoke(isolated, arguments);
        }
    }

    private static void invoke(ClassLoader loader, String[] arguments) throws Throwable {
        try { Class.forName(KNOT_CLIENT, true, loader).getMethod("main", String[].class).invoke(null, (Object) arguments); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }

    private static List<Path> classpath() throws IOException {
        Set<Path> result = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) && !Files.isDirectory(path)) throw new IOException("Missing game classpath entry: " + path.getFileName());
            result.add(path.toRealPath());
        }
        return List.copyOf(result);
    }

    private static Path findClient(List<Path> paths) throws IOException {
        for (Path path : paths) {
            if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            try (ZipFile jar = new ZipFile(path.toFile())) {
                if (jar.getEntry("ave.class") != null && jar.getEntry("net/minecraft/client/main/Main.class") != null) return path;
            }
        }
        throw new IOException("The original Minecraft 1.8.9 client archive could not be found on the game classpath.");
    }

    static Path argumentDirectory(String[] arguments, String option, Path fallback) {
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].equals(option) && index + 1 < arguments.length) return Path.of(arguments[index + 1]).toAbsolutePath().normalize();
            if (arguments[index].startsWith(option + "=")) return Path.of(arguments[index].substring(option.length() + 1)).toAbsolutePath().normalize();
        }
        return fallback.toAbsolutePath().normalize();
    }

    static Path resource(String name, Path destination) throws IOException {
        byte[] data;
        try (var input = ArcadiumLaunch.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing Arcadium Forge compatibility resource: " + name);
            data = input.readAllBytes();
        }
        if (!Files.isRegularFile(destination) || !java.util.Arrays.equals(data, Files.readAllBytes(destination))) {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "resource-", ".tmp");
            Files.write(temporary, data);
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }
}
