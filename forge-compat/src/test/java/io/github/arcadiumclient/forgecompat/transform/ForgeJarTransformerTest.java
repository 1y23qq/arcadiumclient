package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgeJarTransformerTest {
    @TempDir Path directory;
    private final ForgeJarTransformer transformer = new ForgeJarTransformer();

    @Test void boundsChecksGuestKeyboardQueriesWithoutChangingMouseAndOtherKeyboardCalls() throws Exception {
        ClassNode source = simple("example/Keys");
        MethodNode query = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "query", "(I)Z", null, null);
        query.visitVarInsn(Opcodes.ILOAD, 0);
        query.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "isKeyDown", "(I)Z", false);
        query.visitInsn(Opcodes.IRETURN); source.methods.add(query);
        MethodNode keyName = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name", "(I)Ljava/lang/String;", null, null);
        keyName.visitVarInsn(Opcodes.ILOAD, 0);
        keyName.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "getKeyName", "(I)Ljava/lang/String;", false);
        keyName.visitInsn(Opcodes.ARETURN); source.methods.add(keyName);
        MethodNode mouse = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mouse", "(I)Z", null, null);
        mouse.visitVarInsn(Opcodes.ILOAD, 0);
        mouse.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/input/Mouse", "isButtonDown", "(I)Z", false);
        mouse.visitInsn(Opcodes.IRETURN); source.methods.add(mouse);
        Path input = jar(directory.resolve("keys.jar"), Map.of(source.name + ".class", bytes(source)));
        for (boolean official : List.of(false, true)) {
            Path output = transformer.transform(input, directory.resolve("cache"), mappings(), official, null);
            ClassNode result = node(entries(output).get(source.name + ".class"));
            var queries = result.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
            assertEquals("io/github/arcadiumclient/forgecompat/LegacyKeyboard", queries.get(1).owner);
            assertEquals("io/github/arcadiumclient/forgecompat/LegacyKeyboard", queries.get(2).owner);
            assertEquals("org/lwjgl/input/Mouse", queries.get(3).owner);
            assertEquals(1, verify(output));
        }
    }

    @Test void remapsOfficialAndSrgInputsWithoutChangingTheOriginalJar() throws Exception {
        ClassNode source = simple(GAME);
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "field_1", "I", null, null));
        Path input = jar(directory.resolve("mod.jar"), Map.of(GAME + ".class", bytes(source)));
        byte[] original = Files.readAllBytes(input);
        Path result = transformer.transform(input, directory.resolve("cache"), mappings(), false, null);
        ClassNode output = node(entries(result).get(RUNTIME + ".class"));
        assertEquals("f_timer", output.fields.get(0).name);
        assertEquals(1, verify(result));
        assertArrayEquals(original, Files.readAllBytes(input));
        ClassNode api = simple("a");
        api.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "x", "I", null, null));
        Path forge = jar(directory.resolve("forge.jar"), Map.of("a.class", bytes(api)));
        ClassNode forgeOutput = node(entries(transformer.transform(forge, directory.resolve("cache"), mappings(), true, null)).get(RUNTIME + ".class"));
        assertEquals("f_timer", forgeOutput.fields.get(0).name);
    }

    @Test void verifiesCachedContentAndInvalidatesOnNamespaceAndHierarchyChanges() throws Exception {
        Path input = jar(directory.resolve("mod.jar"), Map.of(GAME + ".class", bytes(simple(GAME))));
        ForgeMappings mappings = mappings();
        Path cache = directory.resolve("cache");
        Path first = transformer.transform(input, cache, mappings, false, null);
        byte[] expected = Files.readAllBytes(first);
        assertEquals(first, transformer.transform(input, cache, mappings, false, null));
        Files.write(first, text("corrupt"));
        assertEquals(first, transformer.transform(input, cache, mappings, false, null));
        assertArrayEquals(expected, Files.readAllBytes(first));
        Files.writeString(first.resolveSibling(first.getFileName() + ".json"), "{}");
        assertEquals(first, transformer.transform(input, cache, mappings, false, null));
        assertArrayEquals(expected, Files.readAllBytes(first));
        Path named = transformer.transform(input, cache, mappings("named"), false, null);
        assertNotEquals(first, named);
        assertTrue(entries(named).containsKey("dev/Game.class"));
        mappings.indexSrgHierarchy("another/Child", GAME, List.of());
        assertNotEquals(first, transformer.transform(input, cache, mappings, false, null));
    }

    @Test void appliesTrustedPatchesEveryTimeIncludingCacheHitsAndCapturedState() throws Exception {
        Path input = jar(directory.resolve("mod.jar"), Map.of("example/A.class", bytes(simple("example/A"))));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger marker = new AtomicInteger(1);
        java.util.function.Consumer<ClassNode> patch = node -> {
            calls.incrementAndGet();
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "marker", "I", null, marker.get()));
        };
        ForgeMappings mappings = mappings();
        Path first = transformer.transform(input, directory.resolve("cache"), mappings, false, patch);
        assertEquals(first, transformer.transform(input, directory.resolve("cache"), mappings, false, patch));
        marker.set(2);
        Path second = transformer.transform(input, directory.resolve("cache"), mappings, false, patch);
        assertNotEquals(first, second);
        assertEquals(3, calls.get());
        assertEquals(2, node(entries(second).get("example/A.class")).fields.get(0).value);
        assertEquals(1, verify(second));
        assertThrows(IOException.class, () -> transformer.transform(input, directory.resolve("cache"), mappings,
            false, node -> { throw new IllegalStateException("fixture"); }));
        assertEquals(second, transformer.transform(input, directory.resolve("cache"), mappings, false, patch));
    }

    @Test void patchCanSwapClassNamesWithoutCollidingWithUnpatchedEntries() throws Exception {
        Path input = jar(directory.resolve("mod.jar"), Map.of("example/A.class", bytes(simple("example/A")),
            "example/B.class", bytes(simple("example/B"))));
        Path output = transformer.transform(input, directory.resolve("cache"), mappings(), false,
            node -> node.name = node.name.equals("example/A") ? "example/B" : "example/A");
        assertEquals(2, verify(output));
        assertThrows(IOException.class, () -> transformer.transform(input, directory.resolve("cache"), mappings(),
            false, node -> node.name = "example/A"));
    }

    @Test void removesOnlyStaleSignaturesAndDelegatedLibrariesWhilePreservingLicensesAndConfig() throws Exception {
        Map<String, byte[]> source = new LinkedHashMap<>();
        source.put("META-INF/MANIFEST.MF", text("Manifest-Version: 1.0\r\nTweakClass: org.spongepowered.asm.launch.MixinTweaker\r\nMixinConfigs: mixins.test.json\r\nFMLAT: example_at.cfg\r\nSignature-Version: 1.0\r\nSHA-256-Digest-Manifest: old\r\n\r\nName: example/A.class\r\nSHA-256-Digest: old\r\n\r\n"));
        source.put("example/A.class", bytes(simple("example/A")));
        source.put("org/spongepowered/asm/Old.class", text("discarded before parsing"));
        source.put("org/spongepowered/tools/Old.class", text("discarded"));
        source.put("org/objectweb/asm/Old.class", text("discarded"));
        source.put("META-INF/versions/17/org/objectweb/asm/Old.class", text("discarded"));
        source.put("META-INF/MOD.SF", text("signature"));
        source.put("META-INF/MOD.RSA", text("signature"));
        source.put("META-INF/services/javax.annotation.processing.Processor", text("org.spongepowered.tools.obfuscation.MixinObfuscationProcessor\n"));
        source.put("META-INF/services/example.Service", text("org.spongepowered.asm.Service\nexample.Keep # retained\n"));
        source.put("META-INF/LICENSE.txt", text("Original license"));
        source.put("mixins.test.json", text("{}"));
        source.put("example_at.cfg", text("public example.A"));
        Path input = jar(directory.resolve("mod.jar"), source);
        Map<String, byte[]> output = entries(transformer.transform(input, directory.resolve("cache"), mappings(), false, null));
        assertFalse(output.keySet().stream().anyMatch(key -> key.contains("org/spongepowered/") || key.contains("org/objectweb/asm/")));
        assertFalse(output.containsKey("META-INF/MOD.SF"));
        assertFalse(output.containsKey("META-INF/MOD.RSA"));
        assertFalse(output.containsKey("META-INF/services/javax.annotation.processing.Processor"));
        assertEquals("example.Keep # retained\n", new String(output.get("META-INF/services/example.Service"), java.nio.charset.StandardCharsets.UTF_8));
        assertArrayEquals(source.get("META-INF/LICENSE.txt"), output.get("META-INF/LICENSE.txt"));
        assertTrue(MixinReferences.parseJson(output.get("mixins.test.json"), 4096).getAsJsonObject()
            .getAsJsonObject("overwrites").get("conformVisibility").getAsBoolean());
        Manifest manifest = new Manifest(new ByteArrayInputStream(output.get("META-INF/MANIFEST.MF")));
        assertNull(manifest.getMainAttributes().getValue("TweakClass"));
        assertNull(manifest.getMainAttributes().getValue("Signature-Version"));
        assertEquals("mixins.test.json", manifest.getMainAttributes().getValue("MixinConfigs"));
        assertEquals("example_at.cfg", manifest.getMainAttributes().getValue("FMLAT"));
        assertTrue(manifest.getEntries().isEmpty());
    }

    @Test void rewritesReflectionCallsitesAndKnownMovedTypesWithoutChangingStringLiterals() throws Exception {
        ClassNode source = simple("example/Reflection");
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "strings", "Lorg/apache/logging/log4j/core/helpers/Strings;", null, null));
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "failure", "Lsun/reflect/generics/reflectiveObjects/NotImplementedException;", null, null));
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "integers", "Lorg/apache/logging/log4j/core/helpers/Integers;", null, null));
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "booleans", "Lorg/apache/logging/log4j/core/helpers/Booleans;", null, null));
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "reflect", "()V", null, null);
        method.visitLdcInsn("net.minecraft.client.Minecraft");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        method.visitInsn(Opcodes.POP);
        for (String name : List.of("getDeclaredField", "getField", "getDeclaredMethod", "getMethod")) {
            method.visitLdcInsn(Type.getObjectType(GAME));
            method.visitLdcInsn("field_1");
            boolean field = name.endsWith("Field");
            if (!field) { method.visitInsn(Opcodes.ICONST_0); method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class"); }
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", name,
                field ? "(Ljava/lang/String;)Ljava/lang/reflect/Field;" : "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        source.methods.add(method);
        Path input = jar(directory.resolve("mod.jar"), Map.of(source.name + ".class", bytes(source)));
        Path result = transformer.transform(input, directory.resolve("cache"), mappings(), false, null);
        ClassNode output = node(entries(result).get(source.name + ".class"));
        List<MethodInsnNode> calls = new ArrayList<>();
        for (var instruction : output.methods.get(1).instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        assertEquals(5, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.owner.equals("io/github/arcadiumclient/forgecompat/reflect/ForgeReflection")
            && call.getOpcode() == Opcodes.INVOKESTATIC));
        assertTrue(calls.get(1).desc.startsWith("(Ljava/lang/Class;"));
        assertEquals("Lorg/apache/logging/log4j/util/Strings;", output.fields.get(0).desc);
        assertEquals("Ljava/lang/UnsupportedOperationException;", output.fields.get(1).desc);
        assertEquals("Lorg/apache/logging/log4j/core/util/Integers;", output.fields.get(2).desc);
        assertEquals("Lorg/apache/logging/log4j/core/util/Booleans;", output.fields.get(3).desc);
        assertEquals("net.minecraft.client.Minecraft", ((org.objectweb.asm.tree.LdcInsnNode) output.methods.get(1).instructions.getFirst()).cst);
        assertEquals(1, verify(result));
    }

    @Test void rejectsMalformedArchivesUnsafeNamesDuplicateEntriesAndMismatchedClassNames() throws Exception {
        Path bad = directory.resolve("bad.jar");
        Files.write(bad, text("not a zip"));
        assertThrows(IOException.class, () -> transformer.transform(bad, directory.resolve("cache"), mappings(), false, null));
        for (String path : List.of("../escape", "/absolute", "C:/drive", "nested\\escape", "nested//empty")) {
            Path unsafe = jar(directory.resolve("unsafe.jar"), Map.of(path, text("resource")));
            assertThrows(IOException.class, () -> transformer.transform(unsafe, directory.resolve("cache"), mappings(), false, null));
        }
        Path mismatch = jar(directory.resolve("mismatch.jar"), Map.of("example/A.class", bytes(simple("example/B"))));
        assertThrows(IOException.class, () -> transformer.transform(mismatch, directory.resolve("cache"), mappings(), false, null));
        Path invalid = jar(directory.resolve("invalid.jar"), Map.of("example/A.class", new byte[]{0, 1, 2}));
        assertThrows(IOException.class, () -> transformer.transform(invalid, directory.resolve("cache"), mappings(), false, null));
        Path duplicate = jar(directory.resolve("duplicate.jar"), Map.of("one", text("a"), "two", text("b")));
        byte[] duplicateBytes = Files.readAllBytes(duplicate);
        for (int i = 0; i <= duplicateBytes.length - 3; i++) {
            if (duplicateBytes[i] == 't' && duplicateBytes[i + 1] == 'w' && duplicateBytes[i + 2] == 'o') {
                duplicateBytes[i] = 'o'; duplicateBytes[i + 1] = 'n'; duplicateBytes[i + 2] = 'e';
            }
        }
        Files.write(duplicate, duplicateBytes);
        assertThrows(IOException.class, () -> transformer.transform(duplicate, directory.resolve("cache"), mappings(), false, null));
    }

    @Test void rejectsAnEntryWhoseDeclaredSizeExceedsTheClassLimit() throws Exception {
        Path input = jar(directory.resolve("oversized.jar"), Map.of("example/A.class", bytes(simple("example/A"))));
        byte[] archive = Files.readAllBytes(input);
        boolean changed = false;
        for (int i = 0; i < archive.length - 28; i++) {
            if (archive[i] == 0x50 && archive[i + 1] == 0x4b && archive[i + 2] == 1 && archive[i + 3] == 2) {
                int size = 16 * 1024 * 1024 + 1;
                for (int n = 0; n < 4; n++) archive[i + 24 + n] = (byte) (size >>> (n * 8));
                changed = true;
            }
        }
        assertTrue(changed);
        Files.write(input, archive);
        IOException failure = assertThrows(IOException.class,
            () -> transformer.transform(input, directory.resolve("cache"), mappings(), false, null));
        assertTrue(failure.getMessage().startsWith("Oversized JAR entry:"));
    }
}
