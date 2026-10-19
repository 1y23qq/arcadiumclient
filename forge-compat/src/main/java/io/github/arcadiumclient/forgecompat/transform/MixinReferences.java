package io.github.arcadiumclient.forgecompat.transform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

final class MixinReferences {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String PREFIX = "Lorg/spongepowered/asm/mixin/";
    record MemberKey(String owner, String name, String descriptor, boolean method) {}
    private record Context(List<String> targets, boolean remap) {}

    private final ForgeMappings mappings;
    private final Map<String, Context> contexts = new HashMap<>();
    private final Map<String, Map<String, String>> references = new HashMap<>();
    private final Map<String, JsonObject> refmaps = new LinkedHashMap<>();
    private final Map<String, JsonObject> configurations = new LinkedHashMap<>();
    private final Map<String, Boolean> defaultPriorities = new HashMap<>();
    private final Map<MemberKey, String> memberNames = new HashMap<>();

    MixinReferences(Map<String, byte[]> resources, Collection<ClassNode> classes, ForgeMappings mappings) throws IOException {
        this.mappings = mappings;
        for (ClassNode node : classes) {
            AnnotationNode mixin = find(annotations(node.visibleAnnotations, node.invisibleAnnotations), MIXIN);
            if (mixin != null) contexts.put(node.name, new Context(targets(mixin), enabled(mixin, true)));
        }
        Set<String> files = new LinkedHashSet<>();
        Set<String> declared = declaredConfigurations(resources);
        for (var entry : resources.entrySet()) {
            if (entry.getKey().endsWith(".refmap.json")) files.add(entry.getKey());
            if (!entry.getKey().endsWith(".json") && !declared.contains(entry.getKey())) continue;
            try {
                JsonElement parsed = parseJson(entry.getValue(), 4 * 1024 * 1024);
                if (parsed.isJsonObject()) {
                    JsonObject object = parsed.getAsJsonObject();
                    if (declared.contains(entry.getKey()) || isConfiguration(object)) {
                        configurations.put(entry.getKey(), object);
                        if (object.has("refmap")) files.add(object.get("refmap").getAsString());
                    }
                } else if (declared.contains(entry.getKey())) throw new IOException("Mixin configuration must be an object");
            } catch (IOException | RuntimeException ignored) {
                if (entry.getKey().endsWith(".refmap.json")) throw new IOException("Invalid Mixin refmap: " + entry.getKey(), ignored);
                if (declared.contains(entry.getKey())) throw new IOException("Invalid Mixin configuration: " + entry.getKey(), ignored);
            }
        }
        for (JsonObject config : configurations.values()) {
            String prefix = config.has("package") ? config.get("package").getAsString().replace('.', '/') : "";
            if (!prefix.isEmpty() && !prefix.endsWith("/")) prefix += "/";
            boolean defaults = !config.has("mixinPriority") && !config.has("parent");
            for (String section : List.of("mixins", "client", "server")) {
                if (!config.has(section)) continue;
                for (JsonElement entry : config.getAsJsonArray(section)) {
                    defaultPriorities.merge(prefix + entry.getAsString().replace('.', '/'), defaults, (a, b) -> a && b);
                }
            }
        }
        for (String file : files) {
            byte[] bytes = resources.get(file);
            if (bytes == null) throw new IOException("Missing Mixin refmap: " + file);
            try {
                JsonObject root = parseJson(bytes, 4 * 1024 * 1024).getAsJsonObject();
                JsonObject canonical = canonical(root);
                for (var classEntry : canonical.entrySet()) {
                    String name = classEntry.getKey().replace('.', '/');
                    Map<String, String> map = references.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
                    for (var reference : classEntry.getValue().getAsJsonObject().entrySet()) {
                        String value = reference.getValue().getAsString();
                        String previous = map.putIfAbsent(reference.getKey(), value);
                        if (previous != null && !previous.equals(value)) throw new IOException("Conflicting Mixin refmap entry: " + name);
                    }
                }
                refmaps.put(file, root);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid Mixin refmap: " + file, exception);
            }
        }
        for (ClassNode node : classes) prepareMemberNames(node);
    }

