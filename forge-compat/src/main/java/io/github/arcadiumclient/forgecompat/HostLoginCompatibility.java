package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public final class HostLoginCompatibility {
    private static final String MANAGER = "net/minecraft/server/management/ServerConfigurationManager";
    private static final String NETWORK = "net/minecraft/network/NetworkManager";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String HANDLER = "net/minecraft/network/NetHandlerPlayServer";
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String EVENTS = "net/ornithemc/osl/core/api/events/Event";
    private static final String IDENTIFIER = "net/ornithemc/osl/core/api/util/NamespacedIdentifier";
    private static final String IO_CONSUMER = "net/ornithemc/osl/core/api/util/function/IOConsumer";
    private static final String BUFFER = "net/ornithemc/osl/networking/api/PacketBuffer";
    private static final String SYNC = "arcadium$syncLoginRegistries";
    private static final String LOGIN = "arcadium$fireConnectionLogin";

    private HostLoginCompatibility() {}

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        String manager = mappings.classFromSrg(MANAGER);
        if (!node.name.equals(manager)) return;
        String network = mappings.classFromSrg(NETWORK), player = mappings.classFromSrg(PLAYER);
        String handler = mappings.classFromSrg(HANDLER), server = mappings.classFromSrg(SERVER);
        String legacyDescriptor = "(L" + NETWORK + ";L" + PLAYER + ";)V";
        String legacyName = mappings.methodFromSrg(MANAGER, "func_72355_a", legacyDescriptor);
        String twoArguments = "(L" + network + ";L" + player + ";)V";
        String threeArguments = "(L" + network + ";L" + player + ";L" + handler + ";)V";
        if (node.methods.stream().anyMatch(method -> method.name.equals(legacyName) && method.desc.equals(twoArguments))) return;
        MethodNode original = node.methods.stream().filter(method -> method.name.equals("initializeConnectionToPlayer")
            && method.desc.equals(threeArguments)).findFirst().orElse(null);
        if (original == null) throw unsupported("the Forge login method is missing");
        if ((original.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || node.methods.stream().anyMatch(method -> method.name.equals(SYNC) || method.name.equals(LOGIN))) {
            throw unsupported("the Forge login method shape is unsupported");
        }
        String playerHandler = mappings.fieldFromSrg(PLAYER, "field_71135_a", "L" + HANDLER + ";");
        String managerServer = mappings.fieldFromSrg(MANAGER, "field_72400_f", "L" + SERVER + ";");
        String joinPacket = mappings.classFromSrg("net/minecraft/network/play/server/S01PacketJoinGame");
        List<AbstractInsnNode> uses = new ArrayList<>(), joins = new ArrayList<>(), returns = new ArrayList<>();
        for (AbstractInsnNode instruction : original.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var == 3) uses.add(instruction);
            if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW && allocation.desc.equals(joinPacket)) joins.add(instruction);
            if (instruction.getOpcode() == Opcodes.RETURN) returns.add(instruction);
        }
        if (uses.isEmpty() || uses.get(0).getOpcode() != Opcodes.ALOAD || joins.size() != 1 || returns.size() != 1
                || !(previous(uses.get(0)) instanceof VarInsnNode receiver) || receiver.getOpcode() != Opcodes.ALOAD || receiver.var != 2
                || !(next(uses.get(0)) instanceof FieldInsnNode assignment) || assignment.getOpcode() != Opcodes.PUTFIELD
                || !assignment.owner.equals(player) || !assignment.name.equals(playerHandler) || !assignment.desc.equals("L" + handler + ";")) {
            throw unsupported("the Forge handler assignment or join-packet position changed");
        }
        MethodNode restored = expanded(node, original);
        AbstractInsnNode firstHandler = null;
        for (AbstractInsnNode instruction : restored.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var == 3) { firstHandler = instruction; break; }
        }
        restored.name = legacyName;
        restored.desc = twoArguments;
        restored.signature = null;
        if (restored.parameters != null && restored.parameters.size() >= 3) restored.parameters = new ArrayList<>(restored.parameters.subList(0, 2));
        if (restored.visibleParameterAnnotations != null) restored.visibleParameterAnnotations = java.util.Arrays.copyOf(restored.visibleParameterAnnotations, 2);
        if (restored.invisibleParameterAnnotations != null) restored.invisibleParameterAnnotations = java.util.Arrays.copyOf(restored.invisibleParameterAnnotations, 2);
        restored.visibleAnnotableParameterCount = Math.min(restored.visibleAnnotableParameterCount, 2);
        restored.invisibleAnnotableParameterCount = Math.min(restored.invisibleAnnotableParameterCount, 2);
        InsnList initialize = new InsnList();
        initialize.add(new InsnNode(Opcodes.ACONST_NULL)); initialize.add(new VarInsnNode(Opcodes.ASTORE, 3));
        restored.instructions.insert(initialize);
        InsnList construct = new InsnList();
        construct.add(new TypeInsnNode(Opcodes.NEW, handler)); construct.add(new InsnNode(Opcodes.DUP));
        construct.add(new VarInsnNode(Opcodes.ALOAD, 0)); construct.add(new FieldInsnNode(Opcodes.GETFIELD, manager, managerServer, "L" + server + ";"));
        construct.add(new VarInsnNode(Opcodes.ALOAD, 1)); construct.add(new VarInsnNode(Opcodes.ALOAD, 2));
        construct.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, handler, "<init>", "(L" + server + ";L" + network + ";L" + player + ";)V", false));
        construct.add(new VarInsnNode(Opcodes.ASTORE, 3));
        restored.instructions.insertBefore(firstHandler, construct);
        restored.maxStack += 6;

        MethodNode sync = registryDispatch(mappings, server, player);
        MethodNode login = loginDispatch(player, handler, playerHandler);
        InsnList beforeJoin = new InsnList();
        beforeJoin.add(new VarInsnNode(Opcodes.ALOAD, 0)); beforeJoin.add(new FieldInsnNode(Opcodes.GETFIELD, manager, managerServer, "L" + server + ";"));
        beforeJoin.add(new VarInsnNode(Opcodes.ALOAD, 2));
        beforeJoin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, manager, SYNC, sync.desc, false));
        InsnList beforeReturn = new InsnList(); beforeReturn.add(new VarInsnNode(Opcodes.ALOAD, 2));
        beforeReturn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, manager, LOGIN, login.desc, false));
        original.instructions.insertBefore(joins.get(0), beforeJoin);
        original.instructions.insertBefore(returns.get(0), beforeReturn);
        original.maxStack += 2;
        node.methods.add(restored); node.methods.add(sync); node.methods.add(login);
    }

    private static MethodNode expanded(ClassNode owner, MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(owner.version, owner.access, owner.name, owner.signature, owner.superName, owner.interfaces.toArray(String[]::new));
        method.accept(writer); writer.visitEnd();
        ClassNode expanded = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(expanded, ClassReader.EXPAND_FRAMES);
        return expanded.methods.get(0);
    }

    private static MethodNode loginDispatch(String player, String handler, String playerHandler) {
        String access = "net/ornithemc/osl/networking/impl/access/ServerNetworkHandlerAccess";
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, LOGIN, "(L" + player + ";)V", null, null);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, player, playerHandler, "L" + handler + ";");
        method.visitTypeInsn(Opcodes.CHECKCAST, access);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, access, "osl$networking$connectionContext", "()Lnet/ornithemc/osl/networking/impl/server/ServerConnectionContext;", true);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/ornithemc/osl/networking/api/server/ServerConnectionEvents", "LOGIN", "L" + EVENTS + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENTS, "invoker", "()Ljava/lang/Object;", false);
        method.visitTypeInsn(Opcodes.CHECKCAST, "java/util/function/Consumer"); method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", true);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(2, 2);
        return method;
    }

    private static MethodNode registryDispatch(ForgeMappings mappings, String server, String player) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, SYNC, "(L" + server + ";L" + player + ";)V", null, null);
        LabelNode send = new LabelNode(), end = new LabelNode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, server, mappings.methodFromSrg(SERVER, "func_71262_S", "()Z"), "()Z", false);
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, send));
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, server, mappings.methodFromSrg(SERVER, "func_71214_G", "()Ljava/lang/String;"), "()Ljava/lang/String;", false);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, player, mappings.methodFromSrg(PLAYER, "func_70005_c_", "()Ljava/lang/String;"), "()Ljava/lang/String;", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, end));
        method.instructions.add(send); method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/ornithemc/osl/registries/impl/Constants", "OSL_REGISTRY_SYNC_CHANNEL", "L" + IDENTIFIER + ";");
        method.visitInvokeDynamicInsn("accept", "()L" + IO_CONSUMER + ";", new Handle(Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
            Type.getMethodType("(Ljava/lang/Object;)V"), new Handle(Opcodes.H_INVOKESTATIC,
                "net/ornithemc/osl/registries/impl/registry/sync/SyncedRegistriesPacketSerializer", "serialize", "(L" + BUFFER + ";)V", false),
            Type.getMethodType("(L" + BUFFER + ";)V"));
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/ornithemc/osl/networking/api/server/ServerPlayNetworking", "sendNoCheck",
            "(L" + player + ";L" + IDENTIFIER + ";L" + IO_CONSUMER + ";)V", false);
        method.instructions.add(end); method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(3, 2);
        return method;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node;
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0); return node;
    }
    private static IllegalStateException unsupported(String reason) {
        return new IllegalStateException("Cannot adapt the Forge player login method: " + reason + ".");
    }
}
