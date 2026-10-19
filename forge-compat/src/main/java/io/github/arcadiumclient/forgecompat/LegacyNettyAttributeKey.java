package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class LegacyNettyAttributeKey {
    private static final String KEY = "io/netty/util/AttributeKey";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/LegacyNettyAttributeKey";
    private static final String FACTORY = BRIDGE + "$Factory";
    private static final ClassValue<Factory> FACTORIES = new ClassValue<>() {
        @Override protected Factory computeValue(Class<?> type) {
            try {
                MethodHandle create;
                try { create = MethodHandles.publicLookup().findConstructor(type, MethodType.methodType(void.class, String.class)); }
                catch (NoSuchMethodException | IllegalAccessException missingConstructor) {
                    create = MethodHandles.publicLookup().findStatic(type, "newInstance", MethodType.methodType(type, String.class));
                }
                return new Factory(create.asType(MethodType.methodType(Object.class, String.class)));
            } catch (ReflectiveOperationException exception) {
                throw new LinkageError("Netty AttributeKey needs a public String constructor or newInstance(String).", exception);
            }
        }
    };

    private LegacyNettyAttributeKey() { }

    public static Factory factory(Class<?> type) throws ClassNotFoundException {
        if (!type.getName().equals(KEY.replace('/', '.'))) throw new IllegalArgumentException("Expected Netty AttributeKey.");
        if (Class.forName(type.getName(), true, type.getClassLoader()) != type) throw new LinkageError("Netty AttributeKey class ownership changed.");
        return FACTORIES.get(type);
    }

    public static final class Factory {
        private final MethodHandle create;
        private Factory(MethodHandle create) { this.create = create; }
        public Object create(String name) throws Throwable { return create.invokeExact(name); }
    }

    private static final class Allocation extends BasicValue {
        final TypeInsnNode instruction;
        Allocation(TypeInsnNode instruction) { super(Type.getObjectType(KEY)); this.instruction = instruction; }
        @Override public boolean equals(Object other) { return other instanceof Allocation allocation && allocation.instruction == instruction; }
        @Override public int hashCode() { return System.identityHashCode(instruction); }
    }

    private static final class AllocationFrame extends Frame<BasicValue> {
        AllocationFrame(int locals, int stack) { super(locals, stack); }
        AllocationFrame(Frame<? extends BasicValue> frame) { super(frame); }
        @Override public void execute(AbstractInsnNode instruction, Interpreter<BasicValue> interpreter) throws AnalyzerException {
            Allocation initialized = null;
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(KEY) && call.name.equals("<init>")) {
                int receiver = getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
                if (receiver >= 0 && getStack(receiver) instanceof Allocation allocation) initialized = allocation;
            }
            super.execute(instruction, interpreter);
            if (initialized != null) {
                for (int i = 0; i < getLocals(); i++) if (initialized.equals(getLocal(i))) setLocal(i, BasicValue.REFERENCE_VALUE);
                for (int i = 0; i < getStackSize(); i++) if (initialized.equals(getStack(i))) setStack(i, BasicValue.REFERENCE_VALUE);
            }
        }
    }

    private record Plan(MethodNode method, TypeInsnNode allocation, MethodInsnNode constructor) { }

    public static void normalize(ClassNode node) {
        List<Plan> plans = new ArrayList<>();
        for (MethodNode method : node.methods) {
            List<MethodInsnNode> constructors = Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(KEY) && call.name.equals("<init>") && call.desc.equals("(Ljava/lang/String;)V"))
                .map(MethodInsnNode.class::cast).toList();
            if (constructors.isEmpty()) continue;
            try { plans.addAll(verify(node, method, constructors)); }
            catch (AnalyzerException exception) {
                throw new IllegalStateException("Cannot verify legacy Netty AttributeKey allocation in " + node.name + "." + method.name + method.desc + ".", exception);
            }
        }
        Set<TypeInsnNode> allocations = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Plan plan : plans) if (!allocations.add(plan.allocation)) throw new IllegalStateException("Multiple constructors share a legacy Netty AttributeKey allocation.");
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FrameNode frame) {
            if (frame.local != null) for (Object value : frame.local) if (allocation(value, allocations)) {
                throw new IllegalStateException("Stored uninitialized Netty AttributeKey aliases are unsupported.");
            }
        }
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FrameNode frame && frame.stack != null) {
            for (int i = 0; i < frame.stack.size(); i++) if (allocation(frame.stack.get(i), allocations)) frame.stack.set(i, FACTORY);
        }
        for (Plan plan : plans) {
            InsnList prepare = new InsnList();
            prepare.add(new LdcInsnNode(Type.getObjectType(KEY)));
            prepare.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "factory", "(Ljava/lang/Class;)L" + FACTORY + ";", false));
            plan.method.instructions.insertBefore(plan.allocation, prepare);
            plan.method.instructions.remove(plan.allocation);
            plan.constructor.setOpcode(Opcodes.INVOKEVIRTUAL);
            plan.constructor.owner = FACTORY;
            plan.constructor.name = "create";
            plan.constructor.desc = "(Ljava/lang/String;)Ljava/lang/Object;";
            plan.constructor.itf = false;
            InsnList finish = new InsnList();
            finish.add(new TypeInsnNode(Opcodes.CHECKCAST, KEY));
            finish.add(new InsnNode(Opcodes.SWAP));
            finish.add(new InsnNode(Opcodes.POP));
            plan.method.instructions.insert(plan.constructor, finish);
        }
    }

    private static List<Plan> verify(ClassNode node, MethodNode method, List<MethodInsnNode> constructors) throws AnalyzerException {
        Map<Integer, Set<Integer>> edges = new HashMap<>();
        BasicInterpreter interpreter = new BasicInterpreter(Opcodes.ASM9) {
            @Override public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
                return instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(KEY)
                    ? new Allocation(type) : super.newOperation(instruction);
            }
        };
        Analyzer<BasicValue> analyzer = new Analyzer<>(interpreter) {
            @Override protected Frame<BasicValue> newFrame(int locals, int stack) { return new AllocationFrame(locals, stack); }
            @Override protected Frame<BasicValue> newFrame(Frame<? extends BasicValue> frame) { return new AllocationFrame(frame); }
            @Override protected void newControlFlowEdge(int from, int to) { edges.computeIfAbsent(from, ignored -> new HashSet<>()).add(to); }
        };
        Frame<BasicValue>[] frames = analyzer.analyze(node.name, method);
        List<Plan> result = new ArrayList<>();
        for (MethodInsnNode constructor : constructors) {
            int callIndex = method.instructions.indexOf(constructor);
            Frame<BasicValue> call = frames[callIndex];
            if (call == null || call.getStackSize() < 3 || !(call.getStack(call.getStackSize() - 2) instanceof Allocation value)) {
                throw new IllegalStateException("Legacy Netty AttributeKey requires a verifiable NEW/DUP allocation.");
            }
            TypeInsnNode allocation = value.instruction;
            AbstractInsnNode duplicate = next(allocation);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) throw new IllegalStateException("Legacy Netty AttributeKey requires an immediate DUP.");
            int start = method.instructions.indexOf(allocation), dup = method.instructions.indexOf(duplicate);
            int depth = frames[start].getStackSize();
            if (call.getStackSize() != depth + 3) throw new IllegalStateException("Unsupported legacy Netty AttributeKey constructor stack.");
            Set<Integer> visited = new HashSet<>(); ArrayDeque<Integer> pending = new ArrayDeque<>(); pending.add(dup);
            while (!pending.isEmpty()) {
                int index = pending.removeFirst(); if (!visited.add(index)) continue;
                Frame<BasicValue> frame = frames[index]; if (frame == null) continue;
                for (int local = 0; local < frame.getLocals(); local++) if (value.equals(frame.getLocal(local))) {
                    throw new IllegalStateException("Stored uninitialized Netty AttributeKey aliases are unsupported.");
                }
                int count = 0; for (int stack = 0; stack < frame.getStackSize(); stack++) if (value.equals(frame.getStack(stack))) count++;
                int expected = index == dup ? 1 : 2;
                if (count != expected || frame.getStackSize() < depth + expected || !value.equals(frame.getStack(depth))
                        || expected == 2 && !value.equals(frame.getStack(depth + 1))) {
                    throw new IllegalStateException("Unsupported use of an uninitialized Netty AttributeKey allocation.");
                }
                if (index != callIndex) pending.addAll(edges.getOrDefault(index, Set.of()));
            }
            result.add(new Plan(method, allocation, constructor));
        }
        return result;
    }

    private static boolean allocation(Object value, Set<TypeInsnNode> allocations) {
        return value instanceof LabelNode label && next(label) instanceof TypeInsnNode instruction && allocations.contains(instruction);
    }

    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
}
