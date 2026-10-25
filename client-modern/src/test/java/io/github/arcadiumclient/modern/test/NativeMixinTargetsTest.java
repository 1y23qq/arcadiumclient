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
                            boolean found = target.methods.stream().anyMatch(candidate ->
                                candidate.name.matches(java.util.regex.Pattern.quote(selectedName).replace("*", "\\E.*\\Q")) &&
                                    (descriptor == null || descriptor.equals(candidate.desc)));
                            if (!found) errors.add(mixinName + "." + method.name + " does not target a declared method " + target.name + "." + signature);
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
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
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
