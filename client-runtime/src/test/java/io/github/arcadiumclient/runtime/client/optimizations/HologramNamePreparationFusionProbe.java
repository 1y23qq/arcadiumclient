// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;

public final class HologramNamePreparationFusionProbe implements Opcodes {
    private static final String OP = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String SELF = Type.getInternalName(HologramNamePreparationFusionProbe.class);
    private static final String BATCH = "$argentum$batchNameTag", LAYOUT = "$arcadium$sneakingLayout";
    private record Fixture(Object renderer, MethodHandle call) { }
    private record Result(List<String> events, String exception, long x, long y, long z, String label, String scope, int batchDepth, boolean ownerRestored, long batchTyped, long layoutTyped) { }

    private HologramNamePreparationFusionProbe() { }

    public static void main(String[] args) throws Throwable {
        byte[] original = Files.readAllBytes(Path.of(args[0])); ClassNode before = read(original), transformed = read(original);
        Map<String, String> fingerprints = HologramNamePreparationFusion.audit(before);
        var sites = HologramNamePreparationFusion.apply(transformed); require(sites.size() == 2, "Expected the two actual name sites: " + fingerprints);
        int verified = 0;
        for (MethodNode method : transformed.methods) if ((method.access & ACC_ABSTRACT) == 0) { new Analyzer<>(new BasicVerifier()).analyze(transformed.name, method); verified++; }
        Set<String> changed = new HashSet<>(); for (var site : sites) changed.add(site.method() + site.descriptor());
        int unchanged = 0;
        for (MethodNode method : before.methods) if (!changed.contains(method.name + method.desc)) {
            MethodNode after = find(transformed, method.name, method.desc);
            require(trace(method).equals(trace(after)), "An original callback/bridge/leaf changed: " + method.name); unchanged++;
        }
        for (MethodNode method : transformed.methods) if (method.name.startsWith("arcadium$typedHologram")) {
            require(method.tryCatchBlocks.size() == 2 && method.tryCatchBlocks.stream().allMatch(block -> block.type == null), "Native finally regions were not retained");
            require(code(method).stream().noneMatch(node -> node instanceof MethodInsnNode call && (call.owner.equals(OP) || call.owner.equals("java/lang/Double") && call.name.equals("valueOf"))), "Typed handler retained argument boxing");
        }
        require(HologramNamePreparationFusion.apply(transformed).isEmpty(), "Repeated transformation was not idempotent");
        ClassNode zeroMax = read(original); zeroMax.methods.forEach(method -> { method.maxStack = method.maxLocals = 0; });
        require(HologramNamePreparationFusion.apply(zeroMax).size() == 2, "Late-stage zero maxima were not normalized");
        List<String> negatives = negativeCases(original);
        Fixture nativeFixture = fixture(before, false), fastFixture = fixture(transformed, true);
        int checks = differential(nativeFixture, fastFixture);
        Map<String, Long> allocation = allocations(nativeFixture, fastFixture);
        Map<String, Object> report = new LinkedHashMap<>(); report.put("passed", true); report.put("sourceClassSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        report.put("actualSites", sites.size()); report.put("verifiedMethods", verified); report.put("unchangedOriginalMethods", unchanged); report.put("fingerprints", fingerprints);
        report.put("unknownPatternsRejectedWithoutMutation", negatives); report.put("differentialCases", checks); report.put("allocation", allocation); report.put("zeroMaximaSupported", true); report.put("idempotent", true);
        report.put("usesActualExportedHandlersAndCallers", true); report.put("nativeLeafBytecodeUnchanged", true); report.put("runtimeIntegrated", false); report.put("nativeImageQualification", false); report.put("performanceClaim", false);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Hologram name contract PASS: two actual sites, " + verified + " verified methods, " + negatives.size() + " native fallbacks, " + checks + " differential cases; " + allocation);
    }

    private static List<String> negativeCases(byte[] original) {
        List<String> result = new ArrayList<>();
        reject(original, "unknown renderer", node -> node.name = "custom/LivingRenderer", result);
        reject(original, "unknown mixin owner", node -> annotation(wrapper(node, BATCH)).values.set(annotation(wrapper(node, BATCH)).values.indexOf("mixin") + 1, "custom.NameMixin"), result);
        reject(original, "changed injection priority", node -> annotation(wrapper(node, LAYOUT)).values.set(annotation(wrapper(node, LAYOUT)).values.indexOf("priority") + 1, 1500), result);
        reject(original, "extra handler effect", node -> wrapper(node, BATCH).instructions.insert(new InsnNode(NOP)), result);
        reject(original, "changed sneaking condition", node -> { for (AbstractInsnNode instruction : wrapper(node, LAYOUT).instructions) if (instruction instanceof JumpInsnNode jump) { jump.setOpcode(IFEQ); break; } }, result);
        reject(original, "changed native finally type", node -> wrapper(node, BATCH).tryCatchBlocks.getFirst().type = "java/lang/RuntimeException", result);
        reject(original, "changed native cleanup range", node -> wrapper(node, LAYOUT).tryCatchBlocks.getFirst().end = wrapper(node, LAYOUT).tryCatchBlocks.getFirst().handler, result);
        reject(original, "escaped operation argument", node -> { MethodNode method = wrapper(node, BATCH); method.instructions.insertBefore(method.instructions.getLast(), new VarInsnNode(ALOAD, 8)); method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(POP)); }, result);
        reject(original, "changed wrapper arity", node -> wrapper(node, BATCH).desc = "(Ljava/lang/Object;)V", result);
        reject(original, "malformed parameter metadata", node -> wrapper(node, LAYOUT).parameters.removeLast(), result);
        reject(original, "changed bridge effect", node -> bridge(node, BATCH).instructions.insert(new InsnNode(NOP)), result);
        reject(original, "changed bridge unboxing", node -> { for (AbstractInsnNode instruction : bridge(node, LAYOUT).instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("doubleValue")) { call.name = "unknownDouble"; break; } }, result);
        reject(original, "changed lambda bootstrap", node -> dynamic(node, BATCH).bsm = new Handle(H_INVOKESTATIC, "custom/Factory", "metafactory", dynamic(node, BATCH).bsm.getDesc(), false), result);
        reject(original, "changed lambda handle kind", node -> { InvokeDynamicInsnNode dynamic = dynamic(node, LAYOUT); Handle handle = (Handle) dynamic.bsmArgs[1]; dynamic.bsmArgs[1] = new Handle(H_INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), false); }, result);
        reject(original, "changed argument preload", node -> { List<AbstractInsnNode> code = code(caller(node, BATCH)); int at = code.indexOf(call(node, BATCH)); ((VarInsnNode) code.get(at - 5)).var = 4; }, result);
        reject(original, "interior operand boundary", node -> { MethodNode method = caller(node, LAYOUT); List<AbstractInsnNode> code = code(method); LabelNode boundary = new LabelNode(); method.instructions.insertBefore(code.get(code.indexOf(call(node, LAYOUT)) - 3), boundary); method.instructions.insert(boundary, new LineNumberNode(999, boundary)); }, result);
        reject(original, "foreign branch into operand loads", node -> { MethodNode method = caller(node, BATCH); List<AbstractInsnNode> code = code(method); LabelNode target = new LabelNode(); method.instructions.insertBefore(code.get(code.indexOf(call(node, BATCH)) - 3), target); method.instructions.insertBefore(method.instructions.getLast(), new JumpInsnNode(GOTO, target)); }, result);
        reject(original, "duplicate caller", node -> { MethodNode copy = copy(caller(node, BATCH)); copy.name = "foreignNamePass"; node.methods.add(copy); }, result);
        reject(original, "duplicate handler", node -> { MethodNode copy = copy(wrapper(node, LAYOUT)); copy.name = "another" + LAYOUT; node.methods.add(copy); }, result);
        reject(original, "existing typed method collision", node -> { MethodNode method = new MethodNode(ACC_PRIVATE, "arcadium$typedHologramBatchName", "()V", null, null); method.instructions.add(new InsnNode(RETURN)); node.methods.add(method); }, result);
        return result;
    }

    private static int differential(Fixture nativeFixture, Fixture fastFixture) throws Throwable {
        int checks = 0;
        for (boolean batch : new boolean[]{false, true}) for (boolean sneak : new boolean[]{false, true}) for (int failure = 0; failure <= 7; failure++) for (int scope = 0; scope < 3; scope++) {
            Result expected = run(nativeFixture, batch, sneak, failure, scope, 0, "§bNPC ✫ → :name:"); Result actual = run(fastFixture, batch, sneak, failure, scope, 0, "§bNPC ✫ → :name:");
            sameResult(expected, actual, "batch=" + batch + ",sneak=" + sneak + ",failure=" + failure + ",scope=" + scope); checks++;
            if (scope != 1) require(actual.batchTyped == 0 && actual.layoutTyped == 0, "Unowned invocation used a typed handler");
            if (scope == 1 && failure == 0) require(actual.batchTyped == 1 && actual.layoutTyped == 1, "Owned invocation did not use both typed handlers");
        }
        for (int nested = 1; nested <= 2; nested++) for (boolean sneak : new boolean[]{false, true}) for (int failure : new int[]{0, 8}) {
            sameResult(run(nativeFixture, true, sneak, failure, 1, nested, "outer"), run(fastFixture, true, sneak, failure, 1, nested, "outer"), "nested=" + nested + ",failure=" + failure); checks++;
        }
        for (String text : new String[]{"", "§krotating", "\u2063gradient\u2064", "a changed label", "§c✫ §6→ §e:emoji:", "after resource reload"}) {
            sameResult(run(nativeFixture, true, true, 0, 1, 0, text), run(fastFixture, true, true, 0, 1, 0, text), "live text " + text); checks++;
        }
        EntityStub originalOwner = new EntityStub(false, "before"), fastOwner = new EntityStub(false, "before");
        for (int frame = 0; frame < 16; frame++) {
            String label = "live frame " + frame + (frame % 3 == 0 ? "§k✫\u2063" : " changed"); boolean sneak = (frame & 1) != 0, batch = (frame & 2) != 0; int failure = frame % 5 == 0 ? 4 : 0;
            sameResult(runEntity(nativeFixture, originalOwner, batch, sneak, failure, 1, 0, label), runEntity(fastFixture, fastOwner, batch, sneak, failure, 1, 0, label), "same owner mutation frame " + frame); checks++;
        }
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Result expected = executor.submit(() -> uncheckedRun(nativeFixture)).get(5, TimeUnit.SECONDS);
            Result actual = executor.submit(() -> uncheckedRun(fastFixture)).get(5, TimeUnit.SECONDS);
            sameResult(expected, actual, "foreign thread"); require(actual.batchTyped == 0 && actual.layoutTyped == 0, "Foreign thread borrowed the render scope"); checks++;
        }
        return checks;
    }

    private static Result uncheckedRun(Fixture fixture) { try { return run(fixture, true, true, 0, 1, 0, "foreign"); } catch (Throwable failure) { throw new IllegalStateException(failure); } }
    private static Result run(Fixture fixture, boolean batch, boolean sneak, int failure, int scope, int nested, String label) throws Throwable {
        return runEntity(fixture, new EntityStub(sneak, label), batch, sneak, failure, scope, nested, label);
    }
    private static Result runEntity(Fixture fixture, EntityStub owner, boolean batch, boolean sneak, int failure, int scope, int nested, String label) throws Throwable {
        State.reset(); State.batchEnabled = batch; State.failure = failure; State.nested = nested; State.fixture = fixture;
        owner.sneaking = sneak; owner.text.label = label; State.outer = owner; HologramNamePreparation.batchTypedCalls = HologramNamePreparation.layoutTypedCalls = 0;
        Object active = scope == 1 ? owner : scope == 2 ? new EntityStub(sneak, label) : null;
        Object previous = HologramNamePreparation.enter(active); String exception = "";
        try { invoke(fixture, owner, Double.longBitsToDouble(0x7ff8000000000421L), -0.0, Math.nextUp(1234.5)); }
        catch (RuntimeException thrown) { exception = thrown.getMessage(); }
        finally { HologramNamePreparation.exit(previous); }
        require(!HologramNamePreparation.active(owner), "Owner retained after native callback boundary");
        return new Result(List.copyOf(State.events), exception, State.x, State.y, State.z, State.label, State.scope == null ? "" : State.scope.label, State.batchDepth, State.ownerRestored, HologramNamePreparation.batchTypedCalls, HologramNamePreparation.layoutTypedCalls);
    }

    private static void sameResult(Result expected, Result actual, String mode) {
        require(expected.events.equals(actual.events) && expected.exception.equals(actual.exception) && expected.x == actual.x && expected.y == actual.y && expected.z == actual.z
            && Objects.equals(expected.label, actual.label) && expected.scope.equals(actual.scope) && expected.batchDepth == actual.batchDepth && expected.ownerRestored == actual.ownerRestored,
            "Native name contract differs for " + mode + "\n" + expected + "\n" + actual);
    }

    private static Map<String, Long> allocations(Fixture original, Fixture fused) throws Throwable {
        State.reset(); State.trace = false; State.batchEnabled = true; EntityStub entity = new EntityStub(false, "stable"); State.outer = entity;
        for (int i = 0; i < 40000; i++) { invoke(original, entity, 1.25, 2.5, 3.75); Object previous = HologramNamePreparation.enter(entity); try { invoke(fused, entity, 1.25, 2.5, 3.75); } finally { HologramNamePreparation.exit(previous); } }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean(); require(bean.isThreadAllocatedMemorySupported(), "Allocation measurement is unavailable");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long originalBytes = allocation(bean, original, entity, false), fusedBytes = allocation(bean, fused, entity, true);
        require(fusedBytes <= originalBytes, "Typed native wrapper path allocated more than original");
        return Map.of("calls", 100000L, "nativeBytes", originalBytes, "typedBytes", fusedBytes);
    }
    private static long allocation(com.sun.management.ThreadMXBean bean, Fixture fixture, EntityStub entity, boolean scoped) throws Throwable {
        long minimum = Long.MAX_VALUE, thread = Thread.currentThread().threadId();
        for (int pass = 0; pass < 3; pass++) {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) { Object previous = scoped ? HologramNamePreparation.enter(entity) : null; try { invoke(fixture, entity, 1.25 + (i & 1), 2.5, 3.75); } finally { if (scoped) HologramNamePreparation.exit(previous); } }
            minimum = Math.min(minimum, bean.getThreadAllocatedBytes(thread) - before);
        }
        return minimum;
    }

    private static Fixture fixture(ClassNode source, boolean transformed) throws Throwable {
        ClassNode node = read(bytes(source)); MethodNode batch = wrapper(node, BATCH), layout = wrapper(node, LAYOUT), root = caller(node, BATCH), inner = caller(node, LAYOUT), batchBridge = bridge(node, BATCH), layoutBridge = bridge(node, LAYOUT);
        MethodInsnNode target = code(layoutBridge).stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESPECIAL && call.owner.equals(node.name)).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow();
        MethodNode leaf = find(node, target.name, target.desc); Set<MethodNode> keep = new HashSet<>(List.of(batch, layout, root, inner, batchBridge, layoutBridge, leaf));
        node.methods.removeIf(method -> !keep.contains(method) && !method.name.startsWith("arcadium$typedHologram"));
        leaf.instructions.clear(); leaf.tryCatchBlocks.clear(); leaf.localVariables = null;
        leaf.instructions.add(new VarInsnNode(ALOAD, 1)); leaf.instructions.add(new VarInsnNode(DLOAD, 2)); leaf.instructions.add(new VarInsnNode(DLOAD, 4)); leaf.instructions.add(new VarInsnNode(DLOAD, 6));
        leaf.instructions.add(new MethodInsnNode(INVOKESTATIC, SELF + "$State", "leaf", "(L" + Type.getArgumentTypes(leaf.desc)[0].getInternalName() + ";DDD)V", false)); leaf.instructions.add(new InsnNode(RETURN)); leaf.maxStack = 7; leaf.maxLocals = 8;
        String name = "io/github/arcadiumclient/runtime/client/optimizations/HologramNameFixture" + (transformed ? "Typed" : "Native");
        String living = Type.getArgumentTypes(root.desc)[0].getInternalName(); Map<String, String> types = new HashMap<>(); types.put(node.name, name); types.put(living, SELF + "$EntityStub"); types.put("net/minecraft/unmapped/C_42232651", SELF + "$EntityStub"); types.put("net/minecraft/entity/Entity", SELF + "$EntityStub");
        types.put("net/minecraft/unmapped/C_95231345", SELF + "$TextStub"); types.put("net/minecraft/text/Text", SELF + "$TextStub"); types.put("io/github/arcadiumclient/runtime/client/branding/NametagLayout", SELF + "$LayoutStub"); types.put("io/github/arcadiumclient/runtime/client/branding/NametagLayout$Scope", SELF + "$ScopeStub"); types.put("dev/rdh/argentum/impl/render/entity/NameTagBatch", SELF + "$BatchStub");
        node.version = V17; node.access = ACC_PUBLIC; node.superName = "java/lang/Object"; node.interfaces.clear(); node.fields.clear(); node.signature = null; node.visibleAnnotations = node.invisibleAnnotations = null;
        node.innerClasses.clear(); node.nestHostClass = null; node.nestMembers = null; node.outerClass = node.outerMethod = node.outerMethodDesc = null;
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null); constructor.instructions.add(new VarInsnNode(ALOAD, 0)); constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); constructor.instructions.add(new InsnNode(RETURN)); node.methods.add(constructor);
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(new ClassRemapper(output, new SimpleRemapper(ASM9, types)));
        byte[] binary = output.toByteArray(); Class<?> runtime = new ClassLoader(HologramNamePreparationFusionProbe.class.getClassLoader()) { Class<?> define() { return defineClass(name.replace('/', '.'), binary, 0, binary.length); } }.define();
        Object renderer = runtime.getConstructor().newInstance(); MethodHandle invoke = MethodHandles.publicLookup().findVirtual(runtime, root.name, MethodType.methodType(void.class, EntityStub.class, double.class, double.class, double.class));
        return new Fixture(renderer, invoke.asType(MethodType.methodType(void.class, Object.class, EntityStub.class, double.class, double.class, double.class)));
    }
    private static void invoke(Fixture fixture, EntityStub entity, double x, double y, double z) throws Throwable { fixture.call.invokeExact(fixture.renderer, entity, x, y, z); }

    public interface TextStub { String m_42269114(); }
    public static final class EntityStub {
        boolean sneaking; final TextValue text;
        EntityStub(boolean sneaking, String label) { this.sneaking = sneaking; this.text = new TextValue(label); }
        public boolean m_40164672() { if (State.trace) State.event("sneak:" + sneaking); State.fail(1); return sneaking; }
        public TextStub m_33641726() { State.event("display"); State.fail(2); return text; }
    }
    public static final class TextValue implements TextStub {
        String label; TextValue(String label) { this.label = label; }
        public String m_42269114() { State.event("format:" + label); return label; }
    }
    public static final class ScopeStub { final String label; ScopeStub(String label) { this.label = label; } }
    public static final class LayoutStub {
        public static ScopeStub begin(EntityStub entity, String name) { State.event("layoutBegin:" + name); State.fail(3); ScopeStub previous = State.scope; State.scope = new ScopeStub(name); return previous; }
        public static void end(ScopeStub previous) { State.event("layoutEnd"); State.scope = previous; State.fail(5); }
    }
    public static final class BatchStub {
        private static final BatchStub INSTANCE = new BatchStub();
        public static BatchStub active() { State.event("batchActive"); return State.batchEnabled ? INSTANCE : null; }
        public void enter() { State.event("batchEnter"); State.fail(6); State.batchDepth++; }
        public void exit() { State.event("batchExit"); State.batchDepth--; State.fail(7); }
    }
    public static final class State {
        static final List<String> events = new ArrayList<>(); static boolean trace = true, batchEnabled, inside, ownerRestored; static int failure, nested, batchDepth; static long x, y, z; static String label; static ScopeStub scope; static Fixture fixture; static EntityStub outer;
        static void reset() { events.clear(); trace = true; batchEnabled = inside = ownerRestored = false; failure = nested = batchDepth = 0; x = y = z = 0; label = null; scope = null; fixture = null; outer = null; }
        static void event(String event) { if (trace) events.add(event); }
        static void fail(int at) { if (failure == at) throw new IllegalStateException("native failure " + at); }
        public static void leaf(EntityStub entity, double px, double py, double pz) {
            if (trace) event("leaf:" + entity.text.label); x = Double.doubleToRawLongBits(px); y = Double.doubleToRawLongBits(py); z = Double.doubleToRawLongBits(pz); label = entity.text.label; fail(4);
            if (inside) fail(8);
            if (nested != 0 && !inside) {
                inside = true; EntityStub child = new EntityStub(!entity.sneaking, "nested live"); Object previous = nested == 2 ? HologramNamePreparation.enter(child) : null;
                try { invoke(fixture, child, 11.5, -22.75, 33.125); } catch (Throwable failure) { throw new IllegalStateException(failure); }
                finally { if (nested == 2) HologramNamePreparation.exit(previous); inside = false; ownerRestored = HologramNamePreparation.active(outer); }
            }
        }
    }

    private static void reject(byte[] original, String name, Consumer<ClassNode> mutation, List<String> passed) {
        ClassNode target = read(original); mutation.accept(target); byte[] before = bytes(target); require(HologramNamePreparationFusion.apply(target).isEmpty(), "Unsupported shape transformed: " + name);
        require(Arrays.equals(before, bytes(target)), "Unsupported shape partially mutated: " + name); passed.add(name);
    }
    private static MethodNode wrapper(ClassNode node, String suffix) { return node.methods.stream().filter(method -> method.name.endsWith(suffix)).findFirst().orElseThrow(); }
    private static AnnotationNode annotation(MethodNode method) { return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/MixinMerged;")).findFirst().orElseThrow(); }
    private static MethodNode caller(ClassNode node, String suffix) { return node.methods.stream().filter(method -> code(method).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(node.name) && call.name.equals(wrapper(node, suffix).name))).findFirst().orElseThrow(); }
    private static MethodInsnNode call(ClassNode node, String suffix) { return code(caller(node, suffix)).stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals(wrapper(node, suffix).name)).map(instruction -> (MethodInsnNode) instruction).findFirst().orElseThrow(); }
    private static InvokeDynamicInsnNode dynamic(ClassNode node, String suffix) { AbstractInsnNode previous = call(node, suffix).getPrevious(); while (previous.getOpcode() < 0) previous = previous.getPrevious(); return (InvokeDynamicInsnNode) previous; }
    private static MethodNode bridge(ClassNode node, String suffix) { Handle handle = (Handle) dynamic(node, suffix).bsmArgs[1]; return find(node, handle.getName(), handle.getDesc()); }
    private static MethodNode find(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction); return result; }
    private static MethodNode copy(MethodNode method) { MethodNode result = new MethodNode(ASM9, method.access, method.name, method.desc, method.signature, method.exceptions.toArray(String[]::new)); method.accept(result); return result; }
    private static String trace(MethodNode method) { MethodNode clean = copy(method); for (AbstractInsnNode node : clean.instructions.toArray()) if (node instanceof FrameNode || node instanceof LineNumberNode) clean.instructions.remove(node); Textifier printer = new Textifier(); clean.accept(new TraceMethodVisitor(printer)); return printer.getText().toString(); }
    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(ASM9); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
