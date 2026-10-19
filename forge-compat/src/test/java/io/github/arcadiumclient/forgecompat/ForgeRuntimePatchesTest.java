package io.github.arcadiumclient.forgecompat;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ForgeRuntimePatchesTest {
    @TempDir Path temporary;
    private Map<String, Object> previousBlackboard;
    private Object previousContext;
    private String previousPrepared;
    private static final List<String> EVENTS = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        previousBlackboard = Launch.blackboard;
        Launch.blackboard = new HashMap<>();
        Field field = ForgeRuntimeContext.class.getDeclaredField("configuration");
        field.setAccessible(true);
        previousContext = field.get(null);
        previousPrepared = System.getProperty("arcadium.forge.prepared");
        EVENTS.clear();
    }

    @AfterEach void restore() throws Exception {
        Launch.blackboard = previousBlackboard;
        Field field = ForgeRuntimeContext.class.getDeclaredField("configuration");
        field.setAccessible(true);
        field.set(null, previousContext);
        if (previousPrepared == null) System.clearProperty("arcadium.forge.prepared");
        else System.setProperty("arcadium.forge.prepared", previousPrepared);
    }

    private Path forge() {
        Path path = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        Assumptions.assumeTrue(Files.isRegularFile(path));
        return path;
    }

    private ClassNode patched(String name) throws Exception {
        try (ZipFile zip = new ZipFile(forge().toFile())) {
            ClassNode node = new ClassNode();
            try (var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
                new ClassReader(input.readAllBytes()).accept(node, 0);
            }
            new ForgeRuntimePatches().accept(node);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    @Test void replacesTheRemovedSecurityManagerConstructorWithPreparedRuntimeVerification() throws Exception {
        ClassNode node = patched("net/minecraftforge/fml/common/launcher/FMLTweaker");
        MethodNode constructor = method(node, "<init>");
        assertFalse(calls(constructor, "java/lang/System", "setSecurityManager"));
        assertFalse(calls(constructor, "net/minecraftforge/fml/relauncher/FMLSecurityManager", "<init>"));
        assertTrue(calls(constructor, "java/lang/System", "setProperty"));
        assertTrue(calls(constructor, "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "verifyRuntimeSource"));
        assertTrue(calls(method(node, "acceptOptions"), "net/minecraftforge/fml/common/launcher/Yggdrasil", "login"));
    }

    @Test void preservesRealFmlSideInitializationAndRedirectsOnlyCoremodDiscovery() throws Exception {
        ClassNode node = patched("net/minecraftforge/fml/relauncher/FMLLaunchHandler");
        assertTrue(calls(method(node, "setupHome"), "net/minecraftforge/fml/relauncher/FMLInjectionData", "build"));
        assertTrue(calls(method(node, "setupHome"), "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "handleLaunch"));
        assertFalse(calls(method(node, "setupHome"), "net/minecraftforge/fml/relauncher/CoreModManager", "handleLaunch"));
        assertTrue(java.util.Arrays.stream(method(node, "side").instructions.toArray())
            .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("side")));
    }

    @Test void replacesTheModClassLoaderCastAndEnumeratesRealRestrictedSources() throws Exception {
        ClassNode node = patched("net/minecraftforge/fml/common/ModClassLoader");
        assertTrue(calls(method(node, "<init>"), "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "legacyClassLoader"));
        assertTrue(calls(method(node, "getParentSources"), "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "parentSources"));
        assertTrue(calls(method(patched("net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer"), "<init>"),
            "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "hostClassLoader"));
        System.setProperty("arcadium.forge.prepared", "true");
        Path game = Files.createDirectories(temporary.resolve("game"));
        Path mods = Files.createDirectories(temporary.resolve("session/mods"));
        Path minecraft = Files.write(temporary.resolve("runtime-game.jar"), new byte[]{1});
        Path guest = Files.write(mods.resolve("guest.jar"), new byte[]{2});
        Files.write(temporary.resolve("native-java25.jar"), new byte[]{3});
        ForgeRuntimeContext.configure(game, mods, forge(), minecraft, List.of(guest, guest));
        assertArrayEquals(new File[]{minecraft.toRealPath().toFile(), guest.toRealPath().toFile()}, ForgeRuntimeContext.parentSources());
    }

    @Test void verifiesPinnedForgeAndRefusesAnUnpreparedOrModifiedRuntime() throws Exception {
        Path game = Files.createDirectories(temporary.resolve("game"));
        Path mods = Files.createDirectories(temporary.resolve("session/mods"));
        ForgeRuntimeContext.configure(game, mods, forge());
        System.clearProperty("arcadium.forge.prepared");
        assertThrows(IllegalStateException.class, ForgeRuntimeContext::verifyRuntimeSource);
        System.setProperty("arcadium.forge.prepared", "true");
        ForgeRuntimeContext.verifyRuntimeSource();
        assertThrows(IllegalStateException.class, ForgeRuntimeContext::parentSources);
        Path changed = Files.write(temporary.resolve("damaged-forge.jar"), new byte[]{1, 2, 3});
        ForgeRuntimeContext.configure(game, mods, changed);
        assertThrows(IllegalStateException.class, ForgeRuntimeContext::verifyRuntimeSource);
    }

    @Test void keepsPack200DisabledOnlyBehindThePreparedGameInvariantAndPreservesSanityVerification() throws Exception {
        assertTrue(calls(method(patched("net/minecraftforge/fml/common/patcher/ClassPatchManager"), "setup"),
            "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "requirePreparedGame"));
        assertTrue(calls(method(patched("net/minecraftforge/fml/common/asm/FMLSanityChecker"), "call"),
            "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "verifyRuntimeSource"));
        ClassNode stack = patched("net/minecraftforge/fml/common/LoadController$FMLSecurityManager");
        assertEquals("java/lang/Object", stack.superName);
        assertTrue(calls(method(stack, "getClassContext"),
            "io/github/arcadiumclient/forgecompat/reflect/ForgeReflection", "callingContext"));
        ClassNode wrappers = patched("net/minecraftforge/fml/common/asm/ASMTransformerWrapper");
        assertTrue(calls(method(wrappers, "getTransformerWrapper"),
            "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "createTransformerWrapper"));
        assertNotNull(method(wrappers, "makeWrapper"));
    }

    @Test void patchesOnlyFmlNotchFieldMetadataReadsAndPreservesRawOfficialBytes() throws Exception {
        ClassNode remapper = patched("net/minecraftforge/fml/common/asm/transformers/deobf/FMLDeobfuscatingRemapper");
        assertTrue(calls(method(remapper, "getFieldType"),
            "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "readOfficialClass"));
        for (MethodNode method : remapper.methods) {
            if (!method.name.equals("getFieldType")) assertFalse(calls(method,
                "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext", "readOfficialClass"));
        }
        Path vanilla = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Assumptions.assumeTrue(Files.isRegularFile(vanilla));
        System.setProperty("arcadium.forge.prepared", "true");
        ForgeRuntimeContext.configure(temporary, temporary.resolve("session/mods"), forge(), vanilla, null, List.of());
        byte[] actual = ForgeRuntimeContext.readOfficialClass(null, "ave", "net.minecraft.client.Minecraft", Launch.classLoader);
        try (ZipFile zip = new ZipFile(vanilla.toFile()); var input = zip.getInputStream(zip.getEntry("ave.class"))) {
            assertArrayEquals(input.readAllBytes(), actual);
        }
        assertEquals("ave", new ClassReader(actual).getClassName());
        byte[] framework = ForgeRuntimeContext.readOfficialClass(null,
            "net.minecraftforge.fml.common.Loader", "net.minecraftforge.fml.common.Loader", Launch.classLoader);
        assertEquals("net/minecraftforge/fml/common/Loader", new ClassReader(framework).getClassName());
        assertNull(ForgeRuntimeContext.readOfficialClass(null, "server/AbsentClass", "server.AbsentClass", Launch.classLoader));
        assertThrows(IOException.class,
            () -> ForgeRuntimeContext.readOfficialClass(null, "../outside", "outside", Launch.classLoader));
    }

    @Test void verifiesAllActualForgeMethodsAfterRuntimePatchesWithoutLoadingAnyOfThem() throws Exception {
        int count = 0;
        try (ZipFile zip = new ZipFile(forge().toFile())) {
            for (var entry : zip.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                try (var input = zip.getInputStream(entry)) { new ClassReader(input.readAllBytes()).accept(node, 0); }
                new ForgeRuntimePatches().accept(node);
                ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(output);
                ClassNode checked = new ClassNode();
                new ClassReader(output.toByteArray()).accept(checked, 0);
                for (MethodNode method : checked.methods) {
                    if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                        new Analyzer<>(new BasicVerifier()).analyze(checked.name, method);
                    }
                }
                count++;
            }
        }
        assertTrue(count > 1000);
    }

    @Test void honorsLiveTweakerSortingAndSelfInsertionWithoutDuplicateProcessing() throws Exception {
        var primary = new BaseTweak();
        List<ITweaker> tweaks = new ArrayList<>(List.of(primary, new SortingTweak(), new LastTweak()));
        Launch.blackboard.put("Tweaks", tweaks);
        Launch.blackboard.put("TweakClasses", new ArrayList<>(List.of(SelfInsertingTweak.class.getName())));
        ForgeCompatBootstrap.processTweaks(primary, getClass().getClassLoader(), temporary, temporary);
        assertEquals(List.of("sort", "first", "last", "self"), EVENTS);
        assertEquals(1, tweaks.stream().filter(tweak -> tweak instanceof SelfInsertingTweak).count());
    }

    @Test void reportsUnsupportedTweakerArgumentsAndPropagatesTweakerFailures() throws Exception {
        var primary = new BaseTweak();
        Launch.blackboard.put("Tweaks", new ArrayList<>(List.of(primary, new ArgumentsTweak())));
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        IOException error = assertThrows(IOException.class,
            () -> ForgeCompatBootstrap.processTweaks(primary, getClass().getClassLoader(), temporary, temporary));
        assertFalse(error.getMessage().contains("private-value"));
        Launch.blackboard.put("Tweaks", new ArrayList<>(List.of(primary, new FailingTweak())));
        assertThrows(IllegalStateException.class,
            () -> ForgeCompatBootstrap.processTweaks(primary, getClass().getClassLoader(), temporary, temporary));
    }

    @Test void selectsOnlyTheMinecraftOriginThatContainsTheRuntimeNamespace() throws Exception {
        Path raw = temporary.resolve("raw.jar");
        Path runtime = temporary.resolve("runtime.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(raw))) {
            zip.putNextEntry(new ZipEntry("ave.class")); zip.write(new byte[]{1}); zip.closeEntry();
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(runtime))) {
            zip.putNextEntry(new ZipEntry("net/minecraft/class_123.class")); zip.write(new byte[]{1}); zip.closeEntry();
        }
        assertEquals(runtime.toRealPath(), ForgeCompatBootstrap.runtimeGame(List.of(raw, runtime), "net/minecraft/class_123.class"));
        assertThrows(IOException.class, () -> ForgeCompatBootstrap.runtimeGame(List.of(raw), "net/minecraft/class_123.class"));
    }

    public static class BaseTweak implements ITweaker {
        @Override public void acceptOptions(List<String> args, File game, File assets, String profile) {}
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) {}
        @Override public String getLaunchTarget() { throw new UnsupportedOperationException("No fixture game target"); }
        @Override public String[] getLaunchArguments() { return new String[0]; }
    }
    public static final class SortingTweak extends BaseTweak {
        @Override public void acceptOptions(List<String> args, File game, File assets, String profile) {
            EVENTS.add("sort");
            @SuppressWarnings("unchecked") List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
            tweaks.add(0, new FirstTweak());
        }
    }
    public static final class FirstTweak extends BaseTweak {
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) { EVENTS.add("first"); }
    }
    public static final class LastTweak extends BaseTweak {
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) { EVENTS.add("last"); }
    }
    public static final class SelfInsertingTweak extends BaseTweak {
        public SelfInsertingTweak() {
            @SuppressWarnings("unchecked") List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
            tweaks.add(this);
        }
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) { EVENTS.add("self"); }
    }
    public static final class ArgumentsTweak extends BaseTweak {
        @Override public String[] getLaunchArguments() { return new String[]{"private-value"}; }
    }
    public static final class FailingTweak extends BaseTweak {
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) { throw new IllegalStateException("Synthetic tweaker failure"); }
    }
}
