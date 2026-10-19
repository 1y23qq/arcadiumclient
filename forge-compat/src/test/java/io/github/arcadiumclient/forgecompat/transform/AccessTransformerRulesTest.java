package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.ForgeTestFixtures;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AccessTransformerRulesTest {
    private static final String SOURCE = "net/minecraft/test/Owner";
    private static final String RUNTIME = "net/minecraft/class_100";
    private static final String TINY = """
        tiny\t2\t0\tofficial\tintermediary
        c\ta\tnet/minecraft/class_100
        \tf\tI\tx\tfield_100
        \tf\t[La;\ty\tfield_101
        \tm\t(La;)La;\ta\tmethod_100
        \tm\t(I)V\ta\tmethod_101
        c\tb\tnet/minecraft/class_101
        c\tc\tnet/minecraft/class_102
        """;
    private static final String SRG = """
        CL: a net/minecraft/test/Owner
        CL: b net/minecraft/test/Owner$Inner
        CL: c net/minecraft/test/Other
        FD: a/x net/minecraft/test/Owner/field_1
        FD: a/y net/minecraft/test/Owner/field_2
        MD: a/a (La;)La; net/minecraft/test/Owner/func_1 (Lnet/minecraft/test/Owner;)Lnet/minecraft/test/Owner;
        MD: a/a (I)V net/minecraft/test/Owner/func_2 (I)V
        """;

    @TempDir Path temporary;

    private ForgeMappings mappings() throws Exception {
        return new ForgeMappings(resolver(TINY), new StringReader(SRG));
    }

    private MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl")
            .getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true);
        return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path path = temporary.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private Path config(String filename, String text) throws IOException {
        return jar(filename, Map.of("META-INF/test_at.cfg", text.getBytes(StandardCharsets.UTF_8)));
    }

    private AccessTransformerRules rules(String text) throws Exception {
        AccessTransformerRules rules = new AccessTransformerRules();
        rules.read(config("rules.jar", text), List.of("test_at.cfg"), mappings());
        return rules;
    }

    private ClassNode fixture() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.name = RUNTIME;
        node.superName = "example/Base";
        node.access = Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC,
            "field_100", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL,
            "field_101", "[L" + RUNTIME + ";", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "ordinary", "I", null, null));
        MethodNode target = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
            "method_100", "(L" + RUNTIME + ";)L" + RUNTIME + ";", null, null);
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(target);
        MethodNode overload = new MethodNode(Opcodes.ACC_PROTECTED, "method_101", "(I)V", null, null);
        overload.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(overload);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "example/Base", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(constructor);
        MethodNode caller = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        for (String owner : List.of(RUNTIME, "example/Base")) {
            caller.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            caller.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            caller.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner,
                "method_100", target.desc, false));
            caller.instructions.add(new InsnNode(Opcodes.POP));
        }
        caller.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(caller);
        node.innerClasses.add(new InnerClassNode("net/minecraft/class_101", RUNTIME, "Inner",
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));
        return node;
    }

    @Test void remapsSrgFieldsMethodsAndDescriptorsThroughTheHostResolver() throws Exception {
        AccessTransformerRules rules = rules("""
            # member rules
            public-f net.minecraft.test.Owner field_1
            public net/minecraft/test/Owner field_2
            public-f net.minecraft.test.Owner func_1(Lnet/minecraft/test/Owner;)Lnet/minecraft/test/Owner; # overload
            """);
        assertTrue(rules.targets(RUNTIME));
        assertFalse(rules.targets(SOURCE));
        assertFalse(rules.targets("unrelated/Mod"));
        ClassNode node = fixture();
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, node.fields.get(0).access);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, node.fields.get(1).access);
        assertEquals(Opcodes.ACC_PUBLIC, node.methods.get(0).access);
        assertEquals(Opcodes.ACC_PROTECTED, node.methods.get(1).access);
        assertEquals(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, node.access);
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(output);
        ClassNode roundtrip = new ClassNode();
        new ClassReader(output.toByteArray()).accept(roundtrip, 0);
        assertEquals(node.methods.get(0).access, roundtrip.methods.get(0).access);
        roundtrip.check(Opcodes.ASM9);
    }

    @Test void widensAllFourVisibilityLevelsAndPreservesUnrelatedFlags() throws Exception {
        AccessTransformerRules rules = rules("""
            default example.Target fromPrivate
            protected example.Target fromDefault
            public example.Target fromProtected
            private example.Target alreadyPublic
            private example.Target alreadyDefault
            """);
        ClassNode node = new ClassNode();
        node.name = "example/Target";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE, "fromPrivate", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_STATIC, "fromDefault", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "fromProtected", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "alreadyPublic", "I", null, null));
        node.fields.add(new FieldNode(0, "alreadyDefault", "I", null, null));
        rules.apply(node);
        assertEquals(Opcodes.ACC_VOLATILE, node.fields.get(0).access);
        assertEquals(Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC, node.fields.get(1).access);
        assertEquals(Opcodes.ACC_PUBLIC, node.fields.get(2).access);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, node.fields.get(3).access);
        assertEquals(0, node.fields.get(4).access);
    }

    @Test void mergesModsWithoutVisibilityReductionAndUsesLegacyForgeFinalOrdering() throws Exception {
        var mappings = mappings();
        var rules = new AccessTransformerRules();
        rules.read(config("first.jar", "public-f " + SOURCE + " field_1"), List.of("test_at.cfg"), mappings);
        rules.read(config("second.jar", "private+f " + SOURCE + " field_1\ndefault " + SOURCE + " field_1"),
            List.of("test_at.cfg"), mappings);
        ClassNode node = fixture();
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, node.fields.get(0).access);
        rules.read(config("third.jar", "protected-f " + SOURCE + " field_1"), List.of("test_at.cfg"), mappings);
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, node.fields.get(0).access);
    }

    @Test void supportsIndependentFieldAndMethodWildcardsIncludingConstructors() throws Exception {
        AccessTransformerRules rules = rules("public-f " + SOURCE + " *\nprotected " + SOURCE + " *()\n");
        ClassNode node = fixture();
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, node.fields.get(0).access);
        assertEquals(Opcodes.ACC_PUBLIC, node.fields.get(1).access);
        assertEquals(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, node.methods.get(0).access);
        assertEquals(Opcodes.ACC_PROTECTED, node.methods.get(2).access);
        assertEquals(Opcodes.ACC_PUBLIC, node.methods.get(3).access);
        assertEquals(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, node.access);
        rules("public " + SOURCE + " *()V").apply(node);
        assertTrue(node.methods.stream().allMatch(method -> (method.access & Opcodes.ACC_PUBLIC) != 0));
    }

    @Test void updatesNestedClassMetadataOnBothTheTargetAndItsMappedOuter() throws Exception {
        AccessTransformerRules rules = rules("public-f net.minecraft.test.Owner$Inner");
        assertTrue(rules.targets(RUNTIME));
        assertTrue(rules.targets("net/minecraft/class_101"));
        ClassNode outer = fixture();
        rules.apply(outer);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, outer.innerClasses.get(0).access);
        assertEquals(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, outer.access);
        ClassNode inner = new ClassNode();
        inner.name = "net/minecraft/class_101";
        inner.access = Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        inner.innerClasses.add(new InnerClassNode(inner.name, RUNTIME, "Inner",
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));
        rules.apply(inner);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, inner.access);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, inner.innerClasses.get(0).access);
    }

    @Test void rewritesOnlyWidenedPrivateInstanceCallsOwnedByTheCurrentClass() throws Exception {
        ClassNode node = fixture();
        rules("public " + SOURCE + " func_1(Lnet/minecraft/test/Owner;)Lnet/minecraft/test/Owner;\n"
            + "public " + SOURCE + " <init>()V").apply(node);
        MethodInsnNode self = (MethodInsnNode) node.methods.get(3).instructions.get(2);
        MethodInsnNode parent = (MethodInsnNode) node.methods.get(3).instructions.get(6);
        MethodInsnNode constructor = (MethodInsnNode) node.methods.get(2).instructions.get(1);
        assertEquals(Opcodes.INVOKEVIRTUAL, self.getOpcode());
        assertFalse(self.itf);
        assertEquals(Opcodes.INVOKESPECIAL, parent.getOpcode());
        assertEquals(Opcodes.INVOKESPECIAL, constructor.getOpcode());
    }

    @Test void preservesDefaultPackagePrivateAndPublicVisibilityOnClassRules() throws Exception {
        ClassNode node = fixture();
        rules("protected " + SOURCE + "\nprivate " + SOURCE + "\ndefault " + SOURCE).apply(node);
        assertEquals(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, node.access);
        rules("public-f " + SOURCE).apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, node.access);
    }

    @Test void acceptsCommentsBomTabsLegacyLineEndingsAndRootForgeConfigurations() throws Exception {
        Path path = jar("format.jar", Map.of("forge_at.cfg",
            ("\uFEFF# header\r\r\n\tpublic-f\t" + SOURCE + "\tfield_1 # comment\r\n").getBytes(StandardCharsets.UTF_8)));
        var rules = new AccessTransformerRules();
        rules.read(path, List.of("forge_at.cfg", "forge_at.cfg"), mappings());
        ClassNode node = fixture();
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, node.fields.get(0).access);
    }

    @Test void rejectsUnsupportedSyntaxAndMalformedDescriptorsWithFileAndLineDiagnostics() throws Exception {
        for (String rule : List.of(
                "public", "public example.Target field extra", "PUBLIC example.Target", "public-x example.Target",
                "public example.*", "public example.Target field*", "public example.Target get*()V",
                "public example.Target field:I", "public example.Target invalid()Q", "public example.Target invalid([V)V",
                "public example.Target invalid(V)V", "public example.Target invalid(Ljava..lang.String;)V",
                "public example.Target invalid(L;)V", "public example.Target invalid()", "public example.Target invalid()Vextra",
                "public example.Target <init>()I", "public example.Target <clinit>(I)V")) {
            IOException error = assertThrows(IOException.class, () -> rules("# before\n" + rule), rule);
            assertTrue(error.getMessage().contains("rules.jar!test_at.cfg:2:"), error.getMessage());
        }
        assertThrows(IOException.class, () -> rules("public example.Target excessive(" + "J".repeat(128) + ")V"));
        assertThrows(IOException.class, () -> rules("public example.Target excessive(" + "[".repeat(256) + "I)V"));
    }

    @Test void acceptsTheOriginalForgeAccessTransformersIncludingDottedMethodDescriptors() throws Exception {
        Path runtime = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Assumptions.assumeTrue(Files.isRegularFile(runtime));
        var rules = new AccessTransformerRules();
        try (ZipFile zip = new ZipFile(runtime.toFile())) {
            rules.read(runtime, zip.stream().filter(entry -> entry.getName().endsWith("_at.cfg"))
                    .map(ZipEntry::getName).toList(), mappings());
        }
        assertTrue(rules.targets("net/minecraft/block/Block"));
        assertTrue(rules.targets("net/minecraft/client/Minecraft"));
    }

    @Test void failedReadDoesNotPublishPartiallyCollectedRules() throws Exception {
        var mappings = mappings();
        var rules = rules("public " + SOURCE + " field_1");
        Path invalid = config("invalid.jar", "public example.NewClass\ninvalid example.NewClass\n");
        assertThrows(IOException.class, () -> rules.read(invalid, List.of("test_at.cfg"), mappings));
        assertFalse(rules.targets("example/NewClass"));
        assertTrue(rules.targets(RUNTIME));
        ClassNode node = fixture();
        rules.apply(node);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, node.fields.get(0).access);
    }

    @Test void rejectsMissingUnsafeOversizedAndInvalidUtf8Configurations() throws Exception {
        var mappings = mappings();
        Path empty = jar("empty.jar", Map.of());
        for (String name : List.of("missing.cfg", "../escape.cfg", "/absolute.cfg", "META-INF/../escape.cfg", "C:/escape.cfg", "bad\\file.cfg")) {
            assertThrows(IOException.class, () -> new AccessTransformerRules().read(empty, List.of(name), mappings));
        }
        Path oversized = jar("large.jar", Map.of("META-INF/test_at.cfg", new byte[1024 * 1024 + 1]));
        assertTrue(assertThrows(IOException.class,
            () -> new AccessTransformerRules().read(oversized, List.of("test_at.cfg"), mappings)).getMessage().contains("1 MiB"));
        Path invalidUtf8 = jar("encoding.jar", Map.of("META-INF/test_at.cfg", new byte[]{(byte) 0xC3, 0x28}));
        assertTrue(assertThrows(IOException.class,
            () -> new AccessTransformerRules().read(invalidUtf8, List.of("test_at.cfg"), mappings)).getMessage().contains("UTF-8"));
        assertThrows(IOException.class, () -> rules("#".repeat(16 * 1024 + 1)));
    }

    @Test void appliesTheProvidedJeiAndNeiAccessTransformersToSyntheticClassesWithoutLoadingThem() throws Exception {
        Path samples = ForgeTestFixtures.optionalGuestDirectory();
        Path srg = Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg");
        Path cache = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        Path tiny = cache.resolve("calamus-gen2-intermediary-v2.tiny");
        Path vanilla = cache.resolve("minecraft-merged.jar");
        Assumptions.assumeTrue(Files.isDirectory(samples) && Files.isRegularFile(srg)
            && Files.isRegularFile(tiny) && Files.isRegularFile(vanilla));
        MappingResolver resolver = resolver(Files.readString(tiny));
        ForgeMappings mappings;
        try (var input = Files.newBufferedReader(srg)) {
            mappings = new ForgeMappings(resolver, input);
        }
        var rules = new AccessTransformerRules();
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        Map<String, ClassNode> declarations = new LinkedHashMap<>();
        int configurations = 0;
        int directives = 0;
        try (ZipFile game = new ZipFile(vanilla.toFile()); var files = Files.list(samples)) {
            for (Path path : files.filter(file -> file.getFileName().toString().startsWith("jei_")
                    || file.getFileName().toString().startsWith("NotEnoughItems-")).toList()) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    Manifest manifest;
                    try (var input = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                        manifest = new Manifest(input);
                    }
                    List<String> configs = List.of(manifest.getMainAttributes().getValue("FMLAT").split("\\s+"));
                    rules.read(path, configs, mappings);
                    for (String config : configs) {
                        configurations++;
                        String text;
                        try (var input = zip.getInputStream(zip.getEntry("META-INF/" + config))) {
                            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        for (String line : text.split("\\R")) {
                            line = line.split("#", 2)[0].trim();
                            if (line.isEmpty()) continue;
                            directives++;
                            String[] parts = line.split("\\s+");
                            assertEquals("public", parts[0]);
                            String owner = parts[1].replace('.', '/');
                            String runtime = mappings.classFromSrg(owner);
                            assertNotEquals(owner, runtime);
                            ClassNode declaration = declarations.get(runtime);
                            if (declaration == null) {
                                String official = resolver.unmapClassName("official", runtime.replace('/', '.')).replace('.', '/');
                                declaration = new ClassNode();
                                try (var input = game.getInputStream(game.getEntry(official + ".class"))) {
                                    new ClassReader(input.readAllBytes()).accept(new ClassRemapper(declaration,
                                        mappings.officialToRuntime()), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                                }
                                declarations.put(runtime, declaration);
                            }
                            ClassNode node = nodes.computeIfAbsent(runtime, name -> {
                                ClassNode created = new ClassNode();
                                created.name = name;
                                return created;
                            });
                            int parenthesis = parts[2].indexOf('(');
                            if (parenthesis < 0) {
                                var actual = declaration.fields.stream().filter(field -> mappings.runtimeToSrg()
                                    .mapFieldName(runtime, field.name, field.desc).equals(parts[2])).findFirst().orElseThrow();
                                if (node.fields.stream().noneMatch(field -> field.name.equals(actual.name))) {
                                    node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                                        actual.name, actual.desc, null, null));
                                }
                            } else {
                                String name = parts[2].substring(0, parenthesis);
                                String descriptor = parts[2].substring(parenthesis);
                                var actual = declaration.methods.stream().filter(method -> mappings.runtimeToSrg()
                                    .mapMethodName(runtime, method.name, method.desc).equals(name)
                                    && mappings.runtimeToSrg().mapMethodDesc(method.desc).equals(descriptor)).findFirst().orElseThrow();
                                node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, actual.name, actual.desc, null, null));
                            }
                        }
                    }
                }
            }
        }
        assertEquals(2, configurations);
        assertEquals(19, directives);
        assertEquals(9, nodes.size());
        assertEquals(14, nodes.values().stream().mapToInt(node -> node.fields.size()).sum());
        assertEquals(1, nodes.values().stream().mapToInt(node -> node.methods.size()).sum());
        for (ClassNode node : nodes.values()) {
            assertTrue(rules.targets(node.name));
            rules.apply(node);
            assertTrue(node.fields.stream().allMatch(field -> field.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)));
            assertTrue(node.methods.stream().allMatch(method -> method.access == Opcodes.ACC_PUBLIC));
        }
    }
}
