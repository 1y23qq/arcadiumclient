package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public final class ForgeModScanner {
    private static final String MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;";
    private static final long MAX_JAR_BYTES = 512L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;

    public record Mod(String id, String name, String version, String entrypoint,
                      String dependencies, Map<String, Object> descriptor) { }

    public record ModFile(Path path, String sha256, List<Mod> mods, String corePlugin, String tweakClass,
                          List<String> mixinConfigs, List<String> accessTransformers) { }

    public List<ModFile> discover(Path gameDirectory, Path activeModDirectory) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(gameDirectory.resolve("mods"));
        if (activeModDirectory != null) roots.add(activeModDirectory);
        for (Path root : roots) {
            collect(root, files);
            collect(root.resolve("1.8.9"), files);
        }
        List<ModFile> result = new ArrayList<>();
        for (Path file : files) {
            ModFile candidate = inspect(file, true);
            if (candidate != null) result.add(candidate);
        }
        if (result.stream().noneMatch(ForgeModScanner::hasEntrypoint)) return List.of();
        result.sort(Comparator.comparing(candidate -> candidate.path().toString()));
        return List.copyOf(result);
    }

    private static void collect(Path directory, Set<Path> files) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var entries = Files.list(directory)) {
            for (Path file : entries.sorted().toList()) {
                if (Files.isRegularFile(file)
                        && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    files.add(file.toRealPath());
                    if (files.size() > 4096) throw new IOException("Too many mod archives to inspect. Remove unused mods to continue.");
                }
            }
        }
    }

    public ModFile inspect(Path path) throws IOException {
        return inspect(path, false);
    }

    private static boolean hasEntrypoint(ModFile file) {
        return !file.mods().isEmpty() || !file.corePlugin().isBlank() || !file.tweakClass().isBlank()
                || !file.mixinConfigs().isEmpty();
    }

    private ModFile inspect(Path path, boolean includeLibraries) throws IOException {
        if (Files.size(path) > MAX_JAR_BYTES) throw invalid(path, "The mod archive is too large.");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            if (zip.getEntry("fabric.mod.json") != null) return null;
            List<String> classes = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            long expanded = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name) || names.size() > 100_000 || name.startsWith("/")
                        || name.contains("\\") || name.indexOf('\0') >= 0
                        || List.of(name.split("/")).contains("..")) {
                    throw invalid(path, "The mod archive contains invalid or duplicate entries.");
                }
                if (!entry.isDirectory()) {
                    if (entry.getSize() < 0 || entry.getSize() > MAX_EXPANDED_BYTES
                            || (expanded += entry.getSize()) > MAX_EXPANDED_BYTES) {
                        throw invalid(path, "The mod archive exceeds the extraction limit.");
                    }
                    if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) classes.add(name);
                }
            }
            Manifest manifest = new Manifest();
            ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry != null) manifest.read(new java.io.ByteArrayInputStream(read(zip, manifestEntry)));
            String corePlugin = value(manifest, "FMLCorePlugin");
            String tweakClass = value(manifest, "TweakClass");
            List<String> mixins = new ArrayList<>(commaList(value(manifest, "MixinConfigs")));
            List<String> transformers = new ArrayList<>(spaceList(value(manifest, "FMLAT")));
            if (transformers.isEmpty() && zip.getEntry("META-INF/accesstransformer.cfg") != null) {
                transformers.add("accesstransformer.cfg");
            }
            Map<String, JsonObject> metadata = metadata(zip);
            List<Mod> mods = new ArrayList<>();
            for (String name : classes) {
                byte[] bytes = read(zip, zip.getEntry(name));
                Map<String, Object> descriptor = new LinkedHashMap<>();
                String[] entrypoint = new String[1];
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(int version, int access, String className, String signature,
                                      String superName, String[] interfaces) {
                        entrypoint[0] = className.replace('/', '.');
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String type, boolean visible) {
                        if (!MOD_ANNOTATION.equals(type)) return null;
                        descriptor.put("_present", Boolean.TRUE);
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String key, Object value) { descriptor.put(key, value); }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (descriptor.remove("_present") == null) continue;
                String id = string(descriptor, "modid", "");
                if (id.isBlank()) throw invalid(path, "A Forge mod is missing its mod ID.");
                JsonObject info = metadata.get(id);
                String modName = string(descriptor, "name", id);
                String modVersion = string(descriptor, "version", "");
                String dependencies = string(descriptor, "dependencies", "");
                if (info != null) {
                    if (modName.isBlank() || modName.equals(id)) modName = jsonString(info, "name", id);
                    if (modVersion.isBlank() || modVersion.contains("${")) modVersion = jsonString(info, "version", "");
                    if (info.has("useDependencyInformation") && info.get("useDependencyInformation").getAsBoolean()) {
                        dependencies = metadataDependencies(info);
                    }
                }
                descriptor.put("modid", id);
                descriptor.put("name", modName);
                descriptor.put("version", modVersion);
                descriptor.put("dependencies", dependencies);
                mods.add(new Mod(id, modName, modVersion, entrypoint[0], dependencies, Map.copyOf(descriptor)));
            }
            for (String name : names) {
                if (name.endsWith(".json") && !name.contains("/") && name.toLowerCase(Locale.ROOT).contains("mixin")
                        && !name.toLowerCase(Locale.ROOT).contains("refmap")) {
                    JsonElement json = new JsonParser().parse(new String(read(zip, zip.getEntry(name)), StandardCharsets.UTF_8));
                    if (json.isJsonObject() && json.getAsJsonObject().has("package")
                            && (json.getAsJsonObject().has("mixins") || json.getAsJsonObject().has("client"))) {
                        if (!mixins.contains(name)) mixins.add(name);
                    }
                }
            }
            if (mods.isEmpty() && corePlugin.isBlank() && tweakClass.isBlank() && mixins.isEmpty()
                    && (!includeLibraries || classes.isEmpty())) return null;
            return new ModFile(path.toRealPath(), sha256(path), List.copyOf(mods), corePlugin, tweakClass,
                    List.copyOf(mixins), List.copyOf(transformers));
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            throw invalid(path, "The mod archive contains invalid metadata.", e);
        }
    }

    static byte[] read(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry == null || entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("Mod entry exceeds the read limit.");
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("Mod entry exceeds the read limit.");
            return bytes;
        }
    }

    private static Map<String, JsonObject> metadata(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("mcmod.info");
        if (entry == null) return Map.of();
        JsonElement document = new JsonParser().parse(new String(read(zip, entry), StandardCharsets.UTF_8));
        JsonArray mods = document.isJsonArray() ? document.getAsJsonArray()
                : document.getAsJsonObject().getAsJsonArray("modList");
        if (mods == null) return Map.of();
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement mod : mods) {
            if (mod.isJsonObject() && mod.getAsJsonObject().has("modid")) {
                JsonObject info = mod.getAsJsonObject();
                result.put(info.get("modid").getAsString(), info);
            }
        }
        return result;
    }

    private static String metadataDependencies(JsonObject metadata) {
        Set<String> dependencies = new LinkedHashSet<>();
        for (String key : List.of("requiredMods", "dependencies")) {
            if (!metadata.has(key) || !metadata.get(key).isJsonArray()) continue;
            for (JsonElement value : metadata.getAsJsonArray(key)) dependencies.add("required-after:" + value.getAsString());
        }
        return String.join(";", dependencies);
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static String string(Map<String, Object> values, String key, String fallback) {
        return values.get(key) instanceof String value ? value : fallback;
    }

    private static String value(Manifest manifest, String key) {
        String value = manifest.getMainAttributes().getValue(key);
        return value == null ? "" : value.trim();
    }

    private static List<String> commaList(String value) {
        return value.isBlank() ? List.of() : List.of(value.split("\\s*,\\s*"));
    }

    private static List<String> spaceList(String value) {
        return value.isBlank() ? List.of() : List.of(value.split("\\s+"));
    }

    static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IOException invalid(Path file, String message) { return invalid(file, message, null); }

    private static IOException invalid(Path file, String message, Throwable cause) {
        return new IOException("Cannot load " + file.getFileName() + ": " + message, cause);
    }
}
