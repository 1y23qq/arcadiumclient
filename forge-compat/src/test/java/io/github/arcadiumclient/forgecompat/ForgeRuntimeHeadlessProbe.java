package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.reflect.ForgeReflection;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;

import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

public final class ForgeRuntimeHeadlessProbe {
    private static void stage(String value) { System.out.println("ARCADIUM_FML_PROBE " + value); }

    public static void main(String[] arguments) {
        try {
            run(Path.of(arguments[0]), Path.of(arguments[1]));
            stage("SUCCESS");
        } catch (Throwable failure) {
            while ((failure instanceof InvocationTargetException || failure instanceof ExceptionInInitializerError)
                    && failure.getCause() != null) failure = failure.getCause();
            stage("FAILED " + failure.getClass().getName());
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(Path root, Path output) throws Exception {
        run(root, output, null);
    }

    @FunctionalInterface
    interface RuntimeCheck { void verify(URLClassLoader target) throws Exception; }

    @FunctionalInterface
    interface TargetFactory { URLClassLoader create(URL[] urls, ClassLoader parent) throws Exception; }

    static void run(Path root, Path output, RuntimeCheck check) throws Exception {
        run(root, output, check, URLClassLoader::new);
    }

    static void run(Path root, Path output, RuntimeCheck check, TargetFactory targetFactory) throws Exception {
        Files.createDirectories(output);
        Path originalForge = root.resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        if (!ForgeModScanner.sha256(originalForge).equals(ForgeCompatBootstrap.FORGE_SHA256)) {
            throw new IllegalStateException("Original Forge verification failed");
        }
        Path loom = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(Files.readString(loom.resolve("calamus-gen2-intermediary-v2.tiny"))), tree);
        var resolverConstructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl")
            .getDeclaredConstructor(MappingTree.class, String.class);
        resolverConstructor.setAccessible(true);
        MappingResolver resolver = (MappingResolver) resolverConstructor.newInstance(tree, "intermediary");
        ForgeMappings mappings;
        try (var input = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver, input);
        }
        stage("MAPPINGS_READY");
        Path patches = output.resolve("patches.jar");
        try (var input = ForgeRuntimeHeadlessProbe.class.getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            if (input == null) throw new IllegalStateException("Trusted game patches are missing");
            Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(loom.resolve("minecraft-client.jar"), patches, output.resolve("game-cache"));
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        transformer.index(patched, mappings, true);
        transformer.index(originalForge, mappings, true);
        Path runtimeGame = transformer.transform(patched, output.resolve("runtime-game"), mappings, true, null);
        Path runtimeForge = transformer.transform(originalForge, output.resolve("runtime-api"), mappings, true, new ForgeRuntimePatches());
        stage("TRUSTED_RUNTIME_READY");
        List<Path> paths = new ArrayList<>();
        paths.add(runtimeForge);
        paths.add(runtimeGame);
        for (String value : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!paths.contains(path)) paths.add(path);
        }
        URL[] urls = paths.stream().map(path -> {
            try { return path.toUri().toURL(); }
            catch (Exception failure) { throw new IllegalArgumentException(failure); }
        }).toArray(URL[]::new);
        try (URLClassLoader target = targetFactory.create(urls, ForgeRuntimeHeadlessProbe.class.getClassLoader())) {
            FixtureLauncher host = new FixtureLauncher(target, paths);
            Path game = Files.createDirectories(output.resolve("game"));
            Path mods = Files.createDirectories(output.resolve("session/mods"));
            System.setProperty("arcadium.forge.prepared", "true");
            ForgeRuntimeContext.configure(game, mods, originalForge, patched, runtimeGame, List.of());
            ForgeReflection.configure(mappings);
            Launch.configure(host, mappings, game, game.resolve("assets"), List.of());
            Thread.currentThread().setContextClassLoader(target);
            stage("ADAPTER_READY");
            ForgeCompatBootstrap.bootstrapFml(mods.getParent(), game, game.resolve("assets"));
            stage("FML_BOOTSTRAP_READY");
            Class<?> loaderType = target.loadClass("net.minecraftforge.fml.common.Loader");
            Object loader = loaderType.getMethod("instance").invoke(null);
            if (loader.getClass().getClassLoader() != target) throw new IllegalStateException("Forge Loader is not owned by the target host");
            var initialize = loaderType.getDeclaredMethod("initializeLoader");
            initialize.setAccessible(true);
            initialize.invoke(loader);
            var modsField = loaderType.getDeclaredField("canonicalModsDir");
            modsField.setAccessible(true);
            if (!mods.toRealPath().toFile().equals(modsField.get(loader))) throw new IllegalStateException("The shadow mod directory was not configured");
            Class<?> manager = target.loadClass("net.minecraftforge.fml.relauncher.CoreModManager");
            var mcDir = manager.getDeclaredField("mcDir"); mcDir.setAccessible(true);
            if (!game.toFile().equals(mcDir.get(null))) throw new IllegalStateException("Coremod game directory was not restored");
            Object side = target.loadClass("net.minecraftforge.fml.relauncher.FMLLaunchHandler").getMethod("side").invoke(null);
            if (!side.toString().equals("CLIENT")) throw new IllegalStateException("The real FML client side was not initialized");
            stage("REAL_LOADER_READY");
            Class<?> eventHandler = target.loadClass("net.minecraftforge.fml.common.eventhandler.ASMEventHandler");
            var generatedLoader = eventHandler.getDeclaredField("LOADER"); generatedLoader.setAccessible(true);
            if (((ClassLoader) generatedLoader.get(null)).getParent() != target) throw new IllegalStateException("Generated event classes have the wrong parent");
            stage("EVENT_PARENT_READY");
            if (check != null) check.verify(target);
            if (Launch.classLoader.getTransformers().isEmpty()) throw new IllegalStateException("The trusted Forge transformers were not registered");
            Files.writeString(output.resolve("result.json"), "{\"loaderInitialized\":true,\"realClientSide\":true,\"realGameDirectory\":true,\"shadowModDirectory\":true,\"generatedEventParent\":true,\"guestModsExecuted\":false}\n");
            Launch.classLoader.close();
        }
    }

