// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

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
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class GlintNativeTargetsTest {
    @Test void allGlintSelectorsAndNativeCallsitesExist() throws Exception {
        var loader = getClass().getClassLoader();
        var resource = loader.getResourceAsStream("arcadium-glint.mixins.json");
        assertNotNull(resource);
        var manifest = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
        var errors = new ArrayList<String>();
        int checked = 0;
        for (var name : manifest.getAsJsonArray("client")) {
            var mixin = read(manifest.get("package").getAsString().replace('.', '/') + "/" + name.getAsString());
            var annotation = all(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
            if (!(values(annotation).get("value") instanceof List<?> targetTypes)) continue;
            var targetType = (Type) targetTypes.get(0);
            var target = read(targetType.getInternalName());
            for (var method : mixin.methods) for (var injection : all(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (!(values(injection).get("method") instanceof List<?> selectors)) continue;
                for (var selector : selectors) {
                    String signature = selector.toString();
                    int split = signature.indexOf('(');
                    String selectedName = split < 0 ? signature : signature.substring(0, split);
                    var candidates = target.methods.stream().filter(m -> m.name.equals(selectedName) && (split < 0 || m.desc.equals(signature.substring(split)))).toList();
                    if (candidates.isEmpty()) errors.add(target.name + "." + signature + " not declared");
                    if (candidates.size() > 1) errors.add(target.name + "." + signature + " is ambiguous across overloads");
                    Object atValue = values(injection).get("at");
                    List<?> ats = atValue instanceof List<?> list ? list : atValue == null ? List.of() : List.of(atValue);
                    for (Object atObject : ats) {
                        var at = values((AnnotationNode) atObject);
                        if (!"INVOKE".equals(at.get("value"))) continue;
                        String call = at.get("target").toString();
                        boolean found = candidates.stream().flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)).anyMatch(i -> i instanceof MethodInsnNode invoke && call.equals("L" + invoke.owner + ";" + invoke.name + invoke.desc));
                        if (!found) errors.add(target.name + "." + signature + " lacks " + call);
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked >= 10);
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
    private ClassNode read(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static List<AnnotationNode> all(List<AnnotationNode> a, List<AnnotationNode> b) {
        var result = new ArrayList<AnnotationNode>(); if (a != null) result.addAll(a); if (b != null) result.addAll(b); return result;
    }
    private static Map<String, Object> values(AnnotationNode node) {
        var result = new HashMap<String, Object>();
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) result.put(node.values.get(i).toString(), node.values.get(i + 1));
        return result;
    }
}
