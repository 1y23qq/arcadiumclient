package io.github.arcadiumclient.forgecompat.transform;

import com.google.gson.JsonObject;
import io.github.arcadiumclient.forgecompat.HostClassLoaderCompatibility;
import io.github.arcadiumclient.forgecompat.JourneyMapResourcesCompatibility;
import io.github.arcadiumclient.forgecompat.HostRecipeOverlayCompatibility;
import io.github.arcadiumclient.forgecompat.LegacyNettyAttributeKey;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ForgeJarTransformer {
    private static final long MAX_JAR_BYTES = 256L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 50_000;
    private static final String REFLECTION = "io/github/arcadiumclient/forgecompat/reflect/ForgeReflection";
    private static final String JAVA_VERSION = "io/github/arcadiumclient/forgecompat/LegacyJavaVersion";
    private static final String LOG4J = "io/github/arcadiumclient/forgecompat/LegacyLog4j";
    private static final String CLASS_PATH = "io/github/arcadiumclient/forgecompat/HostClassPathCompatibility";
    private static final String KEYBOARD = "io/github/arcadiumclient/forgecompat/LegacyKeyboard";
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private record ClassEntry(String prefix, ClassNode node) {}
    private record Contents(Map<String, byte[]> entries, Map<String, ClassEntry> classes, String sha256) {}

    public void index(Path input, ForgeMappings mappings, boolean officialInput) throws IOException {
        Contents contents = read(input);
        index(contents, mappings, officialInput);
    }

    public Path transform(Path input, Path cacheDir, ForgeMappings mappings, boolean officialInput,
                          Consumer<ClassNode> afterRemap) throws IOException {
        Contents contents = read(input);
        index(contents, mappings, officialInput);
        String key = textHash("forge-jar-v1\0" + contents.sha256 + "\0" + mappings.runtimeNamespace() + "\0"
            + mappings.identity() + "\0" + officialInput + "\0" + engineIdentity());
        Path directory = cacheDir.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path base = directory.resolve(key + ".jar");
        synchronized (LOCKS.computeIfAbsent(base, ignored -> new Object())) {
            Path lock = directory.resolve(key + ".lock");
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var held = channel.lock()) {
                if (!verified(base, key)) {
                    Map<String, byte[]> changed = remap(contents, mappings, officialInput);
                    install(changed, base, key);
                }
                if (afterRemap == null) return base;
                Contents cached = read(base);
                Map<String, byte[]> patched = new TreeMap<>(cached.entries);
                cached.classes.keySet().forEach(patched::remove);
                for (var entry : cached.classes.entrySet()) {
                    ClassEntry source = entry.getValue();
                    ClassNode node = source.node;
                    try { afterRemap.accept(node); }
                    catch (RuntimeException exception) { throw new IOException("Trusted compatibility patch failed: " + node.name, exception); }
                    String path = source.prefix + node.name + ".class";
                    if (patched.putIfAbsent(path, serialize(node)) != null) throw new IOException("Compatibility patch creates duplicate class: " + path);
                }
                Path temporary = writeTemporary(patched, directory);
                try {
                    String content = digest(temporary);
                    Path destination = directory.resolve(key + "-" + content + ".jar");
                    if (!verified(destination, key + ":" + content)) commit(temporary, destination, key + ":" + content, content);
                    return destination;
                } finally { Files.deleteIfExists(temporary); }
            }
        }
    }

    private static void index(Contents contents, ForgeMappings mappings, boolean official) {
        for (ClassEntry entry : contents.classes.values()) {
            ClassNode node = entry.node;
            if (official) {
                mappings.indexOfficialHierarchy(node.name, node.superName, node.interfaces);
                for (var method : node.methods) mappings.indexOfficialMethod(node.name, method.name, method.desc);
                for (var field : node.fields) mappings.indexOfficialField(node.name, field.name, field.desc);
            }
            else {
                mappings.indexSrgHierarchy(node.name, node.superName, node.interfaces);
                MixinReferences.index(node, mappings);
            }
        }
    }

    private static Map<String, byte[]> remap(Contents contents, ForgeMappings mappings, boolean official) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        List<ClassNode> nodes = contents.classes.values().stream().map(ClassEntry::node).toList();
        MixinReferences mixins = official ? null : new MixinReferences(contents.entries, nodes, mappings);
        Remapper source = official ? mappings.officialToRuntime() : mappings.srgToRuntime();
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                return switch (name) {
                    case "org/apache/logging/log4j/core/helpers/Strings" -> "org/apache/logging/log4j/util/Strings";
                    case "org/apache/logging/log4j/core/helpers/Integers" -> "org/apache/logging/log4j/core/util/Integers";
                    case "org/apache/logging/log4j/core/helpers/Booleans" -> "org/apache/logging/log4j/core/util/Booleans";
                    case "sun/reflect/generics/reflectiveObjects/NotImplementedException" -> "java/lang/UnsupportedOperationException";
                    default -> source.map(name);
                };
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                String override = mixins == null ? null : mixins.memberName(owner, name, descriptor, false);
                return override == null ? source.mapFieldName(owner, name, descriptor) : override;
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                String override = mixins == null ? null : mixins.memberName(owner, name, descriptor, true);
                return override == null ? source.mapMethodName(owner, name, descriptor) : override;
            }
            @Override public String mapInvokeDynamicMethodName(String name, String descriptor) {
                return source.mapInvokeDynamicMethodName(name, descriptor);
            }
        };
        for (var entry : contents.entries.entrySet()) {
            String name = entry.getKey();
            if (signature(name) || delegatedClass(name)) continue;
            ClassEntry classEntry = contents.classes.get(name);
            if (classEntry != null) {
                ClassNode mapped = new ClassNode();
                try {
                    classEntry.node.accept(new ClassRemapper(mapped, remapper));
                    if (mixins != null) mixins.apply(classEntry.node, mapped);
                    rewriteReflection(mapped);
                    rewriteJavaVersionCalls(mapped);
                    rewriteLog4jFactories(mapped);
                    rewriteClassPathCalls(mapped);
                    rewriteKeyboardCalls(mapped);
                    LegacyNettyAttributeKey.normalize(mapped);
                    if (!official) {
                        HostClassLoaderCompatibility.normalize(mapped);
                        JourneyMapResourcesCompatibility.normalize(mapped);
                        HostRecipeOverlayCompatibility.normalize(mapped, mappings);
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("Cannot remap class: " + name, exception);
                }
                String target = classEntry.prefix + mapped.name + ".class";
                if (result.putIfAbsent(target, serialize(mapped)) != null) throw new IOException("Duplicate remapped class: " + target);
            } else if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                if (result.putIfAbsent("META-INF/MANIFEST.MF", manifest(entry.getValue())) != null) throw new IOException("Multiple manifests in JAR");
            } else if (name.startsWith("META-INF/services/")) {
                byte[] service = service(entry.getValue());
                if (service.length > 0) result.put(name, service);
            } else if (result.putIfAbsent(name, entry.getValue()) != null) throw new IOException("Duplicate remapped resource: " + name);
        }
        if (mixins != null) {
            result.putAll(mixins.rewrittenRefmaps());
            result.putAll(mixins.rewrittenConfigurations());
        }
        return result;
    }

    private static void rewriteReflection(ClassNode node) {
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("java/lang/Class")) continue;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals("forName")
                    && (call.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")
                    || call.desc.equals("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"))) {
                    call.owner = REFLECTION;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && (call.name.equals("getDeclaredField") || call.name.equals("getField"))
                    && call.desc.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
                    call.owner = REFLECTION;
                    call.desc = "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;";
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && (call.name.equals("getDeclaredMethod") || call.name.equals("getMethod"))
                    && call.desc.equals("(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")) {
                    call.owner = REFLECTION;
                    call.desc = "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;";
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                }
            }
        }
    }

    private static void rewriteKeyboardCalls(ClassNode node) {
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                    && call.owner.equals("org/lwjgl/input/Keyboard")
                    && (call.name.equals("isKeyDown") && call.desc.equals("(I)Z")
                        || call.name.equals("getKeyName") && call.desc.equals("(I)Ljava/lang/String;"))) {
                call.owner = KEYBOARD;
            }
        }
    }

    private static void rewriteJavaVersionCalls(ClassNode node) {
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                    && call.owner.equals("org/apache/commons/lang3/SystemUtils") && call.name.equals("isJavaVersionAtLeast")
                    && call.desc.equals("(Lorg/apache/commons/lang3/JavaVersion;)Z")) {
                call.owner = JAVA_VERSION;
                call.desc = "(Ljava/lang/Enum;)Z";
            }
        }
    }

    private static void rewriteLog4jFactories(ClassNode node) {
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || call.itf) continue;
            if (call.owner.equals("org/apache/logging/log4j/core/layout/PatternLayout") && call.name.equals("createLayout")
                && call.desc.equals("(Ljava/lang/String;Lorg/apache/logging/log4j/core/config/Configuration;Lorg/apache/logging/log4j/core/pattern/RegexReplacement;Ljava/lang/String;Ljava/lang/String;)Lorg/apache/logging/log4j/core/layout/PatternLayout;")
                || call.owner.equals("org/apache/logging/log4j/core/appender/RandomAccessFileAppender") && call.name.equals("createAppender")
                && call.desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/apache/logging/log4j/core/Layout;Lorg/apache/logging/log4j/core/Filter;Ljava/lang/String;Ljava/lang/String;Lorg/apache/logging/log4j/core/config/Configuration;)Lorg/apache/logging/log4j/core/appender/RandomAccessFileAppender;")) {
                call.owner = LOG4J;
            }
        }
    }

    private static void rewriteClassPathCalls(ClassNode node) {
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                && call.owner.equals("com/google/common/reflect/ClassPath") && call.name.equals("from")
                && call.desc.equals("(Ljava/lang/ClassLoader;)Lcom/google/common/reflect/ClassPath;")) call.owner = CLASS_PATH;
        }
    }

    private static Contents read(Path input) throws IOException {
        if (!Files.isRegularFile(input) || Files.size(input) > MAX_JAR_BYTES) throw new IOException("Invalid compatibility JAR size");
        String inputHash = digest(input);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Map<String, ClassEntry> classes = new LinkedHashMap<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(input.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                String name = entry.getName();
                validateName(name, entry.isDirectory());
                if (entries.containsKey(name) || entries.size() >= MAX_ENTRIES) throw new IOException("Duplicate or excessive JAR entries: " + name);
                int limit = name.endsWith(".class") ? MAX_CLASS_BYTES : MAX_ENTRY_BYTES;
                if (entry.getSize() > limit) throw new IOException("Oversized JAR entry: " + name);
                byte[] data;
                try (InputStream stream = zip.getInputStream(entry)) { data = bounded(stream, limit); }
                total += data.length;
                if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("JAR expands beyond compatibility limit");
                entries.put(name, data);
                if (name.endsWith(".class") && !delegatedClass(name)) {
                    try {
                        ClassNode node = new ClassNode();
                        new ClassReader(data).accept(node, 0);
                        String prefix = classPrefix(name);
                        if (!name.equals(prefix + node.name + ".class")) throw new IOException("Class entry has a different internal name: " + name);
                        classes.put(name, new ClassEntry(prefix, node));
                    } catch (RuntimeException exception) { throw new IOException("Invalid class entry: " + name, exception); }
                }
            }
        }
        if (!inputHash.equals(digest(input))) throw new IOException("Compatibility JAR changed while being read");
        return new Contents(entries, classes, inputHash);
    }

    private static byte[] bounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if ((long) output.size() + read > maximum) throw new IOException("JAR entry expands beyond compatibility limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateName(String name, boolean directory) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
            || name.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe JAR entry name");
        String[] segments = name.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].equals(".") || segments[i].equals("..")
                || segments[i].isEmpty() && !(directory && i == segments.length - 1)) throw new IOException("Unsafe JAR entry path: " + name);
        }
    }

    private static String classPrefix(String name) {
        if (!name.startsWith("META-INF/versions/")) return "";
        int end = name.indexOf('/', "META-INF/versions/".length());
        return end < 0 ? "" : name.substring(0, end + 1);
    }

    private static boolean delegatedClass(String name) {
        String value = name.substring(classPrefix(name).length());
        return value.startsWith("org/spongepowered/asm/") || value.startsWith("org/spongepowered/tools/")
            || value.startsWith("org/objectweb/asm/");
    }

    private static boolean signature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) return false;
        String tail = upper.substring("META-INF/".length());
        return tail.indexOf('/') < 0 && (tail.startsWith("SIG-") || tail.endsWith(".SF") || tail.endsWith(".RSA")
            || tail.endsWith(".DSA") || tail.endsWith(".EC"));
    }

    private static byte[] manifest(byte[] input) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(input));
        Attributes main = manifest.getMainAttributes();
        if ("org.spongepowered.asm.launch.MixinTweaker".equals(main.getValue("TweakClass"))) main.remove(new Attributes.Name("TweakClass"));
        main.remove(Attributes.Name.SIGNATURE_VERSION);
        removeDigests(main);
        for (Attributes section : manifest.getEntries().values()) removeDigests(section);
        manifest.getEntries().entrySet().removeIf(entry -> entry.getValue().isEmpty());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        manifest.write(output);
        return output.toByteArray();
    }

    private static void removeDigests(Attributes attributes) {
        attributes.keySet().removeIf(key -> key.toString().toLowerCase(Locale.ROOT)
            .matches("[a-z0-9-]+-digest(?:-manifest(?:-main-attributes)?)?"));
    }

    private static byte[] service(byte[] original) {
        StringBuilder output = new StringBuilder();
        for (String line : new String(original, StandardCharsets.UTF_8).split("\\R")) {
            String provider = line.split("#", 2)[0].trim();
            if (provider.startsWith("org.spongepowered.asm.") || provider.startsWith("org.spongepowered.tools.")
                || provider.startsWith("org.objectweb.asm.")) continue;
            if (!line.isBlank()) output.append(line).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] serialize(ClassNode node) throws IOException {
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] bytes = writer.toByteArray();
            if (bytes.length > MAX_CLASS_BYTES) throw new IOException("Transformed class exceeds compatibility limit");
            return bytes;
        } catch (RuntimeException exception) { throw new IOException("Cannot serialize compatibility class: " + node.name, exception); }
    }

    private static void install(Map<String, byte[]> entries, Path destination, String key) throws IOException {
        Path temporary = writeTemporary(entries, destination.getParent());
        try { commit(temporary, destination, key, digest(temporary)); }
        finally { Files.deleteIfExists(temporary); }
    }

    private static Path writeTemporary(Map<String, byte[]> entries, Path directory) throws IOException {
        if (entries.size() > MAX_ENTRIES) throw new IOException("Too many transformed JAR entries");
        Path temporary = Files.createTempFile(directory, ".forge-remap-", ".tmp");
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for (var entry : entries.entrySet().stream().sorted(Comparator.comparing((Map.Entry<String, byte[]> entry) ->
                !entry.getKey().equals("META-INF/MANIFEST.MF")).thenComparing(Map.Entry::getKey)).toList()) {
                validateName(entry.getKey(), entry.getKey().endsWith("/"));
                total += entry.getValue().length;
                int limit = entry.getKey().endsWith(".class") ? MAX_CLASS_BYTES : MAX_ENTRY_BYTES;
                if (entry.getValue().length > limit || total > MAX_UNCOMPRESSED_BYTES) throw new IOException("Transformed JAR exceeds the size limit");
                ZipEntry value = new ZipEntry(entry.getKey());
                value.setTime(0);
                zip.putNextEntry(value);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (Throwable failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        if (Files.size(temporary) > MAX_JAR_BYTES) {
            Files.deleteIfExists(temporary);
            throw new IOException("Transformed JAR exceeds the compressed size limit");
        }
        return temporary;
    }

    private static boolean verified(Path path, String key) throws IOException {
        Path sidecar = path.resolveSibling(path.getFileName() + ".json");
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(sidecar)) throw new IOException("Compatibility cache contains a symbolic link");
        if (!Files.isRegularFile(path) || !Files.isRegularFile(sidecar) || Files.size(sidecar) > 4096) return false;
        try {
            JsonObject metadata = MixinReferences.parseJson(Files.readAllBytes(sidecar), 4096).getAsJsonObject();
            return metadata.get("format").getAsInt() == 1 && metadata.get("key").getAsString().equals(key)
                && metadata.get("bytes").getAsLong() == Files.size(path)
                && metadata.get("sha256").getAsString().equals(digest(path));
        } catch (IOException | RuntimeException exception) { return false; }
    }

    private static void commit(Path temporary, Path destination, String key, String hash) throws IOException {
        if (Files.isSymbolicLink(destination)) throw new IOException("Compatibility cache contains a symbolic link");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("format", 1);
        metadata.addProperty("key", key);
        metadata.addProperty("sha256", hash);
        metadata.addProperty("bytes", Files.size(temporary));
        Path sidecar = destination.resolveSibling(destination.getFileName() + ".json");
        if (Files.isSymbolicLink(sidecar)) throw new IOException("Compatibility cache contains a symbolic link");
        Path temporaryMetadata = Files.createTempFile(destination.getParent(), ".forge-hash-", ".tmp");
        try {
            Files.writeString(temporaryMetadata, metadata.toString(), StandardCharsets.UTF_8);
            move(temporary, destination);
            move(temporaryMetadata, sidecar);
        } finally { Files.deleteIfExists(temporaryMetadata); }
    }

    private static void move(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String engineIdentity() throws IOException {
        MessageDigest hash = sha256();
        try {
            Path source = Path.of(ForgeJarTransformer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(source)) return digest(source);
            if (Files.isDirectory(source)) {
                try (var files = Files.walk(source)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                        hash.update(source.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                        try (InputStream input = Files.newInputStream(file)) { input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), hash)); }
                    }
                }
                return HexFormat.of().formatHex(hash.digest());
            }
            throw new IOException("Compatibility engine code cannot be fingerprinted");
        } catch (java.net.URISyntaxException exception) { throw new IOException("Invalid compatibility engine location", exception); }
    }

    private static String digest(Path path) throws IOException {
        MessageDigest hash = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) hash.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static String textHash(String text) {
        return HexFormat.of().formatHex(sha256().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }
}
