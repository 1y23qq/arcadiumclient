package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class HostEntityRenderPassCompatibilityTest {
    private static final String RENDERER = HostEntityRenderPassCompatibility.RENDERER;
    private static final String BAKED = "dev/rdh/argentum/impl/render/blockentity/BakedItemFrames";
    private static final String CLIENT = "net/minecraftforge/client/MinecraftForgeClient";
    private static final String GATHERER = "dev/rdh/argentum/impl/render/entity/EntityGatherer";
    private static final String CULLER = "dev/rdh/argentum/impl/render/entity/EntityOcclusionCuller";
    private static final String MATRICES = "org/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices";

    public static class OwnEntity {
        public int draws, names, checks;
        public boolean baked, visible = true;
        public boolean shouldRenderInPass(int pass) { checks++; return pass == 0; }
        public void draw() { draws++; }
    }
    public static final class SecondPassEntity extends OwnEntity {
        @Override public boolean shouldRenderInPass(int pass) { checks++; return pass == 1; }
    }
    public static final class BothPassEntity extends OwnEntity {
        @Override public boolean shouldRenderInPass(int pass) { checks++; return pass == 0 || pass == 1; }
    }
    public static final class HiddenEntity extends OwnEntity {
        @Override public boolean shouldRenderInPass(int pass) { checks++; return false; }
    }
    public static final class OwnCamera { }
    public static final class OwnClient {
        public static int pass;
        public static int getRenderPass() { return pass; }
    }
    public static final class OwnBaked {
        public static boolean fullyBaked(OwnEntity entity) { return entity.baked; }
    }
    public static final class OwnWorld { }
    public static final class OwnMatrices { }
    public static final class OwnGatherer {
        public List<OwnEntity> entities = new ArrayList<>();
        public List<OwnEntity> getLoadedEntityList(OwnWorld world, int x, int z, int distance) { return entities; }
    }
    public static final class OwnCuller {
        public int calls;
        public List<OwnEntity> candidates;
        public void prepare(List<OwnEntity> candidates, OwnEntity camera, float delta, OwnMatrices matrices, double x, double y, double z) {
            calls++; this.candidates = candidates;
        }
    }
    public static final class LoopFixture {
        public OwnGatherer gatherer = new OwnGatherer();
        public OwnCuller culler = new OwnCuller();
        public OwnMatrices matrices = new OwnMatrices();
        public OwnWorld world = new OwnWorld();
        public int renderEntities(OwnEntity camera, OwnCamera frustum, float delta, double x, double y, double z) {
            List<OwnEntity> entities = gatherer.getLoadedEntityList(world, 0, 0, 0);
            culler.prepare(entities, camera, delta, matrices, x, y, z);
            int count = 0;
            for (OwnEntity entity : entities) {
                if (OwnBaked.fullyBaked(entity)) continue;
                if (!entity.visible) { entity.names++; continue; }
                entity.draw();
                count++;
            }
            return count;
        }
    }

    @Test void preservesCustomSecondPassAndMultipassRenderingWhileRemovingOrdinaryDuplicates() throws Exception {
        ClassNode node = fixture();
        HostEntityRenderPassCompatibility.normalize(node, fixtureMappings());
        byte[] once = bytes(node);
        HostEntityRenderPassCompatibility.normalize(node, fixtureMappings());
        assertArrayEquals(once, bytes(node));
        ClassWriter projected = new ClassWriter(0);
        node.accept(new ClassRemapper(projected, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                if (name.equals(BAKED)) return Type.getInternalName(OwnBaked.class);
                if (name.equals(CLIENT)) return Type.getInternalName(OwnClient.class);
                if (name.equals(GATHERER)) return Type.getInternalName(OwnGatherer.class);
                if (name.equals(CULLER)) return Type.getInternalName(OwnCuller.class);
                if (name.equals(MATRICES)) return Type.getInternalName(OwnMatrices.class);
                return name;
            }
        }));
        class Loader extends ClassLoader {
            Class<?> define(byte[] value) { return defineClass(RENDERER.replace('/', '.'), value, 0, value.length); }
        }
        Class<?> renderer = new Loader().define(projected.toByteArray());
        Object instance = renderer.getConstructor().newInstance();
        OwnEntity ordinary = new OwnEntity(), second = new SecondPassEntity(), both = new BothPassEntity(), hidden = new HiddenEntity();
        OwnEntity baked = new OwnEntity(), nameOnly = new OwnEntity();
        baked.baked = true; nameOnly.visible = false;
        OwnGatherer gatherer = (OwnGatherer) renderer.getField("gatherer").get(instance);
        OwnCuller culler = (OwnCuller) renderer.getField("culler").get(instance);
        gatherer.entities = List.of(ordinary, second, both, hidden, baked, nameOnly);
        var render = renderer.getMethod("renderEntities", OwnEntity.class, OwnCamera.class, float.class, double.class, double.class, double.class);
        OwnClient.pass = 0;
        assertEquals(2, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        OwnClient.pass = 1;
        assertEquals(2, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        assertEquals(1, ordinary.draws); assertEquals(1, second.draws); assertEquals(2, both.draws);
        assertEquals(0, hidden.draws); assertEquals(0, baked.draws); assertEquals(1, nameOnly.names);
        assertEquals(2, culler.calls); assertSame(gatherer.entities, culler.candidates);
        for (OwnEntity entity : gatherer.entities) assertEquals(2, entity.checks);
        OwnClient.pass = -1;
        assertEquals(0, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        assertEquals(3, culler.calls);
        gatherer.entities = List.of(ordinary); culler.calls = 0;
        OwnClient.pass = 0; assertEquals(1, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        OwnClient.pass = 1; assertEquals(0, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        assertEquals(1, culler.calls); assertSame(gatherer.entities, culler.candidates);
        gatherer.entities = List.of(); culler.calls = 0;
        OwnClient.pass = 0; assertEquals(0, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        OwnClient.pass = 1; assertEquals(0, render.invoke(instance, ordinary, new OwnCamera(), 0f, 0d, 0d, 0d));
        assertEquals(1, culler.calls);
        gatherer.entities = List.of(ordinary);
        var select = renderer.getDeclaredMethod("arcadium$entitiesForRenderPass", List.class); select.setAccessible(true);
        OwnClient.pass = 0; assertSame(gatherer.entities, select.invoke(null, gatherer.entities));
        OwnClient.pass = 1; assertSame(java.util.Collections.emptyList(), select.invoke(null, gatherer.entities));
        OwnEntity prefix = new OwnEntity(), reject = new HiddenEntity(), tail = new OwnEntity();
        List<OwnEntity> mixed = List.of(prefix, reject, tail);
        OwnClient.pass = 0; assertEquals(List.of(prefix, tail), select.invoke(null, mixed));
        assertEquals(1, prefix.checks); assertEquals(1, reject.checks); assertEquals(1, tail.checks);
        assertEquals(List.of(prefix, reject, tail), mixed);
        prefix.checks = reject.checks = tail.checks = 0;
        assertEquals(List.of(prefix, tail), select.invoke(null, List.of(reject, prefix, tail)));
        assertEquals(1, prefix.checks); assertEquals(1, reject.checks); assertEquals(1, tail.checks);
    }

    @Test void preservesAllExistingNativeInstructionsLocalsFramesAndBatchCleanup() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path jar = ForgeTestFixtures.nativeJar("argentum-1.0.1.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Path srg = root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg");
        Assumptions.assumeTrue(Files.isRegularFile(jar) && Files.isRegularFile(tiny) && Files.isRegularFile(srg));
        ClassNode node = new ClassNode();
        try (ZipFile zip = new ZipFile(jar.toFile())) { new ClassReader(zip.getInputStream(zip.getEntry(RENDERER + ".class"))).accept(node, 0); }
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(srg)) { mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader); }
        MethodNode render = method(node);
        List<AbstractInsnNode> before = List.of(render.instructions.toArray());
        int locals = render.maxLocals;
        var handlers = List.copyOf(render.tryCatchBlocks);
        HostEntityRenderPassCompatibility.normalize(node, mappings);
        List<AbstractInsnNode> after = List.of(render.instructions.toArray());
        assertEquals(before, after.stream().filter(before::contains).toList());
        assertEquals(3, after.size() - before.size());
        assertEquals(locals, render.maxLocals); assertEquals(handlers, render.tryCatchBlocks);
        ClassNode serialized = read(bytes(node));
        for (MethodNode method : serialized.methods) if (method.name.equals("renderEntities") || method.name.startsWith("arcadium$")) {
            new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        }
        byte[] once = bytes(node);
        HostEntityRenderPassCompatibility.normalize(node, mappings);
        assertArrayEquals(once, bytes(node));
    }

    @Test void rejectsAnUnknownLoopBeforeChangingAnyBytecode() throws Exception {
        ClassNode node = fixture();
        MethodNode render = method(node);
        MethodInsnNode gather = Arrays.stream(render.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call
            && call.owner.equals(GATHERER)).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        gather.name = "collectDifferent";
        byte[] original = bytes(node);
        assertThrows(IllegalStateException.class, () -> HostEntityRenderPassCompatibility.normalize(node, fixtureMappings()));
        assertArrayEquals(original, bytes(node));
    }

    @Test void doesNotTouchOtherClasses() throws Exception {
        ClassNode node = fixture(); node.name = "other/Renderer";
        byte[] original = bytes(node);
        HostEntityRenderPassCompatibility.normalize(node, fixtureMappings());
        assertArrayEquals(original, bytes(node));
        assertFalse(HostEntityRenderPassCompatibility.targets(node.name));
    }

    private static ClassNode fixture() throws Exception {
        ClassNode source;
        try (var input = LoopFixture.class.getResourceAsStream("/" + Type.getInternalName(LoopFixture.class) + ".class")) {
            source = new ClassNode(); new ClassReader(input).accept(source, 0);
        }
        ClassNode target = new ClassNode();
        source.accept(new ClassRemapper(target, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                if (name.equals(Type.getInternalName(LoopFixture.class))) return RENDERER;
                if (name.equals(Type.getInternalName(OwnBaked.class))) return BAKED;
                if (name.equals(Type.getInternalName(OwnGatherer.class))) return GATHERER;
                if (name.equals(Type.getInternalName(OwnCuller.class))) return CULLER;
                if (name.equals(Type.getInternalName(OwnMatrices.class))) return MATRICES;
                return name;
            }
        }));
        target.nestHostClass = null;
        return target;
    }
    private static ForgeMappings fixtureMappings() throws Exception {
        return new ForgeMappings(resolver("tiny\t2\t0\tofficial\tintermediary\nc\te\t" + Type.getInternalName(OwnEntity.class)
            + "\nc\tc\t" + Type.getInternalName(OwnCamera.class) + "\n"), new StringReader("CL: e net/minecraft/entity/Entity\nCL: c net/minecraft/client/renderer/culling/ICamera\n"));
    }
    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode method(ClassNode node) { return node.methods.stream().filter(value -> value.name.equals("renderEntities")).findFirst().orElseThrow(); }
}
