package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.reflect.ForgeReflection;
import io.github.arcadiumclient.forgecompat.transform.AccessTransformerRules;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.tree.ClassNode;

public final class ForgeCompatBootstrap implements PreLaunchEntrypoint {
    public static final String FORGE_SHA256 = "596512ad5f12f95d8a3170321543d4455d23b8fe649c68580c5f828fe74f6668";

    @Override public void onPreLaunch() {
        try { prepare(); }
        catch (Throwable exception) {
            if (exception instanceof InvocationTargetException invocation) exception = invocation.getCause();
            throw new IllegalStateException("Arcadium could not initialize Forge 1.8.9 mod compatibility: "
                    + exception.getMessage(), exception);
        }
    }

    private static void prepare() throws Exception {
        FabricLoader loader = FabricLoader.getInstance();
        Path game = loader.getGameDir();
        Path active = Path.of(System.getProperty("fabric.modsFolder", game.resolve("mods").toString()));
        if (!active.isAbsolute()) active = game.resolve(active);
        List<ForgeModScanner.ModFile> candidates = new ForgeModScanner().discover(game, active);
        if (candidates.isEmpty()) return;
        if (!Boolean.getBoolean("arcadium.forge.prepared")) {
            throw new IOException("Forge mods must be started through the Arcadium launcher. Restart the game from Arcadium Client.");
        }
        Path cache = game.resolve(".arcadium/forge-compat");
        FabricLauncher launcher = FabricLauncherBase.getLauncher();
        ForgeRuntimeLibraries.install(launcher, cache);
        ForgeRuntimeArtifacts.Prepared artifacts = ForgeRuntimeArtifacts.prepare(cache);
        Path originalForge = artifacts.original();
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(artifacts.mappings(), StandardCharsets.UTF_8)) {
            mappings = new ForgeMappings(loader.getMappingResolver(), reader);
        }
        ForgeReflection.configure(mappings);
        HostCompatibilityPatches.install(mappings);
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        Path patchedGame = Path.of(System.getProperty("fabric.gameJarPath.client"));
        transformer.index(patchedGame, mappings, true);
        transformer.index(originalForge, mappings, true);
        for (var candidate : candidates) transformer.index(candidate.path(), mappings, false);
        HostMixinCompatibility.install(mappings);
        AccessTransformerRules access = new AccessTransformerRules();
        List<String> forgeAccess;
        try (ZipFile archive = new ZipFile(originalForge.toFile())) {
            forgeAccess = archive.stream().filter(entry -> entry.getName().endsWith("_at.cfg"))
                    .map(java.util.zip.ZipEntry::getName).toList();
        }
        if (!forgeAccess.isEmpty()) access.read(originalForge, forgeAccess, mappings);
        for (var candidate : candidates) access.read(candidate.path(), candidate.accessTransformers(), mappings);
        Path forgeApi = transformer.transform(originalForge, cache.resolve("api"), mappings, true,
                apiPatches(mappings, loader.isModLoaded("osl-registries"), loader.isModLoaded("evergreenhud"), loader.isModLoaded("vanillahud")));
        List<Path> remapped = new ArrayList<>();
        for (var candidate : candidates) {
            if (!candidate.sha256().equals(ForgeModScanner.sha256(candidate.path()))) {
                throw new IOException("A Forge mod changed while the game was starting: " + candidate.path().getFileName());
            }
            remapped.add(transformer.transform(candidate.path(), cache.resolve("remapped"), mappings, false,
                    ForgeRuntimePatches.forMod(candidate.sha256(), mappings)));
        }
        GuestMixinVisibility.install(launcher, remapped);
        Path session = materializeSession(cache, candidates, remapped);
        Path modDirectory = session.resolve("mods");
        List<Path> runtimeMods = new ArrayList<>();
        for (var candidate : candidates) runtimeMods.add(modDirectory.resolve(candidate.path().getFileName()));
        Path runtimeGame = runtimeGame(loader, mappings);
        ForgeRuntimeContext.configure(game, modDirectory, originalForge, patchedGame, runtimeGame, runtimeMods);
        launcher.addToClassPath(forgeApi);
        Path assets = Path.of(System.getProperty("arcadium.forge.assetsDir", game.resolve("assets").toString()));
        Launch.configure(launcher, mappings, game, assets, runtimeMods);
        List<Path> all = new ArrayList<>();
        all.add(forgeApi);
        all.addAll(runtimeMods);
        ForgeClassPipeline pipeline = ForgeClassPipeline.install(launcher, mappings, access, all);
        Set<String> configs = new LinkedHashSet<>();
        for (var candidate : candidates) configs.addAll(candidate.mixinConfigs());
        var addMixin = Class.forName("org.spongepowered.asm.mixin.Mixins", true, FabricLauncherBase.class.getClassLoader())
                .getMethod("addConfiguration", String.class);
        for (String config : configs) addMixin.invoke(null, config);
        bootstrapFml(session, game, assets);
        pipeline.finishRegistration();
        int mods = candidates.stream().mapToInt(candidate -> candidate.mods().size()).sum();
        System.out.println("[Arcadium] Forge compatibility is ready for " + mods + " mods and "
                + Launch.classLoader.getTransformers().size() + " class transformers.");
    }

    static Consumer<ClassNode> apiPatches(ForgeMappings mappings, boolean oslRegistries, boolean evergreenHud, boolean vanillaHud) {
        Consumer<ClassNode> patches = new ForgeRuntimePatches().andThen(HostSplashCompatibility::normalize)
            .andThen(HostMatrixCompatibility::normalizeForge)
            .andThen(node -> HostPerspectiveModelCompatibility.normalize(node, mappings))
            .andThen(node -> HostHudCompatibility.normalize(node, mappings, evergreenHud, vanillaHud));
        if (oslRegistries) HostRegistryRegistrationCompatibility.configure(mappings);
        return oslRegistries ? patches.andThen(node -> HostRegistryCompatibility.normalize(node, mappings))
            .andThen(node -> HostRegistrySnapshotCompatibility.normalize(node, mappings))
            .andThen(node -> HostRegistryMapperCompatibility.normalize(node, mappings))
            .andThen(HostRegistryRegistrationCompatibility::normalize) : patches;
    }

    private static Path materializeSession(Path cache, List<ForgeModScanner.ModFile> files, List<Path> converted) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int index = 0; index < files.size(); index++) {
            digest.update(files.get(index).path().getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(ForgeModScanner.sha256(converted.get(index)).getBytes(StandardCharsets.US_ASCII));
        }
        Path session = cache.resolve("sessions/" + HexFormat.of().formatHex(digest.digest()).substring(0, 32));
        Path mods = session.resolve("mods");
        Files.createDirectories(mods);
        Set<String> names = new java.util.HashSet<>();
        for (int index = 0; index < files.size(); index++) {
            String name = files.get(index).path().getFileName().toString();
            if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) {
                throw new IOException("Two Forge mod archives have the same filename: " + name + ". Rename one of them.");
            }
            Path destination = mods.resolve(name);
            if (!Files.isRegularFile(destination)
                    || !ForgeModScanner.sha256(destination).equals(ForgeModScanner.sha256(converted.get(index)))) {
                Path pending = Files.createTempFile(mods, "mod-", ".tmp");
                Files.copy(converted.get(index), pending, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(pending, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return session;
    }

    static Path runtimeGame(FabricLoader loader, ForgeMappings mappings) throws IOException {
        String main = mappings.classFromSrg("net/minecraft/client/Minecraft") + ".class";
        var minecraft = loader.getModContainer("minecraft")
            .orElseThrow(() -> new IOException("The host Minecraft runtime is missing."));
        LinkedHashSet<Path> sources = new LinkedHashSet<>(minecraft.getOrigin().getPaths());
        var resource = FabricLauncherBase.getLauncher().getTargetClassLoader().getResource(main);
        if (resource != null && resource.getProtocol().equals("jar")) {
            var connection = (java.net.JarURLConnection) resource.openConnection();
            if (connection.getJarFileURL().getProtocol().equals("file")) {
                try { sources.add(Path.of(connection.getJarFileURL().toURI())); }
                catch (java.net.URISyntaxException cause) { throw new IOException("Invalid Minecraft runtime origin.", cause); }
            }
        }
        return runtimeGame(List.copyOf(sources), main);
    }

    static Path runtimeGame(List<Path> sources, String main) throws IOException {
        for (Path path : sources) {
            if (Files.isDirectory(path) && Files.isRegularFile(path.resolve(main))) return path.toRealPath();
            if (Files.isRegularFile(path)) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    if (zip.getEntry(main) != null) return path.toRealPath();
                }
            }
        }
        throw new IOException("The host Minecraft origin does not contain its runtime namespace classes.");
    }

    static void bootstrapFml(Path session, Path game, Path assets) throws Exception {
        ClassLoader host = Launch.classLoader.getParent();
        Class<?> data = Class.forName("net.minecraftforge.fml.relauncher.FMLInjectionData", true, host);
        var build = data.getDeclaredMethod("build", java.io.File.class, LaunchClassLoader.class);
        build.setAccessible(true);
        build.invoke(null, game.toFile(), Launch.classLoader);
        Class<?> loader = Class.forName("net.minecraftforge.fml.common.Loader", false, host);
        Object[] injected = (Object[]) data.getMethod("data").invoke(null);
        loader.getMethod("injectData", Object[].class).invoke(null, (Object) injected);
        ITweaker primary = (ITweaker) Class.forName("net.minecraftforge.fml.common.launcher.FMLTweaker", true, host)
                .getConstructor().newInstance();
        List<ITweaker> tweaks = new ArrayList<>();
        tweaks.add(primary);
        List<String> pending = new ArrayList<>();
        Launch.blackboard.put("Tweaks", tweaks);
        Launch.blackboard.put("TweakClasses", pending);
        primary.acceptOptions(List.of(), game.toFile(), assets.toFile(), "1.8.9");
        primary.injectIntoClassLoader(Launch.classLoader);
        processTweaks(primary, host, game, assets);
        loader.getMethod("injectData", Object[].class).invoke(null, (Object) data.getMethod("data").invoke(null));
        loader.getMethod("instance").invoke(null);
    }

    static void processTweaks(ITweaker primary, ClassLoader host, Path game, Path assets) throws Exception {
        Object tweakValues = Launch.blackboard.get("Tweaks");
        Object classValues = Launch.blackboard.get("TweakClasses");
        if (!(tweakValues instanceof List<?>) || !(classValues instanceof List<?>)) {
            throw new IOException("The legacy launch tweaker lists are missing.");
        }
        @SuppressWarnings("unchecked") List<ITweaker> tweaks = (List<ITweaker>) tweakValues;
        @SuppressWarnings("unchecked") List<String> pending = (List<String>) classValues;
        Set<ITweaker> processed = Collections.newSetFromMap(new IdentityHashMap<>());
        processed.add(primary);
        List<ITweaker> order = new ArrayList<>();
        Set<String> instantiated = new java.util.HashSet<>();
        instantiated.add(primary.getClass().getName());
        int passes = 0;
        while (true) {
            if (++passes > 256) throw new IOException("Forge coremod initialization did not finish.");
            boolean changed = false;
            for (String name : List.copyOf(pending)) {
                pending.remove(name);
                if (!instantiated.add(name)) continue;
                Object value = Class.forName(name, true, host).getConstructor().newInstance();
                if (!(value instanceof ITweaker tweak)) throw new IOException("Invalid Forge launch tweaker: " + name);
                if (tweaks.stream().noneMatch(existing -> existing == tweak)) tweaks.add(tweak);
                changed = true;
            }
            ITweaker next = tweaks.stream().filter(tweak -> !processed.contains(tweak)).findFirst().orElse(null);
            if (next != null) {
                ITweaker tweak = next;
                processed.add(tweak);
                order.add(tweak);
                tweak.acceptOptions(List.of(), game.toFile(), assets.toFile(), "1.8.9");
                tweak.injectIntoClassLoader(Launch.classLoader);
                changed = true;
            }
            if (!changed && pending.isEmpty()) break;
        }
        for (ITweaker tweak : order) {
            if (tweak.getLaunchArguments().length != 0) {
                throw new IOException("Legacy tweaker cannot change launch arguments after Knot initialization: " + tweak.getClass().getName());
            }
        }
    }
}
