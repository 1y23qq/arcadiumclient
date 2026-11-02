// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

public class GlCallbackFusionTest implements Opcodes {
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CAP = "dev/rdh/argentum/impl/render/entity/instancing/EntityCapture";
    private static final String OWNER = "io/github/arcadiumclient/runtime/test/GeneratedGlGate";
    private static final String TRACE = Type.getInternalName(Trace.class);

    @Test void knownGateRemovesCallbackAllocationAndPreservesAllHookOrdersAndArguments() throws Exception {
        for (String descriptor : List.of("()V", "(FFF)V", "(DDD)V", "(FFFF)V")) {
            Fixture fixture = fixture(descriptor);
            for (int mode = 0; mode < 3; mode++) {
                fixture.capture(mode != 0); Trace.consume = mode == 2;
                Trace.reset(); RenderHookAllocationControl.enabled = false; fixture.invoke();
                List<String> original = List.copyOf(Trace.events); float[] values = Trace.values.clone(); int allocations = Trace.callbacks;
                Trace.reset(); RenderHookAllocationControl.enabled = true; fixture.invoke();
                assertEquals(original, Trace.events, descriptor + "/mode" + mode);
                assertArrayEquals(values, Trace.values);
                assertEquals(1, allocations); assertEquals(0, Trace.callbacks);
            }
        }
        RenderHookAllocationControl.enabled = false;
    }

