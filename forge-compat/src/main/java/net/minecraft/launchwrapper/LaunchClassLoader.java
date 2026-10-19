package net.minecraft.launchwrapper;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LaunchClassLoader extends URLClassLoader {
    public static final int BUFFER_SIZE = 1 << 12;
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final Set<String> HOST_HANDLED = Set.of(
        "net.minecraftforge.fml.common.asm.transformers.PatchingTransformer",
        "net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer"
    );

    private final FabricLauncher launcher;
    private final ForgeMappings mappings;
    private final List<URL> sources = new CopyOnWriteArrayList<>();
    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>();
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private final Set<String> invalidClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> classLoaderExceptions = ConcurrentHashMap.newKeySet();
    private final Set<String> transformerExceptions = ConcurrentHashMap.newKeySet();
    private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> helperBytes = new ConcurrentHashMap<>();
    private final Set<String> negativeResourceCache = ConcurrentHashMap.newKeySet();
    private final Set<String> handledTransformers = ConcurrentHashMap.newKeySet();
    private volatile IClassNameTransformer renameTransformer;

    public LaunchClassLoader(URL[] sources) {
        this(sources, configured().launcher, configured().mappings);
    }

    public LaunchClassLoader(URL[] sources, FabricLauncher launcher, ForgeMappings mappings) {
        super(new URL[0], Objects.requireNonNull(launcher.getTargetClassLoader(), "Knot target class loader"));
        this.launcher = launcher;
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        for (String prefix : List.of("java.", "sun.", "org.lwjgl.", "org.apache.logging.", "net.minecraft.launchwrapper.")) {
            addClassLoaderExclusion(prefix);
        }
        for (String prefix : List.of("javax.", "argo.", "org.objectweb.asm.", "com.google.common.",
                "org.bouncycastle.", "net.minecraft.launchwrapper.injector.")) {
            addTransformerExclusion(prefix);
        }
        for (URL source : sources) addURL(source);
    }

    private static LaunchClassLoader configured() {
        if (Launch.classLoader == null) {
            throw new IllegalStateException("Launch.configure must run before constructing a legacy LaunchClassLoader");
        }
        return Launch.classLoader;
    }

    boolean isFor(FabricLauncher launcher, ForgeMappings mappings) {
        return this.launcher == launcher && this.mappings == mappings;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return findClass(name);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        String source = binary(name);
        String mapped = source;
        IClassNameTransformer rename = renameTransformer;
        if (rename != null && !excluded(classLoaderExceptions, mapped)) mapped = binary(rename.remapClassName(mapped));
        String runtime = mappings.classFromSrg(mapped.replace('.', '/')).replace('/', '.');
        String srg = mappings.classToSrg(runtime.replace('.', '/')).replace('/', '.');
        Class<?> local = findLoadedClass(runtime);
        if (local != null && local.getClassLoader() == this) {
            if (minecraftClass(source, runtime, srg)) {
                throw new ClassNotFoundException("Minecraft classes must be owned by Knot: " + source);
            }
            recordLoaded(source, runtime, srg, local);
            return local;
        }
        if (invalidClasses.contains(source) || invalidClasses.contains(runtime) || invalidClasses.contains(srg)) {
            throw new ClassNotFoundException("Legacy class is marked invalid: " + source);
        }
        try {
            Class<?> loaded = launcher.loadIntoTarget(runtime);
            recordLoaded(source, runtime, srg, loaded);
            return loaded;
        } catch (ClassNotFoundException cause) {
            invalidClasses.add(runtime);
            throw new ClassNotFoundException("Knot cannot load legacy class " + source + " (runtime " + runtime + ")", cause);
        }
    }

    private void recordLoaded(String source, String runtime, String srg, Class<?> loaded) {
        // Legacy cache writes cannot replace the host's Minecraft class identity.
        cachedClasses.put(source, loaded);
        cachedClasses.put(runtime, loaded);
        cachedClasses.put(srg, loaded);
    }

    public Class<?> defineLegacyHelperClass(String name, byte[] srgBytes) {
        String source = binary(name);
        String runtime = mappings.classFromSrg(source.replace('.', '/')).replace('/', '.');
        String srg = mappings.classToSrg(runtime.replace('.', '/')).replace('/', '.');
        if (minecraftClass(source, runtime, srg)) {
            throw new IllegalArgumentException("Minecraft classes cannot be defined outside Knot: " + source);
        }
        Objects.requireNonNull(srgBytes, "helper class bytes");
        if (srgBytes.length > MAX_CLASS_BYTES) {
            throw new IllegalArgumentException("Legacy helper class bytes exceed the 16 MiB limit: " + source);
        }
        synchronized (getClassLoadingLock(runtime)) {
            Class<?> existing = findLoadedClass(runtime);
            if (existing != null) {
                recordLoaded(source, runtime, srg, existing);
                return existing;
            }
            ClassReader reader;
            byte[] transformed;
            try {
                reader = new ClassReader(srgBytes);
                if (!reader.getClassName().equals(source.replace('.', '/'))) {
                    throw new IllegalArgumentException("Helper class bytes declare a different class: " + source);
                }
                ClassWriter output = new ClassWriter(0);
                reader.accept(new ClassRemapper(output, mappings.srgToRuntime()), 0);
                transformed = output.toByteArray();
            } catch (RuntimeException cause) {
                throw new IllegalArgumentException("Cannot convert legacy helper class bytes to the Knot namespace: " + source, cause);
            }
            try {
                Class<?> hostClass = launcher.loadIntoTarget(runtime);
                recordLoaded(source, runtime, srg, hostClass);
                return hostClass;
            } catch (ClassNotFoundException missing) {
                Class<?> defined = defineClass(runtime, transformed, 0, transformed.length);
                helperBytes.put(runtime, transformed);
                negativeResourceCache.remove(runtime);
                invalidClasses.remove(source);
                invalidClasses.remove(runtime);
                invalidClasses.remove(srg);
                recordLoaded(source, runtime, srg, defined);
                return defined;
            }
        }
    }

    private static boolean minecraftClass(String... names) {
        for (String name : names) if (name.startsWith("net.minecraft.")) return true;
        return false;
    }

    @Override
    public synchronized void addURL(URL url) {
        Path path = localPath(url);
        try {
            path = path.toRealPath();
            if (!Files.isDirectory(path) && !Files.isRegularFile(path)) {
                throw new IOException("Not a file or directory");
            }
            URL normalized = path.toUri().toURL();
            Path added = path;
            if (sources.stream().map(LaunchClassLoader::localPath).anyMatch(candidate -> sameFile(added, candidate))) return;
            boolean alreadyPresent = launcher.getClassPath().stream()
                .anyMatch(candidate -> sameFile(added, candidate));
            if (!alreadyPresent) launcher.addToClassPath(path);
            super.addURL(normalized);
            sources.add(normalized);
            resourceCache.clear();
            negativeResourceCache.clear();
            invalidClasses.clear();
        } catch (IOException cause) {
            throw new IllegalArgumentException("Cannot add local LaunchWrapper class path: " + path, cause);
        }
    }

    private static boolean sameFile(Path first, Path second) {
        Path candidate = second.toAbsolutePath().normalize();
        if (first.equals(candidate)) return true;
        try { return Files.isSameFile(first, candidate); }
        catch (IOException | SecurityException | UnsupportedOperationException ignored) { return false; }
    }

    private static Path localPath(URL url) {
        Objects.requireNonNull(url, "url");
        try {
            if (url.getProtocol().equals("jar")) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                if (connection.getEntryName() != null && !connection.getEntryName().isEmpty()) {
                    throw new IllegalArgumentException("LaunchWrapper only supports an entire local JAR, not a JAR subdirectory");
                }
                url = connection.getJarFileURL();
            }
            if (!url.getProtocol().equals("file")) {
                throw new IllegalArgumentException("LaunchWrapper class paths must use local file or JAR-root URLs");
            }
            return Path.of(url.toURI()).toAbsolutePath().normalize();
        } catch (IOException | java.net.URISyntaxException cause) {
            throw new IllegalArgumentException("Invalid local LaunchWrapper class path URL", cause);
        }
    }

    public List<URL> getSources() { return List.copyOf(sources); }
    public List<IClassTransformer> getTransformers() { return List.copyOf(transformers); }
    public Set<String> getHandledTransformers() { return Set.copyOf(handledTransformers); }
    public static Set<String> getHostHandledTransformers() { return HOST_HANDLED; }
    public IClassNameTransformer getNameTransformer() { return renameTransformer; }

    public synchronized void registerTransformer(String transformerClassName) {
        String name = binary(transformerClassName);
        if (HOST_HANDLED.contains(name)) {
            handledTransformers.add(name);
            return;
        }
        try {
            Class<?> type = loadClass(name);
            if (!IClassTransformer.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Legacy transformer does not implement IClassTransformer: " + name);
            }
            IClassTransformer transformer = (IClassTransformer) type.getDeclaredConstructor().newInstance();
            transformers.add(transformer);
            if (renameTransformer == null && transformer instanceof IClassNameTransformer rename) renameTransformer = rename;
        } catch (ReflectiveOperationException | LinkageError cause) {
            throw new IllegalStateException("Cannot register legacy class transformer: " + name, cause);
        }
    }

    public void addClassLoaderExclusion(String toExclude) { classLoaderExceptions.add(prefix(toExclude)); }
    public void addTransformerExclusion(String toExclude) { transformerExceptions.add(prefix(toExclude)); }

    public boolean isTransformationExcluded(String className) {
        String name = binary(className);
        String runtime = mappings.classFromSrg(name.replace('.', '/')).replace('/', '.');
        String srg = mappings.classToSrg(runtime.replace('.', '/')).replace('/', '.');
        return excluded(classLoaderExceptions, name) || excluded(classLoaderExceptions, runtime)
            || excluded(classLoaderExceptions, srg) || excluded(transformerExceptions, name)
            || excluded(transformerExceptions, runtime) || excluded(transformerExceptions, srg);
    }

    private static boolean excluded(Set<String> exclusions, String name) {
        return exclusions.stream().anyMatch(name::startsWith);
    }

    private static String prefix(String value) {
        Objects.requireNonNull(value, "exclusion prefix");
        if (value.isBlank() || value.indexOf('*') >= 0) {
            throw new IllegalArgumentException("LaunchWrapper exclusions require a nonempty class or package prefix");
        }
        return value.replace('/', '.');
    }

    private static String binary(String value) {
        Objects.requireNonNull(value, "class name");
        if (value.isBlank() || value.startsWith("[") || value.endsWith(".class")) {
            throw new IllegalArgumentException("Expected a binary or internal class name: " + value);
        }
        return value.replace('/', '.');
    }

    /** Returns untransformed SRG-named bytes; never invokes the guest transformer pipeline. */
    public byte[] getClassBytes(String name) throws IOException {
        String source = binary(name);
        IClassNameTransformer rename = renameTransformer;
        if (rename != null && !excluded(classLoaderExceptions, source)) source = binary(rename.unmapClassName(source));
        String runtime = mappings.classFromSrg(source.replace('.', '/')).replace('/', '.');
        if (negativeResourceCache.contains(runtime) && !helperBytes.containsKey(runtime)) return null;
        byte[] cached = resourceCache.get(runtime);
        if (cached != null) return cached.clone();
        byte[] raw = helperBytes.get(runtime);
        if (raw == null) {
            try {
                raw = launcher.getClassByteArray(runtime, false);
            } catch (IllegalStateException cause) {
                throw new IOException("Raw class bytes are unavailable before Knot unlocks its class path: " + runtime, cause);
            }
        }
        if (raw == null) {
            negativeResourceCache.add(runtime);
            return null;
        }
        if (raw.length > MAX_CLASS_BYTES) throw new IOException("Legacy class bytes exceed the 16 MiB limit: " + runtime);
        try {
            ClassReader reader = new ClassReader(raw);
            if (!reader.getClassName().equals(runtime.replace('.', '/'))) {
                throw new IOException("Knot returned class bytes for a different class: " + runtime);
            }
            ClassWriter output = new ClassWriter(0);
            reader.accept(new ClassRemapper(output, mappings.runtimeToSrg()), 0);
            byte[] bytes = output.toByteArray();
            resourceCache.put(runtime, bytes);
            return bytes.clone();
        } catch (RuntimeException cause) {
            throw new IOException("Cannot convert raw Knot class bytes to the legacy SRG namespace: " + runtime, cause);
        }
    }

    public void clearNegativeEntries(Set<String> entriesToClear) {
        Set<String> runtime = new HashSet<>();
        for (String entry : entriesToClear) {
            String name = binary(entry);
            String mapped = mappings.classFromSrg(name.replace('.', '/')).replace('/', '.');
            runtime.add(name);
            runtime.add(mapped);
            runtime.add(mappings.classToSrg(mapped.replace('.', '/')).replace('/', '.'));
            IClassNameTransformer rename = renameTransformer;
            if (rename != null) runtime.add(mappings.classFromSrg(rename.unmapClassName(name).replace('.', '/')).replace('/', '.'));
        }
        negativeResourceCache.removeAll(runtime);
        invalidClasses.removeAll(runtime);
    }

    private String resourceName(String name) {
        if (!name.endsWith(".class")) return name;
        String internal = name.substring(0, name.length() - 6);
        return mappings.classFromSrg(internal).concat(".class");
    }

    @Override public URL getResource(String name) { return getParent().getResource(resourceName(name)); }
    @Override public InputStream getResourceAsStream(String name) {
        String resource = resourceName(name);
        if (resource.endsWith(".class")) {
            byte[] helper = helperBytes.get(resource.substring(0, resource.length() - 6).replace('/', '.'));
            if (helper != null) return new ByteArrayInputStream(helper.clone());
        }
        return getParent().getResourceAsStream(resource);
    }
    @Override public URL findResource(String name) { return getResource(name); }
    @Override public Enumeration<URL> getResources(String name) throws IOException {
        return getParent().getResources(resourceName(name));
    }
    @Override public Enumeration<URL> findResources(String name) throws IOException { return getResources(name); }
}
