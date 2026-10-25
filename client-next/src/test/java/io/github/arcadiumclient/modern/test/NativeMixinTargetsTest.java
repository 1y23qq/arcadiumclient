// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

public class NativeMixinTargetsTest {
    private final Map<String, ClassNode> classes = new HashMap<>();
    @Test void nativeAccessorsInvokersAndShadowsMatchActualDescriptors() throws Exception {
        List<String> errors = new ArrayList<>();
        int checked = 0;
        for (String resource : List.of("arcadium-modern.mixins.json", "arcadium-modern-gui.mixins.json", "arcadium-hypixel.mixins.json", "arcadium-glint.mixins.json", "arcadium-native-migration.mixins.json")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var name : config.getAsJsonArray("client")) {
                    String mixinName = config.get("package").getAsString() + "." + name.getAsString();
                    ClassNode mixin = read(mixinName.replace('.', '/'));
                    var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                        .filter(value -> value.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
                    if (!(values(annotation).get("value") instanceof List<?> targets)) continue;
                    for (Object value : targets) {
                        ClassNode target = read(((Type) value).getInternalName());
                        for (var field : mixin.fields) if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream().anyMatch(a -> a.desc.endsWith("/Shadow;"))) {
                            if (target.fields.stream().noneMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)))
                                errors.add(mixinName + " shadows missing native field " + field.name + ":" + field.desc);
                            checked++;
                        }
                        for (MethodNode method : mixin.methods) for (var generated : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                            if (generated.desc.endsWith("/Invoker;")) {
                                String selected = String.valueOf(values(generated).get("value"));
                                if (target.methods.stream().noneMatch(m -> m.name.equals(selected) && m.desc.equals(method.desc)))
                                    errors.add(mixinName + " invokes missing native method " + selected + method.desc);
                                checked++;
                            } else if (generated.desc.endsWith("/Accessor;")) {
                                String selected = String.valueOf(values(generated).get("value"));
                                Type[] arguments = Type.getArgumentTypes(method.desc);
                                String descriptor = arguments.length == 0 ? Type.getReturnType(method.desc).getDescriptor() : arguments[0].getDescriptor();
                                if (target.fields.stream().noneMatch(f -> f.name.equals(selected) && f.desc.equals(descriptor)))
                                    errors.add(mixinName + " accesses missing native field " + selected + ":" + descriptor);
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 20, "The native field and bridge audit did not scan the runtime accessors.");
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
    @Test void nativeInjectorsUseTheDeclaringMinecraftClass() throws Exception {
        var loader = getClass().getClassLoader();
        List<String> errors = new ArrayList<>();
        int checked = 0;
        for (String resource : List.of("arcadium-modern.mixins.json", "arcadium-modern-gui.mixins.json", "arcadium-hypixel.mixins.json", "arcadium-glint.mixins.json", "arcadium-baseline-compat.mixins.json", "arcadium-modern-profiles.mixins.json", "arcadium-native-migration.mixins.json")) {
            var input = loader.getResourceAsStream(resource);
            assertNotNull(input, resource);
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var name : config.getAsJsonArray("client")) {
                String mixinName = config.get("package").getAsString() + "." + name.getAsString();
                ClassNode mixin = read(mixinName.replace('.', '/'));
                var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                    .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                Object targets = values(annotation).get("value");
                if (!(targets instanceof List<?> targetsList)) continue;
                for (Object value : targetsList) {
                    ClassNode target = read(((Type) value).getInternalName());
                    for (MethodNode method : mixin.methods) for (AnnotationNode injector : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                        Object selectors = values(injector).get("method");
                        if (!(selectors instanceof List<?> selectorsList)) continue;
                        for (Object selector : selectorsList) {
                            String signature = selector.toString();
                            int descriptorStart = signature.indexOf('(');
                            String selectedName = descriptorStart < 0 ? signature : signature.substring(0, descriptorStart);
                            String descriptor = descriptorStart < 0 ? null : signature.substring(descriptorStart);
                            var matching = target.methods.stream().filter(candidate ->
                                candidate.name.matches(java.util.regex.Pattern.quote(selectedName).replace("*", "\\E.*\\Q")) &&
                                    (descriptor == null || descriptor.equals(candidate.desc))).toList();
                            boolean found = !matching.isEmpty();
                            if (!found) errors.add(mixinName + "." + method.name + " does not target a declared method " + target.name + "." + signature);
                            Object atValue = values(injector).get("at");
                            List<?> ats = atValue instanceof List<?> list ? list : atValue == null ? List.of() : List.of(atValue);
                            for (Object element : ats) {
                                var at = values((AnnotationNode) element);
                                String kind = String.valueOf(at.get("value"));
                                if (!kind.equals("INVOKE") && !kind.equals("FIELD")) continue;
                                String call = String.valueOf(at.get("target"));
                                boolean called = matching.stream().flatMap(candidate -> java.util.stream.StreamSupport.stream(candidate.instructions.spliterator(), false)).anyMatch(instruction ->
                                    instruction instanceof org.objectweb.asm.tree.MethodInsnNode invoke && call.equals("L" + invoke.owner + ";" + invoke.name + invoke.desc) ||
                                    instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && call.equals("L" + field.owner + ";" + field.name + ":" + field.desc));
                                if (!called) errors.add(mixinName + "." + method.name + " lacks native " + kind + " callsite " + call);
                            }
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 40, "The native injection audit did not scan the runtime mixins.");
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
    private ClassNode read(String name) throws Exception {
        ClassNode cached = classes.get(name);
        if (cached != null) return cached;
        try (var input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, "Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(name, node);
            return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }
    private static Map<String, Object> values(AnnotationNode node) {
        Map<String, Object> result = new HashMap<>();
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) result.put(node.values.get(i).toString(), node.values.get(i + 1));
        return result;
    }
}
