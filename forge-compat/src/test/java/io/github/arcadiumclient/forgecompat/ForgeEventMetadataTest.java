package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ForgeEventMetadataTest {
    private static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String TRANSFORMER = "net/minecraftforge/fml/common/asm/transformers/EventSubscriptionTransformer";
    private static final String TICK = "net/minecraftforge/fml/common/gameevent/TickEvent";

    @Test void classifiesActualAndGuestEventAncestryWithoutLoadingAnyParent() throws Exception {
        Map<String, byte[]> resources = new HashMap<>();
        try (ZipFile zip = new ZipFile(forge().toFile())) {
            for (String name : List.of(TICK, "net/minecraftforge/event/world/WorldEvent", "net/minecraftforge/client/event/GuiScreenEvent", "net/minecraftforge/client/event/GuiScreenEvent$DrawScreenEvent"))
                try (var input = zip.getInputStream(zip.getEntry(name + ".class"))) { resources.put(name + ".class", input.readAllBytes()); }
        }
        resources.put("fixture/GuestParent.class", header("fixture/GuestParent", "net/minecraftforge/event/world/WorldEvent"));
        resources.put("fixture/Ordinary.class", header("fixture/Ordinary", "java/lang/Object"));
        ClassLoader loader = resources(resources);
        assertTrue(ForgeEventMetadata.isEventSuperclass(loader, "fixture.GuestParent"));
        assertTrue(ForgeEventMetadata.isEventSuperclass(loader, TICK));
        assertTrue(ForgeEventMetadata.isEventSuperclass(loader, "net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent"));
        assertFalse(ForgeEventMetadata.isEventSuperclass(loader, "fixture.Ordinary"));
        assertThrows(ClassNotFoundException.class, () -> ForgeEventMetadata.isEventSuperclass(loader, "fixture.Missing"));
    }

    @Test void rejectsCorruptMismatchedCyclicAndExcessivelyDeepMetadata() throws Exception {
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(Map.of("fixture/Bad.class", new byte[3])), "fixture.Bad"));
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(Map.of("fixture/Bad.class", header("fixture/Other", EVENT))), "fixture.Bad"));
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(Map.of("fixture/Bad.class", header("fixture/Bad", "fixture/Bad"))), "fixture.Bad"));
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(Map.of()), "../Bad"));
        Map<String, byte[]> deep = new HashMap<>();
        for (int index = 0; index < 257; index++) deep.put("fixture/Deep" + index + ".class", header("fixture/Deep" + index, "fixture/Deep" + (index + 1)));
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(deep), "fixture.Deep0"));
        assertThrows(IOException.class, () -> ForgeEventMetadata.isEventSuperclass(resources(Map.of("fixture/Large.class", new byte[16 * 1024 * 1024 + 1])), "fixture.Large"));
    }

    @Test void reproducesTheNestedRawParentBugAndKeepsTheActualTickListenerHierarchyWithThePatch() throws Exception {
        Path metadata = Path.of("../build/forge-compat/fml-runtime-review/trusted-classpath.json");
        Assumptions.assumeTrue(Files.isRegularFile(metadata));
        Path guava = null;
        for (var entry : new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path library = Path.of(entry.getAsString());
            if (library.getFileName().toString().equals("guava-17.0.jar") && Files.isRegularFile(library)) guava = library;
        }
        Assumptions.assumeTrue(guava != null);
        for (boolean patch : List.of(false, true)) {
            try (NestedLoader loader = new NestedLoader(new URL[]{forge().toUri().toURL(), guava.toUri().toURL()}, getClass().getClassLoader(), patch)) {
                Class<?> child = loader.loadClass((TICK + "$ClientTickEvent").replace('/', '.'));
                if (!patch) {
                    InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> child.getConstructor().newInstance());
                    assertInstanceOf(NoSuchMethodError.class, failure.getCause());
                    assertTrue(loader.rawParents.contains(TICK));
                } else {
                    Object value = child.getConstructor().newInstance();
                    Class<?> parent = child.getSuperclass(); Object parentValue = parent.getConstructor().newInstance();
                    Object childList = child.getMethod("getListenerList").invoke(value), parentList = parent.getMethod("getListenerList").invoke(parentValue);
                    var field = childList.getClass().getDeclaredField("parent"); field.setAccessible(true);
                    assertSame(parentList, field.get(childList)); assertTrue(loader.rawParents.isEmpty());
                    assertTrue(loader.transformed.contains(TICK)); assertTrue(loader.transformed.contains(TICK + "$ClientTickEvent"));
                }
            }
        }
    }

    @Test void failsIfThePinnedParentPredicateShapeChanges() throws Exception {
        ClassNode node = new ClassNode();
        try (ZipFile zip = new ZipFile(forge().toFile()); var input = zip.getInputStream(zip.getEntry(TRANSFORMER + ".class"))) { new ClassReader(input.readAllBytes()).accept(node, 0); }
        for (MethodNode method : node.methods) if (method.name.equals("buildEvents")) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("isAssignableFrom")) call.name = "unexpected";
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new ForgeRuntimePatches().accept(node));
        assertTrue(failure.getMessage().contains("parent classification does not match"));
    }

    static final class NestedLoader extends URLClassLoader {
        private final boolean patched;
        private final ThreadLocal<Boolean> transforming = ThreadLocal.withInitial(() -> false);
        final Set<String> rawParents = new LinkedHashSet<>(), transformed = new LinkedHashSet<>();
        private Object transformer;
        private java.lang.reflect.Method transform;
        NestedLoader(URL[] urls, ClassLoader parent, boolean patched) { super(urls, parent); this.patched = patched; }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String internal = name.replace('.', '/');
            if (!internal.equals(TRANSFORMER) && !eventCandidate(internal)) return super.findClass(name);
            try {
                byte[] original;
                try (InputStream input = getResourceAsStream(internal + ".class")) {
                    if (input == null) throw new ClassNotFoundException(name); original = input.readAllBytes();
                }
                if (internal.equals(TRANSFORMER)) {
                    ClassNode node = new ClassNode(); new ClassReader(original).accept(node, 0);
                    if (patched) new ForgeRuntimePatches().accept(node);
                    else for (MethodNode method : node.methods) if (method.name.equals("buildEvents")) for (AbstractInsnNode instruction : method.instructions)
                        if (instruction instanceof MethodInsnNode call && call.name.equals("getType") && call.getPrevious() instanceof FieldInsnNode field && field.name.equals("superName")) call.name = "getObjectType";
                    ClassWriter writer = new ClassWriter(0); node.accept(writer); original = writer.toByteArray();
                } else if (transforming.get()) rawParents.add(internal);
                else {
                    if (transformer == null) {
                        Class<?> type = loadClass(TRANSFORMER.replace('/', '.')); transformer = type.getConstructor().newInstance();
                        transform = type.getMethod("transform", String.class, String.class, byte[].class);
                    }
                    transforming.set(true);
                    try { original = (byte[]) transform.invoke(transformer, name, name, original); transformed.add(internal); }
                    finally { transforming.set(false); }
                }
                return defineClass(name, original, 0, original.length);
            } catch (ClassNotFoundException failure) { throw failure; }
            catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
        }

        private static boolean eventCandidate(String name) {
            return name.startsWith("net/minecraftforge/event/") || name.startsWith("net/minecraftforge/client/event/")
                || name.startsWith("net/minecraftforge/fml/common/gameevent/") || name.startsWith("net/minecraftforge/fml/client/event/")
                || name.startsWith("net/minecraftforge/fml/common/network/FMLNetworkEvent");
        }
    }

    private static ClassLoader resources(Map<String, byte[]> values) {
        return new ClassLoader(null) {
            @Override public Class<?> loadClass(String name) { throw new AssertionError("An event parent was defined while reading its metadata: " + name); }
            @Override public InputStream getResourceAsStream(String name) { byte[] value = values.get(name); return value == null ? null : new ByteArrayInputStream(value); }
        };
    }
    private static byte[] header(String name, String parent) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, parent, null); writer.visitEnd(); return writer.toByteArray(); }
    private static Path forge() { Path path = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
}
