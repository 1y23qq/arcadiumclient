package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeJava25ReflectionTest {
    private static final String HELPER = "io/github/arcadiumclient/forgecompat/ForgeJava25Reflection";
    @TempDir Path temporary;
    private enum Sample { FIRST; private final int marker; Sample() { marker = 17; } }
    private static final class Fields { static final Object FINAL = new Object(); }

    @Test void preservesTheOriginalHolderRegistryLookupNbtAndExceptionHandling() throws Exception {
        for (String simple : List.of("ObjectHolderRef", "ItemStackHolderRef")) {
            ClassNode node = read("net/minecraftforge/fml/common/registry/" + simple);
            MethodNode apply = method(node, "apply");
            var registryCalls = calls(apply).stream().filter(call -> call.owner.startsWith("net/minecraftforge/")).toList();
            var handlers = List.copyOf(apply.tryCatchBlocks);
            int constructors = node.methods.stream().filter(value -> value.name.equals("<init>")).mapToInt(value -> value.instructions.size()).sum();
            new ForgeRuntimePatches().accept(node);
            assertEquals(registryCalls, calls(apply).stream().filter(call -> call.owner.startsWith("net/minecraftforge/")).toList());
            assertEquals(handlers, apply.tryCatchBlocks);
            assertEquals(constructors, node.methods.stream().filter(value -> value.name.equals("<init>")).mapToInt(value -> value.instructions.size()).sum());
            assertEquals(1, calls(apply).stream().filter(call -> call.owner.equals(HELPER) && call.name.equals("writeHolder")).count());
            assertEquals(0, calls(apply).stream().filter(call -> call.owner.equals("java/lang/reflect/Method") && call.name.equals("invoke")).count());
            assertEquals(1, calls(method(node, "makeWritable")).stream().filter(call -> call.owner.equals(HELPER) && call.name.equals("prepareHolder")).count());
            verify(node); byte[] once = bytes(node); new ForgeRuntimePatches().accept(node); assertArrayEquals(once, bytes(node));
        }
    }

    @Test void rejectsAnUnexpectedHolderWriteShapeBeforeReplacingItsPreparation() throws Exception {
        ClassNode node = read("net/minecraftforge/fml/common/registry/ObjectHolderRef");
        for (AbstractInsnNode instruction : method(node, "apply").instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("fieldAccessorSet")) field.name = "unexpectedAccessor";
        byte[] before = bytes(node);
        assertThrows(IllegalStateException.class, () -> new ForgeRuntimePatches().accept(node));
        assertArrayEquals(before, bytes(node));
    }

    @Test void keepsTheRealEnumAppendLookupAndConvenienceMethods() throws Exception {
        ClassNode node = read("net/minecraftforge/common/util/EnumHelper");
        Map<String, byte[]> original = new HashMap<>();
        for (MethodNode method : node.methods) if (method.name.startsWith("add")) original.put(method.name + method.desc, methodBytes(method));
        new ForgeRuntimePatches().accept(node);
        for (MethodNode method : node.methods) if (method.name.startsWith("add")) assertArrayEquals(original.get(method.name + method.desc), methodBytes(method));
        for (String name : List.of("setup", "makeEnum", "setFailsafeFieldValue", "cleanEnumCache")) {
            assertTrue(calls(method(node, name)).stream().anyMatch(call -> call.owner.equals(HELPER)));
            assertFalse(calls(method(node, name)).stream().anyMatch(call -> call.owner.equals("java/lang/reflect/Field") && call.name.equals("getDeclaredField")));
        }
        verify(node); byte[] once = bytes(node); new ForgeRuntimePatches().accept(node); assertArrayEquals(once, bytes(node));
    }

    @Test void invokesAnActualEnumConstructorWithoutSkippingItsBody() throws Exception {
        Sample added = (Sample) ForgeJava25Reflection.makeEnum(Sample.class, "SECOND", 1, new Class<?>[0], new Object[0]);
        assertEquals("SECOND", added.name()); assertEquals(1, added.ordinal()); assertEquals(17, added.marker);
    }

    @Test void rejectsWrongEnumArgumentsAndRequiresTheRealPublicApiForGenericWrites() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ForgeJava25Reflection.makeEnum(String.class, "BAD", 0, new Class<?>[0], new Object[0]));
        assertThrows(IllegalArgumentException.class, () -> ForgeJava25Reflection.makeEnum(Sample.class, "BAD", 0, new Class<?>[]{int.class}, new Object[0]));
        assertThrows(NoSuchMethodException.class, () -> ForgeJava25Reflection.makeEnum(Sample.class, "BAD", 0, new Class<?>[]{int.class}, new Object[]{1}));
        Object original = Fields.FINAL;
        assertThrows(IllegalStateException.class, () -> ForgeJava25Reflection.setFailsafeFieldValue(Fields.class.getDeclaredField("FINAL"), null, new Object()));
        assertSame(original, Fields.FINAL);
        Object value = new Object();
        assertThrows(IllegalStateException.class, () -> ForgeJava25Reflection.prepareHolder(Fields.class.getDeclaredField("FINAL")));
        assertThrows(IllegalStateException.class, () -> ForgeJava25Reflection.writeHolder(Fields.class.getDeclaredField("FINAL"), value));
    }

    @Test void runsTheRealFmlHoldersAndMinecraftEnumExtensionOnJava25() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path metadata = root.resolve("build/forge-compat/fml-runtime-review/trusted-classpath.json");
        Assumptions.assumeTrue(Files.isRegularFile(metadata));
        LinkedHashSet<Path> classpath = new LinkedHashSet<>(); classpath.add(root.resolve("forge-compat/build/resources/main"));
        Path lzma = root.resolve("build/forge-compat/dependencies/lzma-0.0.1.jar"); Assumptions.assumeTrue(Files.isRegularFile(lzma)); classpath.add(lzma);
        for (Class<?> type : List.of(ForgeJava25ReflectionRuntimeProbe.class, ForgeRuntimePatches.class, MappingResolver.class,
                ClassReader.class, ClassNode.class, Remapper.class, JsonParser.class,
                org.objectweb.asm.tree.analysis.AnalyzerException.class)) classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (var element : new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path path = Path.of(element.getAsString()); if (!Files.isRegularFile(path)) continue;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry("ave.class") != null || zip.getEntry("net/minecraft/client/main/Main.class") != null || zip.getEntry("fabric.mod.json") != null
                        || zip.getEntry("mcmod.info") != null || path.getFileName().toString().startsWith("asm-") || path.getFileName().toString().contains("Arcadium")) continue;
                classpath.add(path);
            }
        }
        Path evidence = root.resolve("build/forge-compat/java25-reflection-tests"); Files.createDirectories(evidence);
        Path output = temporary.resolve("runtime");
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true",
            "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()),
            ForgeJava25ReflectionRuntimeProbe.class.getName(), root.toString(), output.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(evidence.resolve("probe.log").toFile());
        for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        Process process = builder.start(); boolean finished = process.waitFor(90, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        String log = Files.readString(evidence.resolve("probe.log"));
        List<String> stages = log.lines().filter(line -> line.contains("ARCADIUM_REFLECTION_PROBE ")).map(line -> line.substring(line.indexOf("ARCADIUM_REFLECTION_PROBE "))).toList();
        Files.write(evidence.resolve("stages.txt"), stages);
        assertTrue(finished, "Real holder/enum probe timed out: " + stages);
        assertEquals(0, process.exitValue(), "Real holder/enum probe failed: " + stages);
        assertTrue(stages.contains("ARCADIUM_REFLECTION_PROBE SUCCESS"));
        Files.copy(output.resolve("reflection-result.json"), evidence.resolve("probe-result.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static ClassNode read(String name) throws Exception {
        Path path = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"); Assumptions.assumeTrue(Files.isRegularFile(path));
        ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(path.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow(); }
    private static List<MethodInsnNode> calls(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(MethodNode method) { ClassNode node = new ClassNode(); node.version = 52; node.name = "fixture/Method"; node.superName = "java/lang/Object"; node.methods.add(method); return bytes(node); }
    private static void verify(ClassNode node) throws Exception { ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0); for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method); }
}
