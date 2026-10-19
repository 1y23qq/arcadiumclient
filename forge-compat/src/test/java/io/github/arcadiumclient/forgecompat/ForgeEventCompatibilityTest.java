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

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeEventCompatibilityTest {
    private static final String TRANSFORMER = "net/minecraftforge/fml/common/asm/transformers/EventSubscriptionTransformer";
    @TempDir Path temporary;

    @Test void preservesTheListenerDescriptorAndEventGenerationWhileClassifyingParentsFromMetadata() throws Exception {
        ClassNode node = read(TRANSFORMER);
        MethodNode build = build(node);
        List<MethodInsnNode> getTypes = Arrays.stream(build.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals("org/objectweb/asm/Type") && call.name.equals("getType")).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, getTypes.size());
        MethodInsnNode descriptor = getTypes.get(0), superclass = getTypes.get(1);
        assertInstanceOf(LdcInsnNode.class, previous(descriptor));
        assertEquals("Lnet/minecraftforge/fml/common/eventhandler/ListenerList;", ((LdcInsnNode) previous(descriptor)).cst);
        assertEquals("superName", ((FieldInsnNode) previous(superclass)).name);
        List<AbstractInsnNode> originalBody = List.of(build.instructions.toArray()).subList(
            Arrays.asList(build.instructions.toArray()).indexOf(superclass.getPrevious()), build.instructions.size());
        new ForgeRuntimePatches().accept(node);
        assertEquals("getType", descriptor.name); assertEquals("getObjectType", superclass.name);
        assertTrue(List.of(build.instructions.toArray()).containsAll(originalBody));
        assertEquals(1, Arrays.stream(build.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals("io/github/arcadiumclient/forgecompat/ForgeEventMetadata") && call.name.equals("isEventSuperclass")).count());
        byte[] once = bytes(node); new ForgeRuntimePatches().accept(node); assertArrayEquals(once, bytes(node));
        for (MethodNode method : node.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
            new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
    }

    @Test void failsClearlyIfTheTrustedSuperclassOperandShapeChanges() throws Exception {
        ClassNode node = read(TRANSFORMER);
        for (AbstractInsnNode instruction : build(node).instructions) if (instruction instanceof FieldInsnNode field && field.name.equals("superName")) field.name = "unexpected";
        byte[] before = bytes(node);
        var failure = assertThrows(IllegalStateException.class, () -> new ForgeRuntimePatches().accept(node));
        assertTrue(failure.getMessage().contains("superclass type conversion is missing")); assertArrayEquals(before, bytes(node));
    }

    @Test void reproducesTheRealAsm9FailureAndBuildsTheActualWorldEventAfterTheOneCallPatch() throws Exception {
        Path forge = forge();
        for (boolean patched : List.of(false, true)) {
            try (URLClassLoader target = new URLClassLoader(new URL[]{forge.toUri().toURL()}, getClass().getClassLoader()) {
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (!patched || !name.equals(TRANSFORMER.replace('/', '.'))) return super.findClass(name);
                    try {
                        ClassNode node = read(TRANSFORMER); new ForgeRuntimePatches().accept(node);
                        byte[] bytes = bytes(node); return defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                }
            }) {
                Class<?> transformer = target.loadClass(TRANSFORMER.replace('/', '.'));
                Object instance = transformer.getConstructor().newInstance();
                var build = transformer.getDeclaredMethod("buildEvents", ClassNode.class); build.setAccessible(true);
                ClassNode event = read("net/minecraftforge/event/world/WorldEvent");
                if (!patched) {
                    var failure = assertThrows(InvocationTargetException.class, () -> build.invoke(instance, event));
                    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
                    assertTrue(failure.getCause().getMessage().contains("Invalid descriptor"));
                } else {
                    assertEquals(true, build.invoke(instance, event));
                    assertTrue(event.fields.stream().anyMatch(field -> field.name.equals("LISTENER_LIST") && field.desc.equals("Lnet/minecraftforge/fml/common/eventhandler/ListenerList;")));
                    assertTrue(event.methods.stream().anyMatch(method -> method.name.equals("<init>") && method.desc.equals("()V")));
                    assertTrue(event.methods.stream().anyMatch(method -> method.name.equals("getListenerList")));
                    ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_FRAMES); event.accept(output);
                    ClassNode serialized = new ClassNode(); new ClassReader(output.toByteArray()).accept(serialized, 0);
                    for (MethodNode method : serialized.methods) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method);
                }
            }
        }
    }

    @Test void initializesTheRealEventHierarchyAndPublicEventBusOnJava25() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path metadata = root.resolve("build/forge-compat/fml-runtime-review/trusted-classpath.json");
        Assumptions.assumeTrue(Files.isRegularFile(metadata));
        LinkedHashSet<Path> classpath = new LinkedHashSet<>();
        classpath.add(root.resolve("forge-compat/build/resources/main"));
        Path lzma = root.resolve("build/forge-compat/dependencies/lzma-0.0.1.jar");
        Assumptions.assumeTrue(Files.isRegularFile(lzma)); classpath.add(lzma);
        for (Class<?> type : List.of(ForgeEventRuntimeProbe.class, ForgeRuntimePatches.class, MappingResolver.class,
                ClassReader.class, ClassNode.class, Remapper.class, JsonParser.class,
                org.objectweb.asm.tree.analysis.AnalyzerException.class)) {
            classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        for (var element : new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path path = Path.of(element.getAsString()); if (!Files.isRegularFile(path)) continue;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry("ave.class") != null || zip.getEntry("net/minecraft/client/main/Main.class") != null
                        || zip.getEntry("fabric.mod.json") != null || zip.getEntry("mcmod.info") != null
                        || path.getFileName().toString().startsWith("asm-") || path.getFileName().toString().contains("Arcadium")) continue;
                classpath.add(path);
            }
        }
        Path evidence = root.resolve("build/forge-compat/event-asm9-tests"); Files.createDirectories(evidence);
        Path output = temporary.resolve("runtime");
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Djava.awt.headless=true",
            "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp", String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()),
            ForgeEventRuntimeProbe.class.getName(), root.toString(), output.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(evidence.resolve("probe.log").toFile());
        for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        Process process = builder.start(); boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        String log = Files.readString(evidence.resolve("probe.log"));
        List<String> stages = log.lines().filter(line -> line.contains("ARCADIUM_EVENT_PROBE ")).map(line -> line.substring(line.indexOf("ARCADIUM_EVENT_PROBE "))).toList();
        Files.write(evidence.resolve("stages.txt"), stages);
        assertTrue(finished, "Real event probe timed out: " + stages);
        assertEquals(0, process.exitValue(), "Real event probe failed: " + stages);
        assertTrue(stages.contains("ARCADIUM_EVENT_PROBE SUCCESS"));
        assertFalse(log.contains("Invalid descriptor"), "Real event transformation still reports an invalid descriptor");
        Files.copy(output.resolve("event-result.json"), evidence.resolve("probe-result.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path forge() { Path path = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
    private static ClassNode read(String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(forge().toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static MethodNode build(ClassNode node) { return node.methods.stream().filter(method -> method.name.equals("buildEvents")).findFirst().orElseThrow(); }
    private static AbstractInsnNode previous(AbstractInsnNode node) { do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
}
