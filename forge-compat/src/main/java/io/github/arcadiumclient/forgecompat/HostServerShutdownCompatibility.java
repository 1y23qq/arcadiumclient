package io.github.arcadiumclient.forgecompat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class HostServerShutdownCompatibility {
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String INTEGRATED = "net/minecraft/server/integrated/IntegratedServer";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostServerShutdownCompatibility";
    static final String THREAD_ACCESSOR = "arcadium$serverThreadForShutdown";

    private HostServerShutdownCompatibility() { }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (node.name.equals(mappings.classFromSrg(SERVER))) {
            installThreadAccessor(node, mappings);
            return;
        }
        if (!node.name.equals(mappings.classFromSrg(INTEGRATED))) return;
        String stop = mappings.methodFromSrg(INTEGRATED, "func_71263_m", "()V");
        MethodNode method = node.methods.stream().filter(value -> value.name.equals(stop) && value.desc.equals("()V")).findFirst().orElse(null);
        if (method == null) return;
        MethodInsnNode await = null;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.owner.equals(BRIDGE) && call.name.equals("await")) return;
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("com/google/common/util/concurrent/Futures")
                    && call.name.equals("getUnchecked") && call.desc.equals("(Ljava/util/concurrent/Future;)Ljava/lang/Object;")) {
                if (await != null) throw new IllegalStateException("The integrated server shutdown has multiple task waits.");
                await = call;
            }
        }
        if (await == null) throw new IllegalStateException("The integrated server logout task wait is missing.");
        InsnList state = new InsnList();
        state.add(new VarInsnNode(Opcodes.ALOAD, 0));
        state.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, mappings.classFromSrg(SERVER),
            THREAD_ACCESSOR, "()Ljava/lang/Thread;", false));
        state.add(new VarInsnNode(Opcodes.ALOAD, 0));
        state.add(new FieldInsnNode(Opcodes.GETFIELD, mappings.classFromSrg(SERVER),
            mappings.fieldFromSrg(SERVER, "field_175589_i", "Ljava/util/Queue;"), "Ljava/util/Queue;"));
        method.instructions.insertBefore(await, state);
        await.owner = BRIDGE; await.name = "await";
        await.desc = "(Ljava/util/concurrent/Future;Ljava/lang/Thread;Ljava/util/Queue;)Ljava/lang/Object;";
        method.maxStack = Math.max(method.maxStack, 3);
    }

    private static void installThreadAccessor(ClassNode node, ForgeMappings mappings) {
        String field = mappings.fieldFromSrg(SERVER, "field_175590_aa", "Ljava/lang/Thread;");
        FieldNode thread = node.fields.stream().filter(value -> value.name.equals(field)
            && value.desc.equals("Ljava/lang/Thread;") && (value.access & Opcodes.ACC_STATIC) == 0).findFirst()
            .orElseThrow(() -> new IllegalStateException("The Minecraft server thread field is missing."));
        for (MethodNode existing : node.methods) if (existing.name.equals(THREAD_ACCESSOR)) {
            List<AbstractInsnNode> body = Arrays.stream(existing.instructions.toArray()).filter(value -> value.getOpcode() >= 0).toList();
            if (existing.desc.equals("()Ljava/lang/Thread;") && (existing.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC
                    && body.size() == 3 && body.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                    && body.get(1) instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETFIELD
                    && read.owner.equals(node.name) && read.name.equals(thread.name) && read.desc.equals(thread.desc)
                    && body.get(2).getOpcode() == Opcodes.ARETURN) return;
            throw new IllegalStateException("The Minecraft server shutdown thread accessor conflicts with another method.");
        }
        MethodNode accessor = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
            THREAD_ACCESSOR, "()Ljava/lang/Thread;", null, null);
        accessor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        accessor.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, thread.name, thread.desc));
        accessor.instructions.add(new InsnNode(Opcodes.ARETURN));
        accessor.maxLocals = 1;
        accessor.maxStack = 1;
        node.methods.add(accessor);
    }

    public static Object await(Future<?> future, Thread worker, Queue<?> queue) {
        if (future.isDone() || !(future instanceof ListenableFuture<?> listenable) || !(future instanceof RunnableFuture<?> task)
                || worker == null || worker == Thread.currentThread() || worker.getState() == Thread.State.NEW) {
            return Futures.getUnchecked(future);
        }
        boolean interrupted = false;
        try {
            if (worker.getState() == Thread.State.TERMINATED) interrupted = join(worker);
            else {
                Object signal = new Object(); boolean[] ended = {false};
                Thread watcher = new Thread(() -> {
                    try { worker.join(); }
                    catch (InterruptedException finished) { return; }
                    synchronized (signal) { ended[0] = true; signal.notifyAll(); }
                }, "Arcadium server shutdown join");
                watcher.setDaemon(true);
                listenable.addListener(() -> { synchronized (signal) { signal.notifyAll(); } }, Runnable::run);
                watcher.start();
                try {
                    synchronized (signal) {
                        while (!future.isDone() && !ended[0]) {
                            try { signal.wait(); }
                            catch (InterruptedException retry) { interrupted = true; }
                        }
                    }
                } finally {
                    watcher.interrupt(); interrupted |= join(watcher);
                }
            }
            if (!future.isDone()) {
                synchronized (queue) { queue.remove(future); }
                task.run();
            }
            return Futures.getUnchecked(future);
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static boolean join(Thread thread) {
        boolean interrupted = false;
        while (true) {
            try { thread.join(); return interrupted; }
            catch (InterruptedException retry) { interrupted = true; }
        }
    }
}
