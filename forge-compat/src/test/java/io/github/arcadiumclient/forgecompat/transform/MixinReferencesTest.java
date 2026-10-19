package io.github.arcadiumclient.forgecompat.transform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MixinReferencesTest {
    @TempDir Path directory;
    private static final String PREFIX = "Lorg/spongepowered/asm/mixin/";

    @Test void remapsReadableShadowOverwriteAccessorInvokerAndInjectionReferences() throws Exception {
        ClassNode source = mixin();
        FieldNode shadow = new FieldNode(Opcodes.ACC_PUBLIC, "timer", "I", null, null);
        shadow.invisibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Shadow;", "aliases", List.of("timer"))));
        source.fields.add(shadow);
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "tick", "()V", null, null);
        tick.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Shadow;")));
        source.methods.add(tick);
        MethodNode shadowCaller = method("useShadow", "()V");
        shadowCaller.visitVarInsn(Opcodes.ALOAD, 0);
        shadowCaller.visitFieldInsn(Opcodes.GETFIELD, MIXIN, "timer", "I");
        shadowCaller.visitInsn(Opcodes.POP);
        shadowCaller.visitVarInsn(Opcodes.ALOAD, 0);
        shadowCaller.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MIXIN, "tick", "()V", false);
        shadowCaller.visitInsn(Opcodes.RETURN);
        source.methods.add(shadowCaller);
        MethodNode accessor = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getTimer", "()I", null, null);
        accessor.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "gen/Accessor;")));
        source.methods.add(accessor);
        MethodNode invoker = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "invokeTick", "()V", null, null);
        invoker.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "gen/Invoker;")));
        source.methods.add(invoker);
        MethodNode overwrite = method("getMinecraft", "()L" + GAME + ";");
        overwrite.visitInsn(Opcodes.ACONST_NULL);
        overwrite.visitInsn(Opcodes.ARETURN);
        overwrite.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Overwrite;")));
        source.methods.add(overwrite);
        MethodNode inject = method("onTick", "()V");
        inject.visitInsn(Opcodes.RETURN);
        inject.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "method", List.of("tick"),
            "at", List.of(annotation(PREFIX + "injection/At;", "value", "INVOKE", "target", "L" + GAME + ";getMinecraft()L" + GAME + ";")))));
        source.methods.add(inject);
        JsonObject refs = new JsonObject();
        refs.addProperty("timer", "field_1:I");
        refs.addProperty("tick", "func_1()V");
        refs.addProperty("getMinecraft()L" + GAME + ";", "func_2()L" + GAME + ";");
        refs.addProperty("L" + GAME + ";getMinecraft()L" + GAME + ";", "L" + GAME + ";func_2()L" + GAME + ";");
        Path result = transform(source, refmap(refs));
        ClassNode output = node(entries(result).get(MIXIN + ".class"));
        assertEquals("f_timer", output.fields.get(0).name);
        assertEquals("f_timer", ((List<?>) value(output.fields.get(0).invisibleAnnotations.get(0), "aliases")).get(0));
        assertTrue(output.methods.stream().anyMatch(method -> method.name.equals("m_tick")));
        assertTrue(output.methods.stream().anyMatch(method -> method.name.equals("m_get")));
        MethodNode caller = output.methods.stream().filter(method -> method.name.equals("useShadow")).findFirst().orElseThrow();
        for (var instruction : caller.instructions) {
            if (instruction instanceof FieldInsnNode field) assertEquals("f_timer", field.name);
            if (instruction instanceof MethodInsnNode call) assertEquals("m_tick", call.name);
        }
        MethodNode accessorOutput = output.methods.stream().filter(method -> method.name.equals("getTimer")).findFirst().orElseThrow();
        assertEquals("f_timer", value(accessorOutput.visibleAnnotations.get(0), "value"));
        MethodNode invokerOutput = output.methods.stream().filter(method -> method.name.equals("invokeTick")).findFirst().orElseThrow();
        assertEquals("m_tick", value(invokerOutput.visibleAnnotations.get(0), "value"));
        MethodNode injectOutput = output.methods.stream().filter(method -> method.name.equals("onTick")).findFirst().orElseThrow();
        AnnotationNode injection = injectOutput.visibleAnnotations.get(0);
        assertEquals(List.of("m_tick()V"), value(injection, "method"));
        AnnotationNode at = (AnnotationNode) ((List<?>) value(injection, "at")).get(0);
        assertEquals("L" + RUNTIME + ";m_get()L" + RUNTIME + ";", value(at, "target"));
        JsonObject rewritten = new JsonParser().parse(new String(entries(result).get("mixins.test.refmap.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("f_timer:I", rewritten.getAsJsonObject("mappings").getAsJsonObject(MIXIN).get("timer").getAsString());
        assertEquals(1, verify(result));
    }

    @Test void honorsRemapFalseAtClassMemberAndNestedInjectionScopes() throws Exception {
        ClassNode source = mixin();
        FieldNode shadow = new FieldNode(Opcodes.ACC_PUBLIC, "field_1", "I", null, null);
        shadow.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Shadow;", "remap", false)));
        source.fields.add(shadow);
        MethodNode disabled = method("disabled", "()V");
        disabled.visitInsn(Opcodes.RETURN);
        disabled.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "remap", false,
            "method", List.of("func_1"), "at", List.of(annotation(PREFIX + "injection/At;", "value", "INVOKE", "target", "L" + GAME + ";func_1()V")))));
        source.methods.add(disabled);
        MethodNode nested = method("nested", "()V");
        nested.visitInsn(Opcodes.RETURN);
        nested.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "method", List.of("func_1"),
            "at", List.of(annotation(PREFIX + "injection/At;", "value", "INVOKE", "remap", false, "target", "L" + GAME + ";func_1()V")))));
        source.methods.add(nested);
        ClassNode output = node(entries(transform(source, null)).get(MIXIN + ".class"));
        assertEquals("field_1", output.fields.get(0).name);
        AnnotationNode disabledOutput = output.methods.get(1).visibleAnnotations.get(0);
        assertEquals(List.of("func_1"), value(disabledOutput, "method"));
        AnnotationNode nestedOutput = output.methods.get(2).visibleAnnotations.get(0);
        assertEquals(List.of("m_tick"), value(nestedOutput, "method"));
        AnnotationNode at = (AnnotationNode) ((List<?>) value(nestedOutput, "at")).get(0);
        assertEquals("L" + GAME + ";func_1()V", value(at, "target"));
        ClassNode external = simple("example/Pseudo");
        external.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Mixin;", "targets", List.of("net.optifine.CustomSky"), "remap", false)));
        ClassNode externalOutput = node(entries(transform(external, null)).get("example/Pseudo.class"));
        assertEquals(List.of("net.optifine.CustomSky"), value(externalOutput.visibleAnnotations.get(0), "targets"));
    }

    @Test void remapsStringTargetsAndSliceSelectorsAndPreservesOrdinaryAnnotationText() throws Exception {
        ClassNode source = mixin();
        source.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Mixin;", "targets", List.of(GAME.replace('/', '.'))),
            annotation("Lexample/Text;", "value", GAME)));
        MethodNode inject = method("slice", "()V");
        inject.visitInsn(Opcodes.RETURN);
        inject.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "method", List.of("func_1()V"),
            "slice", List.of(annotation(PREFIX + "injection/Slice;", "from", annotation(PREFIX + "injection/At;", "value", "FIELD", "target", "L" + GAME + ";field_1:I"))))));
        source.methods.add(inject);
        ClassNode output = node(entries(transform(source, null)).get(MIXIN + ".class"));
        assertEquals(List.of("host.Game"), value(output.visibleAnnotations.get(0), "targets"));
        assertEquals(GAME, value(output.visibleAnnotations.get(1), "value"));
        AnnotationNode slice = (AnnotationNode) ((List<?>) value(output.methods.get(1).visibleAnnotations.get(0), "slice")).get(0);
        assertEquals("L" + RUNTIME + ";f_timer:I", value((AnnotationNode) value(slice, "from"), "target"));
    }

    @Test void mergesCanonicalSrgRefmapEntriesAndRetainsDefaultOnlyEntries() throws Exception {
        JsonObject root = new JsonObject();
        JsonObject defaults = new JsonObject(), canonical = new JsonObject();
        defaults.addProperty("tick", "func_1()V");
        canonical.addProperty("getMinecraft", "func_2()L" + GAME + ";");
        JsonObject defaultClasses = new JsonObject(), canonicalClasses = new JsonObject(), data = new JsonObject();
        defaultClasses.add(MIXIN, defaults); canonicalClasses.add(MIXIN, canonical);
        data.add("searge", canonicalClasses); root.add("mappings", defaultClasses); root.add("data", data);
        Path result = transform(mixin(), root.toString());
        JsonObject rewritten = new JsonParser().parse(new String(entries(result).get("mixins.test.refmap.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject refs = rewritten.getAsJsonObject("mappings").getAsJsonObject(MIXIN);
        assertEquals("m_tick()V", refs.get("tick").getAsString());
        assertEquals("m_get()L" + RUNTIME + ";", refs.get("getMinecraft").getAsString());
        assertEquals(rewritten.get("mappings"), rewritten.getAsJsonObject("data").get("searge"));
    }

    @Test void failsClearlyOnMissingInvalidOrUnsupportedMixinReferences() throws Exception {
        ClassNode source = mixin();
        MethodNode method = method("onTick", "()V");
        method.visitInsn(Opcodes.RETURN);
        method.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "method", List.of("func_*"))));
        source.methods.add(method);
        assertThrows(IOException.class, () -> transform(source, null));
        assertThrows(IOException.class, () -> transform(mixin(), "not JSON"));
        assertThrows(IOException.class, () -> transform(mixin(), "[".repeat(129) + "]".repeat(129)));
        assertThrows(IOException.class, () -> transform(mixin(), "{\"mappings\":{},\"data\":{\"notch\":{\"example/GameMixin\":{\"onlyNotch\":\"a()V\"}}}}"));
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put(MIXIN + ".class", bytes(mixin()));
        resources.put("mixins.test.json", text("{\"package\":\"example\",\"mixins\":[\"GameMixin\"],\"refmap\":\"missing.json\"}"));
        Path input = jar(directory.resolve("missing.jar"), resources);
        assertThrows(IOException.class, () -> new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null));
    }

    private Path transform(ClassNode node, String refmap) throws Exception {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put(node.name + ".class", bytes(node));
        if (refmap != null) {
            resources.put("mixins.test.refmap.json", text(refmap));
            resources.put("mixins.test.json", text("{\"package\":\"example\",\"mixins\":[\"GameMixin\"],\"refmap\":\"mixins.test.refmap.json\"}"));
        }
        Path input = jar(directory.resolve("input.jar"), resources);
        return new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null);
    }

    private static ClassNode mixin() {
        ClassNode node = simple(MIXIN);
        node.access |= Opcodes.ACC_ABSTRACT;
        node.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Mixin;", "value", List.of(Type.getObjectType(GAME)))));
        return node;
    }

    private static MethodNode method(String name, String descriptor) { return new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null); }

    private static AnnotationNode annotation(String descriptor, Object... values) {
        AnnotationNode node = new AnnotationNode(descriptor);
        node.values = new ArrayList<>(List.of(values));
        return node;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static String refmap(JsonObject values) {
        JsonObject root = new JsonObject(), classes = new JsonObject();
        classes.add(MIXIN, values); root.add("mappings", classes);
        return root.toString();
    }
}