    @Test void nestedCaptureAndDelegateExceptionsKeepTheOriginalBeforeAfterBoundaries() throws Exception {
        Fixture fixture = fixture("(FFF)V");
        List<String> previous = null;
        for (boolean enabled : new boolean[]{false, true}) {
            RenderHookAllocationControl.enabled = enabled; fixture.capture(true); Trace.consume = false; Trace.reset();
            Trace.nested = () -> {
                try { fixture.capture(false); fixture.invoke(); fixture.capture(true); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            };
            fixture.invoke();
            if (previous == null) previous = List.copyOf(Trace.events); else assertEquals(previous, Trace.events);
        }
        for (boolean enabled : new boolean[]{false, true}) {
            RenderHookAllocationControl.enabled = enabled; fixture.capture(true); Trace.reset(); Trace.throwing = true;
            InvocationTargetException failure = assertThrows(InvocationTargetException.class, fixture::invoke);
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(List.of("before", "current", "capture"), Trace.events);
        }
        Trace.throwing = false; RenderHookAllocationControl.enabled = false;
    }

    @Test void extraCallbackConsumersAndUnknownMixinOwnersAreNotRewritten() {
        ClassNode used = owner("(FFF)V");
        MethodNode move = used.methods.stream().filter(method -> method.name.equals("move")).findFirst().orElseThrow();
        AbstractInsnNode last = move.instructions.getLast();
        move.instructions.insertBefore(last, new VarInsnNode(ALOAD, 3));
        move.instructions.insertBefore(last, new InsnNode(POP));
        assertTrue(GlCallbackFusion.apply(used).isEmpty());
        ClassNode unknown = owner("(FFF)V");
        unknown.methods.get(1).visibleAnnotations.get(0).values.set(1, "another.ModMixin");
        assertTrue(GlCallbackFusion.apply(unknown).isEmpty());
    }

    @Test void aDelegateWithAdditionalEffectsOrNonCancellableConstructorKeepsOriginalCode() {
        ClassNode changed = owner("(FFF)V");
        changed.methods.get(1).instructions.insert(new MethodInsnNode(INVOKESTATIC, TRACE, "unrecognized", "()V", false));
        assertTrue(GlCallbackFusion.apply(changed).isEmpty());
        ClassNode noncancellable = owner("(FFF)V");
        for (AbstractInsnNode instruction : noncancellable.methods.get(0).instructions) if (instruction.getOpcode() == ICONST_1) {
            noncancellable.methods.get(0).instructions.set(instruction, new InsnNode(ICONST_0)); break;
        }
        assertTrue(GlCallbackFusion.apply(noncancellable).isEmpty());
    }

    @Test void aSecondApplicationCannotDuplicateTheFastGate() {
        ClassNode target = owner("(FFF)V");
        assertEquals(1, GlCallbackFusion.apply(target).size());
        assertTrue(GlCallbackFusion.apply(target).isEmpty());
        assertEquals(1, target.methods.stream().filter(method -> method.name.startsWith("arcadium$typedRenderGate$")).count());
    }

    private record Fixture(Class<?> owner, Class<?> capture, java.lang.reflect.Method method, Object[] arguments) {
        void capture(boolean present) throws Exception { capture.getField("active").set(null, present ? capture.getConstructor().newInstance() : null); }
        void invoke() throws Exception { method.invoke(null, arguments); }
    }

    private static Fixture fixture(String descriptor) throws Exception {
        ClassNode node = owner(descriptor);
        assertEquals(1, GlCallbackFusion.apply(node).size());
        Map<String,byte[]> classes = Map.of(OWNER.replace('/', '.'), bytes(node), CAP.replace('/', '.'), capture(), CI.replace('/', '.'), callback());
        ClassLoader loader = new ClassLoader(GlCallbackFusionTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!classes.containsKey(name)) return super.loadClass(name, resolve);
                    Class<?> type = findLoadedClass(name);
                    if (type == null) { byte[] code = classes.get(name); type = defineClass(name, code, 0, code.length); }
                    if (resolve) resolveClass(type); return type;
                }
            }
        };
        Class<?> type = loader.loadClass(OWNER.replace('/', '.')), capture = loader.loadClass(CAP.replace('/', '.'));
        Type[] args = Type.getArgumentTypes(descriptor); Class<?>[] types = new Class<?>[args.length]; Object[] values = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == Type.DOUBLE_TYPE ? double.class : float.class;
            if (args[i] == Type.DOUBLE_TYPE) values[i] = Double.valueOf(1.234567891 + i);
            else values[i] = Float.valueOf(1.2345679f + i);
        }
        return new Fixture(type, capture, type.getMethod("move", types), values);
    }

    private static ClassNode owner(String descriptor) {
        ClassNode node = new ClassNode(); node.version = V1_8; node.access = ACC_PUBLIC; node.name = OWNER; node.superName = "java/lang/Object";
        Type[] args = Type.getArgumentTypes(descriptor); int slots = Arrays.stream(args).mapToInt(Type::getSize).sum();
        String operation = args.length == 0 ? "pushMatrix" : args.length == 4 ? "rotate" : "translate";
        String suffix = args.length == 0 ? "pushEntityMatrix" : args.length == 4 ? "rotateEntity" : "translateEntity";
        String handlerName = "handler$unit$argentum$celeritas$" + suffix;
        Type[] handlerArgs = Arrays.copyOf(args, args.length + 1); handlerArgs[args.length] = Type.getObjectType(CI);
        String handlerDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, handlerArgs);
        MethodNode move = new MethodNode(ACC_PUBLIC | ACC_STATIC, "move", descriptor, null, null);
        event(move.instructions, "before");
        move.instructions.add(new TypeInsnNode(NEW, CI)); move.instructions.add(new InsnNode(DUP)); move.instructions.add(new LdcInsnNode("move"));
        move.instructions.add(new InsnNode(ICONST_1)); move.instructions.add(new MethodInsnNode(INVOKESPECIAL, CI, "<init>", "(Ljava/lang/String;Z)V", false));
        move.instructions.add(new VarInsnNode(ASTORE, slots));
        int local = 0;
        for (Type arg : args) { move.instructions.add(new VarInsnNode(arg.getOpcode(ILOAD), local)); local += arg.getSize(); }
        move.instructions.add(new VarInsnNode(ALOAD, slots)); move.instructions.add(new MethodInsnNode(INVOKESTATIC, OWNER, handlerName, handlerDescriptor, false));
        move.instructions.add(new VarInsnNode(ALOAD, slots)); move.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CI, "isCancelled", "()Z", false));
        LabelNode proceed = new LabelNode(); move.instructions.add(new JumpInsnNode(IFEQ, proceed)); move.instructions.add(new InsnNode(RETURN));
        move.instructions.add(proceed); move.instructions.add(new FrameNode(F_APPEND, 1, new Object[]{CI}, 0, null));
        event(move.instructions, "name"); event(move.instructions, "native"); move.instructions.add(new InsnNode(RETURN));
        move.maxStack = Math.max(4, slots + 1); move.maxLocals = slots + 1; node.methods.add(move);
        MethodNode handler = new MethodNode(ACC_PRIVATE | ACC_STATIC, handlerName, handlerDescriptor, null, null);
        AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = new ArrayList<>(List.of("mixin", "dev.rdh.argentum.mixin.features.model.instancing.GlStateManagerMixin"));
        handler.visibleAnnotations = new ArrayList<>(List.of(merged));
        handler.instructions.add(new MethodInsnNode(INVOKESTATIC, CAP, "current", "()L" + CAP + ";", false)); handler.instructions.add(new VarInsnNode(ASTORE, slots + 1));
        handler.instructions.add(new VarInsnNode(ALOAD, slots + 1)); LabelNode end = new LabelNode(); handler.instructions.add(new JumpInsnNode(IFNULL, end));
        handler.instructions.add(new VarInsnNode(ALOAD, slots + 1)); local = 0;
        for (Type arg : args) { handler.instructions.add(new VarInsnNode(arg.getOpcode(ILOAD), local)); if (arg == Type.DOUBLE_TYPE) handler.instructions.add(new InsnNode(D2F)); local += arg.getSize(); }
        Type[] floats = new Type[args.length]; Arrays.fill(floats, Type.FLOAT_TYPE);
        handler.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CAP, operation, Type.getMethodDescriptor(Type.BOOLEAN_TYPE, floats), false));
        handler.instructions.add(new JumpInsnNode(IFEQ, end)); handler.instructions.add(new VarInsnNode(ALOAD, slots));
        handler.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CI, "cancel", "()V", false)); handler.instructions.add(end);
        handler.instructions.add(new FrameNode(F_APPEND, 1, new Object[]{CAP}, 0, null)); handler.instructions.add(new InsnNode(RETURN));
        handler.maxLocals = slots + 2; handler.maxStack = slots + 1; node.methods.add(handler);
        return node;
    }

    private static byte[] callback() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); writer.visit(V1_8, ACC_PUBLIC, CI, null, "java/lang/Object", null);
        writer.visitField(ACC_PRIVATE, "cancelled", "Z", null, null).visitEnd();
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/String;Z)V", null, null); constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0); constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitMethodInsn(INVOKESTATIC, TRACE, "callback", "()V", false); constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        var cancel = writer.visitMethod(ACC_PUBLIC, "cancel", "()V", null, null); cancel.visitCode(); cancel.visitVarInsn(ALOAD, 0); cancel.visitInsn(ICONST_1); cancel.visitFieldInsn(PUTFIELD, CI, "cancelled", "Z"); cancel.visitInsn(RETURN); cancel.visitMaxs(0, 0); cancel.visitEnd();
        var state = writer.visitMethod(ACC_PUBLIC, "isCancelled", "()Z", null, null); state.visitCode(); state.visitVarInsn(ALOAD, 0); state.visitFieldInsn(GETFIELD, CI, "cancelled", "Z"); state.visitInsn(IRETURN); state.visitMaxs(0, 0); state.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] capture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); writer.visit(V1_8, ACC_PUBLIC, CAP, null, "java/lang/Object", null);
        writer.visitField(ACC_PUBLIC | ACC_STATIC, "active", "L" + CAP + ";", null, null).visitEnd();
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0); constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        var current = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "current", "()L" + CAP + ";", null, null); current.visitCode(); current.visitLdcInsn("current"); current.visitMethodInsn(INVOKESTATIC, TRACE, "event", "(Ljava/lang/String;)V", false); current.visitFieldInsn(GETSTATIC, CAP, "active", "L" + CAP + ";"); current.visitInsn(ARETURN); current.visitMaxs(0, 0); current.visitEnd();
        for (int count : new int[]{0, 3, 4}) {
            String name = count == 0 ? "pushMatrix" : count == 4 ? "rotate" : "translate";
            String descriptor = "(" + "F".repeat(count) + ")Z";
            var method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null); method.visitCode();
            for (int i = 0; i < 4; i++) if (i < count) method.visitVarInsn(FLOAD, i + 1); else method.visitInsn(FCONST_0);
            method.visitMethodInsn(INVOKESTATIC, TRACE, "accept", "(FFFF)Z", false); method.visitInsn(IRETURN); method.visitMaxs(0, 0); method.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static void event(InsnList output, String message) { output.add(new LdcInsnNode(message)); output.add(new MethodInsnNode(INVOKESTATIC, TRACE, "event", "(Ljava/lang/String;)V", false)); }
    public static final class Trace {
        static final List<String> events = new ArrayList<>(); static int callbacks; static boolean consume, throwing; static float[] values = new float[4]; static Runnable nested;
        public static void event(String event) { events.add(event); }
        public static void callback() { callbacks++; }
        public static void unrecognized() { }
        public static boolean accept(float x, float y, float z, float a) {
            event("capture"); values = new float[]{x, y, z, a};
            if (throwing) throw new IllegalStateException("capture failure");
            Runnable call = nested; nested = null; if (call != null) call.run(); return consume;
        }
        static void reset() { events.clear(); callbacks = 0; values = new float[4]; nested = null; throwing = false; }
    }
}