    static void index(ClassNode node, ForgeMappings mappings) {
        AnnotationNode mixin = find(annotations(node.visibleAnnotations, node.invisibleAnnotations), MIXIN);
        if (mixin != null && !targets(mixin).isEmpty()) mappings.indexSrgMixinTargets(node.name, targets(mixin));
    }

    String memberName(String owner, String name, String descriptor, boolean method) {
        return memberNames.get(new MemberKey(owner, name, descriptor, method));
    }

    private void prepareMemberNames(ClassNode node) throws IOException {
        Context context = contexts.get(node.name);
        if (context == null) return;
        for (FieldNode field : node.fields) {
            AnnotationNode shadow = find(annotations(field.visibleAnnotations, field.invisibleAnnotations), PREFIX + "Shadow;");
            if (shadow != null) memberNames.put(new MemberKey(node.name, field.name, field.desc, false),
                enabled(shadow, context.remap) ? mappedName(node.name, field.name, field.desc, false) : field.name);
        }
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (!annotation.desc.equals(PREFIX + "Shadow;") && !annotation.desc.equals(PREFIX + "Overwrite;")) continue;
                String name = method.name;
                if (enabled(annotation, context.remap)) {
                    String prefix = string(annotation, "prefix", "shadow$");
                    boolean prefixed = annotation.desc.endsWith("Shadow;") && name.startsWith(prefix);
                    name = (prefixed ? prefix : "") + mappedName(node.name,
                        prefixed ? name.substring(prefix.length()) : name, method.desc, true);
                }
                memberNames.put(new MemberKey(node.name, method.name, method.desc, true), name);
            }
        }
    }

    void apply(ClassNode original, ClassNode mapped) throws IOException {
        Context context = contexts.get(original.name);
        if (context == null) return;
        List<AnnotationNode> sourceAnnotations = annotations(original.visibleAnnotations, original.invisibleAnnotations);
        List<AnnotationNode> mappedAnnotations = annotations(mapped.visibleAnnotations, mapped.invisibleAnnotations);
        AnnotationNode sourceMixin = find(sourceAnnotations, MIXIN);
        AnnotationNode mappedMixin = find(mappedAnnotations, MIXIN);
        if (sourceMixin != null && mappedMixin != null) {
            if (Boolean.TRUE.equals(defaultPriorities.get(original.name)) && value(sourceMixin, "priority") == null
                    && context.targets.stream().anyMatch(target -> target.replace('.', '/').startsWith("net/minecraft/"))
                    && original.methods.stream().anyMatch(method -> find(annotations(method.visibleAnnotations, method.invisibleAnnotations),
                        PREFIX + "Overwrite;") != null)) {
                // Equal-priority FIELD injectors reject overwritten methods; preserve native callbacks after the guest body is merged.
                put(mappedMixin, "priority", 999);
            }
            if (!context.remap) {
                copyValue(sourceMixin, mappedMixin, "value");
                copyValue(sourceMixin, mappedMixin, "targets");
            } else {
                Object strings = value(sourceMixin, "targets");
                if (strings instanceof List<?> list) {
                    List<String> targets = new ArrayList<>();
                    for (Object target : list) targets.add(mappings.reflectionClassFromSrg((String) target));
                    put(mappedMixin, "targets", targets);
                }
            }
        }
        for (int i = 0; i < original.fields.size(); i++) {
            FieldNode source = original.fields.get(i), output = mapped.fields.get(i);
            applyAnnotations(original.name, source.name, source.desc, false, context.remap,
                annotations(source.visibleAnnotations, source.invisibleAnnotations),
                annotations(output.visibleAnnotations, output.invisibleAnnotations));
        }
        for (int i = 0; i < original.methods.size(); i++) {
            MethodNode source = original.methods.get(i), output = mapped.methods.get(i);
            applyAnnotations(original.name, source.name, source.desc, true, context.remap,
                annotations(source.visibleAnnotations, source.invisibleAnnotations),
                annotations(output.visibleAnnotations, output.invisibleAnnotations));
        }
    }

    private void applyAnnotations(String owner, String memberName, String descriptor, boolean method, boolean parentRemap,
                                  List<AnnotationNode> originals, List<AnnotationNode> mapped) throws IOException {
        for (int i = 0; i < originals.size(); i++) {
            AnnotationNode source = originals.get(i), output = mapped.get(i);
            if (!source.desc.startsWith(PREFIX)) continue;
            boolean remap = enabled(source, parentRemap);
            if (!remap) {
                copyValue(source, output, "value");
                copyValue(source, output, "method");
                copyValue(source, output, "aliases");
                copyValue(source, output, "at");
                copyValue(source, output, "slice");
                continue;
            }
            if (source.desc.equals(PREFIX + "gen/Accessor;") || source.desc.equals(PREFIX + "gen/Invoker;")) {
                boolean invoker = source.desc.endsWith("Invoker;");
                String target = string(source, "value", "");
                if (target.isEmpty()) target = derivedTarget(memberName, invoker);
                String targetDescriptor;
                if (invoker) targetDescriptor = descriptor;
                else {
                    Type returnType = Type.getReturnType(descriptor);
                    Type[] arguments = Type.getArgumentTypes(descriptor);
                    targetDescriptor = returnType.getSort() == Type.VOID && arguments.length == 1
                        ? arguments[0].getDescriptor() : returnType.getDescriptor();
                }
                put(output, "value", mappedName(owner, target, targetDescriptor, invoker));
            }
            Object aliases = value(source, "aliases");
            if (aliases instanceof List<?> list) {
                List<String> changed = new ArrayList<>();
                for (Object alias : list) changed.add(mappedName(owner, (String) alias, descriptor, method));
                put(output, "aliases", changed);
            }
            Object selectors = value(source, "method");
            if (selectors instanceof List<?> list) {
                List<String> changed = new ArrayList<>();
                for (Object selector : list) changed.add(selector(owner, (String) selector));
                put(output, "method", changed);
            } else if (selectors instanceof String text) put(output, "method", selector(owner, text));
            rewriteNested(owner, source, output, remap);
        }
    }

    private void rewriteNested(String owner, AnnotationNode source, AnnotationNode output, boolean parentRemap) throws IOException {
        if (source.values == null) return;
        for (int i = 0; i < source.values.size(); i += 2) {
            String key = (String) source.values.get(i);
            Object original = source.values.get(i + 1), mapped = value(output, key);
            if (original instanceof AnnotationNode annotation && mapped instanceof AnnotationNode mappedAnnotation) {
                rewriteAt(owner, annotation, mappedAnnotation, parentRemap);
            } else if (original instanceof List<?> list && mapped instanceof List<?> mappedList) {
                for (int j = 0; j < list.size(); j++) {
                    if (list.get(j) instanceof AnnotationNode annotation && mappedList.get(j) instanceof AnnotationNode mappedAnnotation) {
                        rewriteAt(owner, annotation, mappedAnnotation, parentRemap);
                    }
                }
            }
        }
    }

    private void rewriteAt(String owner, AnnotationNode source, AnnotationNode output, boolean parentRemap) throws IOException {
        boolean remap = enabled(source, parentRemap);
        if (!remap) {
            copyValue(source, output, "target");
            copyValue(source, output, "from");
            copyValue(source, output, "to");
            return;
        }
        if (source.desc.equals(PREFIX + "injection/At;")) {
            String target = string(source, "target", "");
            if (!target.isEmpty()) {
                if (string(source, "value", "").equals("NEW") && !target.startsWith("L")) {
                    put(output, "target", target.startsWith("(") ? mappings.descriptorFromSrg(target) : mappings.reflectionClassFromSrg(target));
                } else put(output, "target", selector(owner, target));
            }
        }
        rewriteNested(owner, source, output, remap);
    }

    private String mappedName(String mixin, String name, String descriptor, boolean method) throws IOException {
        String reference = lookup(mixin, name + (method ? descriptor : ":" + descriptor));
        if (reference == null) reference = lookup(mixin, name);
        String mapped;
        if (reference != null) mapped = mapSelector(mixin, reference);
        else mapped = mapSelector(mixin, name + (method ? descriptor : ":" + descriptor));
        int owner = mapped.indexOf(';');
        int start = mapped.indexOf('('), field = mapped.indexOf(':');
        if (mapped.startsWith("L") && owner >= 0 && (start < 0 || owner < start)
            && (field < 0 || owner < field)) mapped = mapped.substring(owner + 1);
        int methodStart = mapped.indexOf('('), fieldStart = mapped.indexOf(':');
        int end = methodStart >= 0 ? methodStart : fieldStart;
        return end >= 0 ? mapped.substring(0, end) : mapped;
    }

    private String selector(String mixin, String selector) throws IOException {
        String resolved = lookup(mixin, selector);
        return mapSelector(mixin, resolved == null ? selector : resolved);
    }

    private String mapSelector(String mixin, String reference) throws IOException {
        if (reference.indexOf('*') >= 0) throw new IOException("Wildcard Mixin selectors need explicit target expansion: " + mixin);
        try {
            Context context = contexts.get(mixin);
            if (context == null || context.targets.isEmpty()) return mappings.mixinSelectorFromSrg(reference);
            Set<String> mapped = new LinkedHashSet<>();
            for (String target : context.targets) mapped.add(mappings.mixinSelectorFromSrg(target, reference));
            if (mapped.size() != 1) throw new IOException("Mixin target members have different runtime names: " + mixin);
            return mapped.iterator().next();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Cannot remap Mixin selector in " + mixin + ": " + reference, exception);
        }
    }

    private String lookup(String mixin, String key) {
        return references.getOrDefault(mixin, Map.of()).get(key);
    }

    Map<String, byte[]> rewrittenRefmaps() throws IOException {
        Map<String, byte[]> output = new LinkedHashMap<>();
        for (var file : refmaps.entrySet()) {
            JsonObject root = file.getValue();
            JsonObject rewritten = new JsonObject();
            JsonObject source = canonical(root);
            for (var entry : source.entrySet()) {
                String mixin = entry.getKey().replace('.', '/');
                JsonObject values = new JsonObject();
                for (var reference : entry.getValue().getAsJsonObject().entrySet()) {
                    String key = reference.getKey();
                    String value = references.getOrDefault(mixin, Map.of()).getOrDefault(key, reference.getValue().getAsString());
                    if (!contexts.containsKey(mixin) || contexts.get(mixin).remap) value = mapSelector(mixin, value);
                    values.addProperty(key, value);
                }
                rewritten.add(entry.getKey(), values);
            }
            root.add("mappings", rewritten);
            if (root.has("data")) {
                JsonObject data = root.getAsJsonObject("data");
                for (String namespace : new ArrayList<>(data.entrySet().stream().map(Map.Entry::getKey).toList())) data.add(namespace, rewritten);
            }
            output.put(file.getKey(), root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return output;
    }

    Map<String, byte[]> rewrittenConfigurations() throws IOException {
        Map<String, byte[]> output = new LinkedHashMap<>();
        for (var entry : configurations.entrySet()) {
            JsonObject config = entry.getValue();
            if (config.has("overwrites") && !config.get("overwrites").isJsonObject()) {
                throw new IOException("Invalid Mixin overwrite options: " + entry.getKey());
            }
            JsonObject overwrites = config.has("overwrites") ? config.getAsJsonObject("overwrites") : new JsonObject();
            if (overwrites.has("conformVisibility") && overwrites.get("conformVisibility").isJsonPrimitive()
                    && overwrites.getAsJsonPrimitive("conformVisibility").isBoolean() && overwrites.get("conformVisibility").getAsBoolean()) continue;
            overwrites.addProperty("conformVisibility", true);
            config.add("overwrites", overwrites);
            output.put(entry.getKey(), config.toString().getBytes(StandardCharsets.UTF_8));
        }
        return output;
    }

    private static boolean isConfiguration(JsonObject object) {
        if (!object.has("package") || !object.get("package").isJsonPrimitive()
                || !object.getAsJsonPrimitive("package").isString()) return false;
        for (String section : List.of("mixins", "client", "server")) {
            if (object.has(section) && object.get(section).isJsonArray()) return true;
        }
        return object.has("plugin") && object.get("plugin").isJsonPrimitive() && object.getAsJsonPrimitive("plugin").isString();
    }

    private static Set<String> declaredConfigurations(Map<String, byte[]> resources) throws IOException {
        Set<String> declared = new LinkedHashSet<>();
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("META-INF/MANIFEST.MF")) continue;
            String names = new Manifest(new ByteArrayInputStream(entry.getValue())).getMainAttributes().getValue("MixinConfigs");
            if (names != null) for (String name : names.split(",")) if (!name.isBlank()) declared.add(name.trim());
        }
        return declared;
    }

    private static JsonObject canonical(JsonObject root) throws IOException {
        JsonObject result = new JsonObject();
        List<JsonObject> sources = new ArrayList<>();
        if (root.has("mappings")) sources.add(root.getAsJsonObject("mappings"));
        if (root.has("data") && root.getAsJsonObject("data").has("searge")) {
            sources.add(root.getAsJsonObject("data").getAsJsonObject("searge"));
        }
        if (sources.isEmpty()) throw new IOException("Mixin refmap has no SRG mappings");
        for (JsonObject source : sources) {
            for (var entry : source.entrySet()) {
                JsonObject values = result.has(entry.getKey()) ? result.getAsJsonObject(entry.getKey()) : new JsonObject();
                for (var reference : entry.getValue().getAsJsonObject().entrySet()) values.add(reference.getKey(), reference.getValue());
                result.add(entry.getKey(), values);
            }
        }
        if (root.has("data")) for (var namespace : root.getAsJsonObject("data").entrySet()) {
            for (var entry : namespace.getValue().getAsJsonObject().entrySet()) {
                for (String key : entry.getValue().getAsJsonObject().entrySet().stream().map(Map.Entry::getKey).toList()) {
                    if (!result.has(entry.getKey()) || !result.getAsJsonObject(entry.getKey()).has(key)) {
                        throw new IOException("Mixin refmap reference has no canonical SRG mapping");
                    }
                }
            }
        }
        return result;
    }

    static JsonElement parseJson(byte[] bytes, int maximum) throws IOException {
        if (bytes.length > maximum) throw new IOException("Compatibility JSON exceeds the size limit");
        String text = new String(bytes, StandardCharsets.UTF_8);
        boolean quoted = false, escaped = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > 128) throw new IOException("Compatibility JSON exceeds the nesting limit");
            } else if (value == '}' || value == ']') depth--;
        }
        return new JsonParser().parse(text);
    }

    private static String derivedTarget(String name, boolean method) throws IOException {
        String prefix = method ? name.startsWith("call") ? "call" : name.startsWith("invoke") ? "invoke" : ""
            : name.startsWith("get") || name.startsWith("set") ? name.substring(0, 3) : name.startsWith("is") ? "is" : "";
        if (prefix.isEmpty() || name.length() == prefix.length()) throw new IOException("Cannot infer Mixin accessor target: " + name);
        String suffix = name.substring(prefix.length());
        return suffix.length() > 1 && Character.isUpperCase(suffix.charAt(1)) ? suffix
            : Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static List<String> targets(AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        Object classes = value(annotation, "value"), strings = value(annotation, "targets");
        if (classes instanceof List<?> list) for (Object item : list) if (item instanceof Type type) targets.add(type.getInternalName());
        if (strings instanceof List<?> list) for (Object item : list) if (item instanceof String text) targets.add(text.replace('.', '/'));
        return targets;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor) {
        return annotations.stream().filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }

    private static String string(AnnotationNode annotation, String key, String fallback) {
        Object value = value(annotation, key);
        return value instanceof String text ? text : fallback;
    }

    private static boolean enabled(AnnotationNode annotation, boolean fallback) {
        Object value = value(annotation, "remap");
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static void put(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) annotation.values = new ArrayList<>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) { annotation.values.set(i + 1, value); return; }
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }

    private static void copyValue(AnnotationNode source, AnnotationNode target, String key) {
        Object value = value(source, key);
        if (value != null) put(target, key, value);
    }
}
