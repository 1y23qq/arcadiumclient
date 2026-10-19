package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class LegacyJavaVersionTest {
    private static final String JAVA_VERSION = "org.apache.commons.lang3.JavaVersion";
    private static final String SYSTEM_UTILS = "org.apache.commons.lang3.SystemUtils";
    private static final String BLAMING = "net.minecraftforge.fml.common.asm.transformers.BlamingTransformer";
    private static final String HELPER = "io/github/arcadiumclient/forgecompat/LegacyJavaVersion";
    @TempDir Path temporary;

    @Test void preservesAllEightyOneActualCommonsLang332ComparisonsIncludingJava09() throws Exception {
        try (Loader loader = new Loader(Map.of())) {
            Class<?> type = loader.loadClass(JAVA_VERSION); Object[] versions = (Object[]) type.getMethod("values").invoke(null);
            assertEquals(9, versions.length); int comparisons = 0;
            for (Object current : versions) for (Object required : versions) {
                boolean original = (boolean) type.getMethod("atLeast", type).invoke(current, required);
                assertEquals(original, LegacyJavaVersion.atLeast(current.toString(), (Enum<?>) required), current + ">=" + required);
                comparisons++;
            }
            assertEquals(81, comparisons);
            assertTrue(LegacyJavaVersion.atLeast("1.5", (Enum<?>) type.getField("JAVA_0_9").get(null)));
            assertFalse(LegacyJavaVersion.atLeast("1.4", (Enum<?>) type.getField("JAVA_0_9").get(null)));
        }
    }

    @Test void comparesTheActualModernVmWithoutChangingGlobalVersionOrCommonsLangState() throws Exception {
        Assumptions.assumeTrue(Runtime.version().feature() >= 9);
        String before = System.getProperty("java.specification.version");
        try (Loader loader = new Loader(Map.of())) {
            Class<?> type = loader.loadClass(JAVA_VERSION), utils = loader.loadClass(SYSTEM_UTILS);
            Enum<?> java8 = (Enum<?>) type.getField("JAVA_1_8").get(null);
            InvocationTargetException original = assertThrows(InvocationTargetException.class, () -> utils.getMethod("isJavaVersionAtLeast", type).invoke(null, java8));
            assertInstanceOf(NullPointerException.class, original.getCause());
            var cached = utils.getDeclaredField("JAVA_SPECIFICATION_VERSION_AS_ENUM"); cached.setAccessible(true); assertNull(cached.get(null));
            for (Object required : (Object[]) type.getMethod("values").invoke(null)) assertTrue(LegacyJavaVersion.isJavaVersionAtLeast((Enum<?>) required));
            assertFalse(LegacyJavaVersion.atLeast("7", java8)); assertTrue(LegacyJavaVersion.atLeast("8", java8));
            for (String current : List.of("9", "11", "17", "25", "26")) assertTrue(LegacyJavaVersion.atLeast(current, java8));
            assertEquals(before, System.getProperty("java.specification.version")); assertNull(cached.get(null));
        }
    }

    @Test void rewritesOnlyTheExactObsoleteStaticCallForOfficialAndGuestInputsAndRunsItOnJvm() throws Exception {
        ClassNode query = new ClassNode(); query.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/VersionQuery", null, "java/lang/Object", null);
        MethodNode check = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "check", "(Lorg/apache/commons/lang3/JavaVersion;)Z", null, null);
        check.visitVarInsn(Opcodes.ALOAD, 0); check.visitMethodInsn(Opcodes.INVOKESTATIC, SYSTEM_UTILS.replace('.', '/'), "isJavaVersionAtLeast", check.desc, false); check.visitInsn(Opcodes.IRETURN); query.methods.add(check);
        MethodNode untouched = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "home", "()Ljava/io/File;", null, null);
        untouched.visitLdcInsn(SYSTEM_UTILS); untouched.visitInsn(Opcodes.POP); untouched.visitMethodInsn(Opcodes.INVOKESTATIC, SYSTEM_UTILS.replace('.', '/'), "getJavaHome", "()Ljava/io/File;", false); untouched.visitInsn(Opcodes.ARETURN); query.methods.add(untouched);
        Path source = jar("query.jar", Map.of(query.name + ".class", bytes(query))); byte[] original = Files.readAllBytes(source);
        for (boolean official : List.of(true, false)) {
            Path transformed = new ForgeJarTransformer().transform(source, temporary.resolve("cache"), mappings(), official, null);
            byte[] result = entries(transformed).get(query.name + ".class"); ClassNode output = node(result);
            List<MethodInsnNode> calls = output.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray())).filter(i -> i instanceof MethodInsnNode).map(MethodInsnNode.class::cast).toList();
            assertEquals(2, calls.size());
            assertEquals(HELPER, calls.get(0).owner); assertEquals("(Ljava/lang/Enum;)Z", calls.get(0).desc); assertEquals(Opcodes.INVOKESTATIC, calls.get(0).getOpcode());
            assertEquals(SYSTEM_UTILS.replace('.', '/'), calls.get(1).owner); assertEquals("getJavaHome", calls.get(1).name);
            assertEquals(SYSTEM_UTILS, ((LdcInsnNode) output.methods.get(1).instructions.getFirst()).cst);
            for (MethodNode method : output.methods) new Analyzer<>(new BasicVerifier()).analyze(output.name, method);
            try (Loader loader = new Loader(Map.of(query.name.replace('/', '.'), result))) {
                Class<?> type = loader.loadClass(JAVA_VERSION), caller = loader.loadClass(query.name.replace('/', '.'));
                for (Object value : (Object[]) type.getMethod("values").invoke(null)) assertEquals(true, caller.getMethod("check", type).invoke(null, value));
            }
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void executesTheActualForgeBlamingTransformerOnJava25WithoutSuppressingItsVisitor() throws Exception {
        Assumptions.assumeTrue(Runtime.version().feature() >= 9);
        Path forge = Path.of("..").toAbsolutePath().normalize().resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Assumptions.assumeTrue(Files.isRegularFile(forge));
        Map<String, byte[]> source = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(forge.toFile())) {
            for (String name : List.of(BLAMING, BLAMING + "$VersionVisitor")) {
                String entry = name.replace('.', '/') + ".class"; try (var input = zip.getInputStream(zip.getEntry(entry))) { source.put(entry, input.readAllBytes()); }
            }
        }
        Map<String, byte[]> rawClasses = definitions(source);
        byte[] java8 = fixtureClass(Opcodes.V1_8, "fixture/Java8Consumer");
        try (Loader loader = new Loader(rawClasses)) {
            Object transformer = loader.loadClass(BLAMING).getConstructor().newInstance();
            InvocationTargetException original = assertThrows(InvocationTargetException.class, () -> transformer.getClass().getMethod("transform", String.class, String.class, byte[].class).invoke(transformer, "fixture.Java8Consumer", "fixture.Java8Consumer", java8));
            assertInstanceOf(NullPointerException.class, original.getCause());
        }
        Path input = jar("blaming.jar", source);
        Path transformed = new ForgeJarTransformer().transform(input, temporary.resolve("forge-cache"), mappings(), true, null);
        Map<String, byte[]> converted = entries(transformed);
        ClassNode visitor = node(converted.get((BLAMING + "$VersionVisitor").replace('.', '/') + ".class"));
        long replacements = visitor.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray())).filter(i -> i instanceof MethodInsnNode call && call.owner.equals(HELPER)).count();
        assertEquals(2, replacements);
        for (byte[] value : converted.values()) for (MethodNode method : node(value).methods) new Analyzer<>(new BasicVerifier()).analyze(node(value).name, method);
        try (Loader loader = new Loader(definitions(converted))) {
            Class<?> type = loader.loadClass(BLAMING); Object transformer = type.getConstructor().newInstance();
            MethodNode originalTransform = node(source.get(BLAMING.replace('.', '/') + ".class")).methods.stream().filter(m -> m.name.equals("transform")).findFirst().orElseThrow();
            MethodNode convertedTransform = node(converted.get(BLAMING.replace('.', '/') + ".class")).methods.stream().filter(m -> m.name.equals("transform")).findFirst().orElseThrow();
            assertEquals(instructions(originalTransform), instructions(convertedTransform));
            for (int version : new int[]{Opcodes.V1_6, Opcodes.V1_7, Opcodes.V1_8, Opcodes.V25}) {
                byte[] fixture = fixtureClass(version, "fixture/Version" + version);
                assertSame(fixture, type.getMethod("transform", String.class, String.class, byte[].class).invoke(transformer, "fixture.Version" + version, "fixture.Version" + version, fixture));
            }
            var bad = type.getDeclaredField("orphanNaughtyClasses"); bad.setAccessible(true); assertTrue(((java.util.Set<?>) bad.get(null)).isEmpty());
        }
    }

    @Test void rejectsNullAndUnrelatedEnumsInsteadOfTurningVersionChecksIntoUnconditionalSuccess() {
        assertThrows(NullPointerException.class, () -> LegacyJavaVersion.isJavaVersionAtLeast(null));
        assertThrows(IllegalArgumentException.class, () -> LegacyJavaVersion.isJavaVersionAtLeast(Thread.State.RUNNABLE));
    }

    private static final class Loader extends URLClassLoader {
        private final Map<String, byte[]> definitions;
        Loader(Map<String, byte[]> definitions) throws Exception { super(new URL[]{lang().toUri().toURL(), guava().toUri().toURL()}, LegacyJavaVersionTest.class.getClassLoader()); this.definitions = definitions; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    byte[] bytes = definitions.get(name);
                    if (bytes != null) type = defineClass(name, bytes, 0, bytes.length);
                    else if (name.startsWith("org.apache.commons.lang3.") || name.startsWith("com.google.common.")) type = findClass(name);
                    else type = super.loadClass(name, false);
                }
                if (resolve) resolveClass(type); return type;
            }
        }
    }
    private static Path lang() { Path jar = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/org.apache.commons/commons-lang3/3.3.2/90a3822c38ec8c996e84c16a3477ef632cbc87a3/commons-lang3-3.3.2.jar"); Assumptions.assumeTrue(Files.isRegularFile(jar)); return jar; }
    private static Path guava() { Path jar = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/com.google.guava/guava/17.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/guava-17.0.jar"); Assumptions.assumeTrue(Files.isRegularFile(jar)); return jar; }
    private static ForgeMappings mappings() throws Exception {
        return new ForgeMappings(new MappingResolver() {
            @Override public List<String> getNamespaces() { return List.of("official", "intermediary"); }
            @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
            @Override public String mapClassName(String namespace, String name) { return name; }
            @Override public String unmapClassName(String namespace, String name) { return name; }
            @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
            @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
        }, new StringReader("CL: fixture/Placeholder net/minecraft/Placeholder\n"));
    }
    private Path jar(String name, Map<String, byte[]> files) throws Exception { Path file = temporary.resolve(name); try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) { for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } } return file; }
    private static Map<String, byte[]> entries(Path file) throws Exception { Map<String, byte[]> result = new LinkedHashMap<>(); try (ZipFile zip = new ZipFile(file.toFile())) { for (var item : java.util.Collections.list(zip.entries())) try (var input = zip.getInputStream(item)) { result.put(item.getName(), input.readAllBytes()); } } return result; }
    private static Map<String, byte[]> definitions(Map<String, byte[]> entries) { Map<String, byte[]> result = new LinkedHashMap<>(); entries.forEach((name, bytes) -> { if (name.endsWith(".class")) result.put(name.substring(0, name.length() - 6).replace('/', '.'), bytes); }); return result; }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] fixtureClass(int version, String name) { ClassNode node = new ClassNode(); node.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null); return bytes(node); }
    private static List<String> instructions(MethodNode method) { List<String> result = new ArrayList<>(); for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction.getOpcode() + (instruction instanceof MethodInsnNode call ? " " + call.owner + "." + call.name + call.desc : "")); return result; }
}
