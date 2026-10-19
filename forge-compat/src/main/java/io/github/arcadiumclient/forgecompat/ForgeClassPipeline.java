package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.AccessTransformerRules;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

final class ForgeClassPipeline extends AbstractMap<String, byte[]> {
    private final FabricLauncher launcher;
    private final ForgeMappings mappings;
    private final AccessTransformerRules access;
    private final Map<String, byte[]> fabricPatches;
    private final Set<String> guestClasses;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> transforming = ThreadLocal.withInitial(() -> false);
    private volatile boolean ready;

    private ForgeClassPipeline(FabricLauncher launcher, ForgeMappings mappings, AccessTransformerRules access,
                               Map<String, byte[]> fabricPatches, Set<String> guestClasses) {
        this.launcher = launcher;
        this.mappings = mappings;
        this.access = access;
        this.fabricPatches = fabricPatches;
        this.guestClasses = guestClasses;
    }

    static ForgeClassPipeline install(FabricLauncher launcher, ForgeMappings mappings,
                                     AccessTransformerRules access, List<Path> jars) throws IOException {
        Set<String> classes = new HashSet<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                zip.stream().filter(entry -> entry.getName().endsWith(".class")).forEach(entry ->
                        classes.add(entry.getName().substring(0, entry.getName().length() - 6)));
            }
        }
        Object transformer = net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE.getGameProvider().getEntrypointTransformer();
        try {
            Field field = transformer.getClass().getDeclaredField("patchedClasses");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, byte[]> patches = (Map<String, byte[]>) field.get(transformer);
            if (patches == null || patches instanceof ForgeClassPipeline) {
                throw new IOException("The host class transformer is not ready for Forge compatibility.");
            }
            ForgeClassPipeline pipeline = new ForgeClassPipeline(launcher, mappings, access, patches, Set.copyOf(classes));
            field.set(transformer, pipeline);
            return pipeline;
        } catch (ReflectiveOperationException exception) {
            throw new IOException("This Fabric Loader version cannot install the Forge compatibility class pipeline.", exception);
        }
    }

    void finishRegistration() { cache.clear(); ready = true; }

    @Override public byte[] get(Object key) {
        if (!(key instanceof String name)) return null;
        String internal = name.replace('.', '/');
        byte[] original = fabricPatches.get(name);
        boolean hostRenderer = HostEntityRenderPassCompatibility.RENDERER.equals(internal)
            || HostMatrixCompatibility.CAPTURE.equals(internal);
        boolean legacyTarget = internal.startsWith("net/minecraft/") || guestClasses.contains(internal) || access.targets(internal);
        boolean target = hostRenderer || legacyTarget;
        if (!target || transforming.get()) return original;
        if (ready) {
            byte[] cached = cache.get(name);
            if (cached != null) return cached;
        }
        transforming.set(true);
        try {
            if (original == null) original = launcher.getClassByteArray(name, false);
            if (original == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(original).accept(node, 0);
            access.apply(node);
            HostApiReferenceCompatibility.normalize(node, mappings);
            HostEntityRenderPassCompatibility.normalize(node, mappings);
            HostMatrixCompatibility.normalizeNative(node);
            HostExitCompatibility.normalize(node);
            HostBiomeCompatibility.normalize(node, mappings);
            HostLoginCompatibility.normalize(node, mappings);
            HostTooltipCompatibility.normalize(node, mappings, launcher);
            HostRaytraceCompatibility.normalize(node, mappings);
            HostItemRenderCompatibility.normalize(node, mappings);
            HostArmorCompatibility.normalize(node, mappings);
            HostHeadRotationCompatibility.normalize(node, mappings);
            HostServerShutdownCompatibility.normalize(node, mappings);
            HostLocalizationCompatibility.normalize(node, mappings);
            if (FabricLoader.getInstance().isModLoaded("osl-registries")) {
                HostRegistryCompatibility.normalize(node, mappings);
            }
            ClassWriter accessed = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(accessed);
            byte[] bytes = accessed.toByteArray();
            if (legacyTarget && Launch.classLoader != null && !Launch.classLoader.isTransformationExcluded(internal)) {
                List<IClassTransformer> transformers = Launch.classLoader.getTransformers();
                if (!transformers.isEmpty()) {
                    ClassWriter legacy = new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassRemapper(legacy, mappings.runtimeToSrg()), 0);
                    bytes = legacy.toByteArray();
                    String legacyName = mappings.classToSrg(internal).replace('/', '.');
                    for (IClassTransformer transformer : transformers) {
                        byte[] result = transformer.transform(legacyName, legacyName, bytes);
                        if (result == null) throw new IllegalStateException("A Forge transformer removed class " + legacyName + ".");
                        bytes = result;
                    }
                    ClassWriter runtime = new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassRemapper(runtime, mappings.srgToRuntime()), 0);
                    bytes = runtime.toByteArray();
                }
            }
            HostClassMetadata.synchronize(internal, bytes);
            if (ready) cache.put(name, bytes);
            return bytes;
        } catch (Throwable exception) {
            throw new IllegalStateException("Forge compatibility could not transform " + name + ".", exception);
        } finally { transforming.set(false); }
    }

    @Override public Set<Entry<String, byte[]>> entrySet() { return fabricPatches.entrySet(); }
}