    private static final class FixtureLauncher implements FabricLauncher {
        private final URLClassLoader target;
        private final List<Path> paths;
        FixtureLauncher(URLClassLoader target, List<Path> paths) { this.target = target; this.paths = paths; }
        @Override public ClassLoader getTargetClassLoader() { return target; }
        @Override public List<Path> getClassPath() { return List.copyOf(paths); }
        @Override public Class<?> loadIntoTarget(String name) throws ClassNotFoundException { return target.loadClass(name); }
        @Override public InputStream getResourceAsStream(String name) { return target.getResourceAsStream(name); }
        @Override public byte[] getClassByteArray(String name, boolean transformed) throws java.io.IOException {
            if (transformed) throw new IllegalArgumentException("The trusted probe reads raw classes only");
            try (var input = target.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) return null;
                byte[] bytes = input.readAllBytes();
                if (!new ClassReader(bytes).getClassName().equals(name.replace('.', '/'))) throw new IllegalStateException("Wrong raw class namespace");
                return bytes;
            }
        }
        @Override public boolean isClassLoaded(String name) { return false; }
        @Override public EnvType getEnvironmentType() { return EnvType.CLIENT; }
        @Override public boolean isDevelopment() { return false; }
        @Override public void addToClassPath(Path path, String... prefixes) { throw new UnsupportedOperationException("The trusted probe classpath is fixed"); }
        @Override public void setAllowedPrefixes(Path path, String... prefixes) { throw new UnsupportedOperationException("The trusted probe prefix policy is fixed"); }
        @Override public void setValidParentClassPath(Collection<Path> paths) { throw new UnsupportedOperationException("The trusted probe parent is fixed"); }
        @Override public MappingConfiguration getMappingConfiguration() { throw new UnsupportedOperationException("The trusted probe mappings are explicit"); }
        @Override public String getEntrypoint() { throw new UnsupportedOperationException("The trusted probe never starts Minecraft"); }
        @Override public Manifest getManifest(Path path) { throw new UnsupportedOperationException("The trusted probe uses the real runtime resources"); }
    }
}
