package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class HostRaytraceCompatibilityTest {
    private static final String WORLD = "io/github/arcadiumclient/forgecompat/fixture/RayWorld";
    private static final String BLOCK = OwnBlock.class.getName().replace('.', '/');
    private static final String STATE = OwnState.class.getName().replace('.', '/');
    private static final String VECTOR = OwnVector.class.getName().replace('.', '/');
    private static final String HIT = OwnHit.class.getName().replace('.', '/');
    private static final String DESC = "(L" + VECTOR + ";L" + VECTOR + ";ZZZ)L" + HIT + ";";
    private static final String SRG_DESC = "(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;";
    @TempDir Path temporary;

    public static final class OwnState { }
    public static final class OwnVector { }
    public static final class OwnHit { public final int calls; public OwnHit(int calls) { this.calls = calls; } }
    public static final class OwnBlock { public static int calls; public boolean check(OwnState state, boolean flag) { calls++; return flag; } }

    @Test void verifiesMetadataAndDeadValueKillOnTheJvmWithoutChangingRaytraceEffects() throws Exception {
        ClassNode node = fixture();
        var mappings = fixtureMappings();
        MethodNode before = ray(node);
        assertEquals(Set.of(14, 40), blockLocals(node.name, before, collisionCalls(before).get(1), BLOCK));
        List<String> originalCode = code(before, Set.of());
        HostRaytraceCompatibility.normalize(node, mappings);
        MethodNode after = ray(node);
        assertEquals(Set.of(40), blockLocals(node.name, after, collisionCalls(after).get(1), BLOCK));
        Set<AbstractInsnNode> clear = addedClear(after, 14);
        assertEquals(2, clear.size());
        assertEquals(originalCode, code(after, clear));
        class FixtureLoader extends ClassLoader {
            Class<?> define(byte[] bytes) { return defineClass(WORLD.replace('/', '.'), bytes, 0, bytes.length); }
        }
        Class<?> loaded = new FixtureLoader().define(bytes(node, false));
        OwnBlock.calls = 0;
        OwnHit result = (OwnHit) loaded.getMethod("ray", OwnVector.class, OwnVector.class, boolean.class, boolean.class, boolean.class)
            .invoke(loaded.getConstructor().newInstance(), new OwnVector(), new OwnVector(), true, false, false);
        assertEquals(4, result.calls);
        assertEquals(4, OwnBlock.calls);
        byte[] once = bytes(node, false);
        HostRaytraceCompatibility.normalize(node, mappings);
        assertArrayEquals(once, bytes(node, false));
    }

    @Test void rejectsAnyReachableLoopReadOrReuseOfTheInitialBlockSlot() throws Exception {
        for (boolean read : List.of(true, false)) {
            ClassNode node = fixture();
            MethodNode ray = ray(node);
            InsnList unsafe = new InsnList();
            unsafe.add(new VarInsnNode(Opcodes.ALOAD, 14));
            unsafe.add(read ? new InsnNode(Opcodes.POP) : new VarInsnNode(Opcodes.ASTORE, 14));
            ray.instructions.insert(collisionCalls(ray).get(1), unsafe);
            byte[] original = bytes(node, false);
            assertThrows(IllegalStateException.class, () -> HostRaytraceCompatibility.normalize(node, fixtureMappings()));
            assertArrayEquals(original, bytes(node, false));
        }
    }

    @Test void detectsBackEdgesToAnEarlierLexicalUseOfTheInitialBlock() throws Exception {
        ClassNode node = fixture(); MethodNode ray = ray(node);
        AbstractInsnNode receiver = previous(previous(previous(collisionCalls(ray).get(0))));
        LabelNode earlier = new LabelNode(); ray.instructions.insertBefore(receiver, earlier);
        JumpInsnNode back = Arrays.stream(ray.instructions.toArray()).filter(value -> value.getOpcode() == Opcodes.GOTO)
            .map(value -> (JumpInsnNode) value).findFirst().orElseThrow();
        back.label = earlier;
        byte[] original = bytes(node, false);
        assertThrows(IllegalStateException.class, () -> HostRaytraceCompatibility.normalize(node, fixtureMappings()));
        assertArrayEquals(original, bytes(node, false));
    }

    @Test void includesExceptionalPathsInTheDeadLocalProof() throws Exception {
        ClassNode node = fixture(); MethodNode ray = ray(node);
        JumpInsnNode back = Arrays.stream(ray.instructions.toArray()).filter(value -> value.getOpcode() == Opcodes.GOTO)
            .map(value -> (JumpInsnNode) value).findFirst().orElseThrow();
        LabelNode end = new LabelNode(), handler = new LabelNode(); ray.instructions.insert(back, end);
        ray.instructions.add(handler); ray.instructions.add(new VarInsnNode(Opcodes.ASTORE, 41));
        ray.instructions.add(new VarInsnNode(Opcodes.ALOAD, 14)); ray.instructions.add(new InsnNode(Opcodes.POP));
        ray.instructions.add(new VarInsnNode(Opcodes.ALOAD, 41)); ray.instructions.add(new InsnNode(Opcodes.ATHROW));
        ray.tryCatchBlocks.add(new TryCatchBlockNode(back.label, end, handler, "java/lang/Throwable"));
        ClassNode valid = new ClassNode(); new ClassReader(bytes(node, true)).accept(valid, 0);
        byte[] original = bytes(valid, false);
        assertThrows(IllegalStateException.class, () -> HostRaytraceCompatibility.normalize(valid, fixtureMappings()));
        assertArrayEquals(original, bytes(valid, false));
    }

    @Test void repairsTheActualForgeFrameAndSarcioCaptureWhilePreservingEveryExistingInstruction() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path client = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path sarcio = ForgeTestFixtures.nativeJar("Sarcio-1.1.0.jar");
        Assumptions.assumeTrue(Files.isRegularFile(client) && Files.isRegularFile(tiny) && Files.isRegularFile(sarcio));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) { assertNotNull(input); Files.copy(input, patches); }
        Path patched = new ForgeGamePatcher().patch(client, patches, temporary.resolve("patched"));
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(dependencies.resolve("forge-1.8.9.srg"))) { mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader); }
        new ForgeJarTransformer().index(patched, mappings, true);
        ClassNode world = new ClassNode(); readClass(patched, "adm.class").accept(new ClassRemapper(world, mappings.officialToRuntime()));
        String methodName = mappings.methodFromSrg("net/minecraft/world/World", "func_147447_a", SRG_DESC);
        String descriptor = mappings.srgToRuntime().mapMethodDesc(SRG_DESC);
        String block = mappings.classFromSrg("net/minecraft/block/Block");
        MethodNode method = world.methods.stream().filter(value -> value.name.equals(methodName) && value.desc.equals(descriptor)).findFirst().orElseThrow();
        ClassNode mixin = readClass(sarcio, "dev/rdh/sarcio/mixin/bugfix/world/WorldMixin.class");
        MethodNode callback = mixin.methods.stream().filter(value -> value.name.equals("sarcio$cameraIgnoresGlass")).findFirst().orElseThrow();
        assertEquals(3, Type.getArgumentTypes(callback.desc).length);
        assertTrue(callback.invisibleParameterAnnotations[1].get(0).values == null || callback.invisibleParameterAnnotations[1].get(0).values.isEmpty());
        AnnotationNode vector = callback.invisibleParameterAnnotations[2].get(0);
        assertEquals(true, value(vector, "argsOnly")); assertEquals(1, value(vector, "ordinal"));
        List<MethodInsnNode> calls = collisionCalls(method, block);
        assertEquals(2, calls.size());
        assertEquals(Set.of(14, 40), blockLocals(world.name, method, calls.get(1), block));
        List<String> original = code(method, Set.of());
        byte[] callbackBefore = methodBytes(mixin.name, callback);
        HostRaytraceCompatibility.normalize(world, mappings);
        List<MethodInsnNode> afterCalls = collisionCalls(method, block);
        assertEquals(Set.of(14), blockLocals(world.name, method, afterCalls.get(0), block));
        assertEquals(Set.of(40), blockLocals(world.name, method, afterCalls.get(1), block));
        assertEquals(original, code(method, addedClear(method, 14)));
        assertArrayEquals(callbackBefore, methodBytes(mixin.name, callback));
        int second = method.instructions.indexOf(afterCalls.get(1));
        assertFalse(method.localVariables.stream().anyMatch(local -> local.index == 14 && local.desc.equals("L" + block + ";")
            && method.instructions.indexOf(local.start) <= second && method.instructions.indexOf(local.end) > second));
        ClassNode serialized = new ClassNode(); new ClassReader(bytes(world, false)).accept(serialized, 0);
        MethodNode checked = serialized.methods.stream().filter(candidate -> candidate.name.equals(methodName) && candidate.desc.equals(descriptor)).findFirst().orElseThrow();
        new Analyzer<>(new BasicVerifier()).analyze(serialized.name, checked);
        System.out.println("Real Sarcio capture: initial Block14 retained before loop, second canCollideCheck Block14+40→40; all original Forge instructions/branches unchanged");
    }

    private static Set<Integer> blockLocals(String owner, MethodNode method, AbstractInsnNode point, String block) throws Exception {
        BasicInterpreter interpreter = new BasicInterpreter(Opcodes.ASM9) {
            @Override public BasicValue newValue(Type type) {
                return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) ? new BasicValue(type) : super.newValue(type);
            }
            @Override public BasicValue merge(BasicValue first, BasicValue second) {
                if (first.equals(second)) return first;
                if (first.isReference() && second.isReference()) {
                    if (first.getType().getInternalName().equals("null")) return second;
                    if (second.getType().getInternalName().equals("null")) return first;
                    return new BasicValue(Type.getObjectType("java/lang/Object"));
                }
                return BasicValue.UNINITIALIZED_VALUE;
            }
        };
        var frame = new Analyzer<>(interpreter).analyze(owner, method)[method.instructions.indexOf(point)];
        Set<Integer> result = new java.util.LinkedHashSet<>();
        for (int i = 0; i < frame.getLocals(); i++) if (frame.getLocal(i).getType() != null && frame.getLocal(i).getType().getSort() == Type.OBJECT
            && frame.getLocal(i).getType().getInternalName().equals(block)) result.add(i);
        return result;
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, WORLD, null, "java/lang/Object", null);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); constructor.visitInsn(Opcodes.RETURN); node.methods.add(constructor);
        MethodNode ray = new MethodNode(Opcodes.ACC_PUBLIC, "ray", DESC, null, null);
        ray.visitTypeInsn(Opcodes.NEW, STATE); ray.visitInsn(Opcodes.DUP); ray.visitMethodInsn(Opcodes.INVOKESPECIAL, STATE, "<init>", "()V", false); ray.visitVarInsn(Opcodes.ASTORE, 13);
        ray.visitTypeInsn(Opcodes.NEW, BLOCK); ray.visitInsn(Opcodes.DUP); ray.visitMethodInsn(Opcodes.INVOKESPECIAL, BLOCK, "<init>", "()V", false); ray.visitVarInsn(Opcodes.ASTORE, 14);
        org.objectweb.asm.Label initial = new org.objectweb.asm.Label(), end = new org.objectweb.asm.Label(), loop = new org.objectweb.asm.Label();
        ray.visitLabel(initial); collision(ray, 14); ray.visitInsn(Opcodes.ICONST_0); ray.visitVarInsn(Opcodes.ISTORE, 15);
        ray.visitLabel(loop); ray.visitVarInsn(Opcodes.ILOAD, 15); ray.visitInsn(Opcodes.ICONST_3); ray.visitJumpInsn(Opcodes.IF_ICMPGE, end);
        ray.visitTypeInsn(Opcodes.NEW, BLOCK); ray.visitInsn(Opcodes.DUP); ray.visitMethodInsn(Opcodes.INVOKESPECIAL, BLOCK, "<init>", "()V", false); ray.visitVarInsn(Opcodes.ASTORE, 40);
        collision(ray, 40); ray.visitIincInsn(15, 1); ray.visitJumpInsn(Opcodes.GOTO, loop);
        ray.visitLabel(end); ray.visitTypeInsn(Opcodes.NEW, HIT); ray.visitInsn(Opcodes.DUP);
        ray.visitFieldInsn(Opcodes.GETSTATIC, BLOCK, "calls", "I"); ray.visitMethodInsn(Opcodes.INVOKESPECIAL, HIT, "<init>", "(I)V", false); ray.visitInsn(Opcodes.ARETURN);
        ray.visitLocalVariable("initialBlock", "L" + BLOCK + ";", null, initial, end, 14);
        node.methods.add(ray); ClassNode result = new ClassNode(); new ClassReader(bytes(node, true)).accept(result, 0); return result;
    }
    private static void collision(MethodNode method, int slot) {
        method.visitVarInsn(Opcodes.ALOAD, slot); method.visitVarInsn(Opcodes.ALOAD, 13); method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK, "check", "(L" + STATE + ";Z)Z", false); method.visitInsn(Opcodes.POP);
    }
    private static ForgeMappings fixtureMappings() throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\tw\t" + WORLD + "\n\tm\t(Lv;Lv;ZZZ)Lh;\ta\tray\nc\tb\t" + BLOCK
            + "\n\tm\t(Ls;Z)Z\ta\tcheck\nc\tv\t" + VECTOR + "\nc\th\t" + HIT + "\nc\ts\t" + STATE + "\n";
        String srg = "CL: w net/minecraft/world/World\nCL: b net/minecraft/block/Block\nCL: v net/minecraft/util/Vec3\nCL: h net/minecraft/util/MovingObjectPosition\nCL: s net/minecraft/block/state/IBlockState\nMD: w/a (Lv;Lv;ZZZ)Lh; net/minecraft/world/World/func_147447_a " + SRG_DESC
            + "\nMD: b/a (Ls;Z)Z net/minecraft/block/Block/func_176209_a (Lnet/minecraft/block/state/IBlockState;Z)Z\n";
        return new ForgeMappings(resolver(tiny), new StringReader(srg));
    }
    private static MappingResolver resolver(String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary");
    }
    private static MethodNode ray(ClassNode node) { return node.methods.stream().filter(method -> method.name.equals("ray")).findFirst().orElseThrow(); }
    private static List<MethodInsnNode> collisionCalls(MethodNode method) { return collisionCalls(method, BLOCK); }
    private static List<MethodInsnNode> collisionCalls(MethodNode method, String block) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(block) && call.desc.endsWith(";Z)Z")).map(instruction -> (MethodInsnNode) instruction).toList(); }
    private static Set<AbstractInsnNode> addedClear(MethodNode method, int slot) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() == Opcodes.ACONST_NULL && instruction.getNext() instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE && store.var == slot) return Set.of(instruction, store);
        return Set.of();
    }
    private static List<String> code(MethodNode method, Set<AbstractInsnNode> omitted) {
        List<AbstractInsnNode> real = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0 && !omitted.contains(instruction)).toList();
        java.util.function.Function<LabelNode, Integer> target = label -> { AbstractInsnNode next = label; while (next != null && !real.contains(next)) next = next.getNext(); return real.indexOf(next); };
        List<String> result = new ArrayList<>();
        for (AbstractInsnNode instruction : real) {
            String detail = "";
            if (instruction instanceof VarInsnNode variable) detail = "v" + variable.var;
            else if (instruction instanceof IincInsnNode increment) detail = increment.var + ":" + increment.incr;
            else if (instruction instanceof TypeInsnNode type) detail = type.desc;
            else if (instruction instanceof MethodInsnNode call) detail = call.owner + "." + call.name + call.desc + ":" + call.itf;
            else if (instruction instanceof FieldInsnNode field) detail = field.owner + "." + field.name + ":" + field.desc;
            else if (instruction instanceof IntInsnNode integer) detail = String.valueOf(integer.operand);
            else if (instruction instanceof LdcInsnNode constant) detail = String.valueOf(constant.cst);
            else if (instruction instanceof JumpInsnNode branch) detail = String.valueOf(target.apply(branch.label));
            else if (instruction instanceof TableSwitchInsnNode branch) detail = branch.min + ":" + branch.max + ":" + target.apply(branch.dflt) + ":" + branch.labels.stream().map(target).toList();
            else if (instruction instanceof LookupSwitchInsnNode branch) detail = branch.keys + ":" + target.apply(branch.dflt) + ":" + branch.labels.stream().map(target).toList();
            else if (instruction instanceof MultiANewArrayInsnNode array) detail = array.desc + ":" + array.dims;
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) detail = dynamic.name + dynamic.desc + ":" + dynamic.bsm + ":" + Arrays.toString(dynamic.bsmArgs);
            result.add(instruction.getOpcode() + ":" + detail);
        }
        return result;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) { do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node; }
    private static byte[] bytes(ClassNode node, boolean frames) { ClassWriter writer = new ClassWriter(frames ? ClassWriter.COMPUTE_FRAMES : 0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node, false); }
    private static ClassNode readClass(Path jar, String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static Object value(AnnotationNode annotation, String key) { for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1); return null; }
}
