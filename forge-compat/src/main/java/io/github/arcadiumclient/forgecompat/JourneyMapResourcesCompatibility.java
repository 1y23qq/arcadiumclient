package io.github.arcadiumclient.forgecompat;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class JourneyMapResourcesCompatibility {
    private static final String OWNER = "journeymap/client/io/FileHandler";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/JourneyMapResourcesCompatibility";

    private JourneyMapResourcesCompatibility() { }

    public static boolean isArchiveResource(URL resource) {
        return resource != null && resource.getProtocol().equals("jar");
    }

    public static String archivePath(URL resource) throws IOException {
        var connection = resource.openConnection();
        if (!(connection instanceof JarURLConnection archive)) throw new IOException("Resource is not a JAR URL: " + resource);
        archive.setUseCaches(false);
        return filePath(archive.getJarFileURL());
    }

    public static String filePath(URL resource) throws IOException {
        if (!resource.getProtocol().equals("file")) throw new IOException("Resource is not a file URL: " + resource);
        try { return Path.of(resource.toURI()).toString(); }
        catch (URISyntaxException | IllegalArgumentException exception) { throw new IOException("Invalid resource file URL: " + resource, exception); }
    }

    public static void normalize(ClassNode node) {
        if (!node.name.equals(OWNER)) return;
        MethodNode method = node.methods.stream().filter(value -> value.name.equals("copyResources")
            && value.desc.equals("(Ljava/io/File;Ljava/lang/String;Ljava/lang/String;Z)V")).findFirst().orElse(null);
        if (method == null) return;
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) instructions.add(instruction);
        long existing = instructions.stream().filter(value -> value instanceof MethodInsnNode call && call.owner.equals(BRIDGE)).count();
        if (existing == 3) return;
        MethodInsnNode classification = unique(instructions, Opcodes.INVOKESTATIC, OWNER, "isInJar", "()Z");
        MethodInsnNode path = unique(instructions, Opcodes.INVOKEVIRTUAL, "java/net/URL", "getPath", "()Ljava/lang/String;");
        MethodInsnNode file = unique(instructions, Opcodes.INVOKEVIRTUAL, "java/net/URL", "getFile", "()Ljava/lang/String;");
        int offset = instructions.indexOf(path);
        if (existing != 0 || offset < 1 || offset + 11 >= instructions.size()
            || !variable(instructions.get(offset - 1), Opcodes.ALOAD, 6)
            || !constant(instructions.get(offset + 1), "utf-8")
            || !call(instructions.get(offset + 2), Opcodes.INVOKESTATIC, "java/net/URLDecoder", "decode", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
            || !constant(instructions.get(offset + 3), "file:")
            || !call(instructions.get(offset + 4), Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")
            || instructions.get(offset + 5).getOpcode() != Opcodes.ICONST_1 || instructions.get(offset + 6).getOpcode() != Opcodes.AALOAD
            || !constant(instructions.get(offset + 7), "!/")
            || !call(instructions.get(offset + 8), Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")
            || instructions.get(offset + 9).getOpcode() != Opcodes.ICONST_0 || instructions.get(offset + 10).getOpcode() != Opcodes.AALOAD
            || !variable(instructions.get(offset + 11), Opcodes.ASTORE, 4)) {
            throw new IllegalStateException("Unsupported JourneyMap resource path expression");
        }
        int classifyOffset = instructions.indexOf(classification);
        if (classifyOffset + 1 >= instructions.size() || !variable(instructions.get(classifyOffset + 1), Opcodes.ISTORE, 8)
            || instructions.stream().noneMatch(value -> variable(value, Opcodes.ASTORE, 6))) {
            throw new IllegalStateException("Unsupported JourneyMap resource classification");
        }
        Set<LabelNode> entries = new HashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode branch) entries.add(branch.label);
            else if (instruction instanceof LookupSwitchInsnNode branch) { entries.add(branch.dflt); entries.addAll(branch.labels); }
            else if (instruction instanceof TableSwitchInsnNode branch) { entries.add(branch.dflt); entries.addAll(branch.labels); }
        }
        for (var handler : method.tryCatchBlocks) entries.add(handler.handler);
        for (AbstractInsnNode instruction = path.getNext(); instruction != instructions.get(offset + 11); instruction = instruction.getNext()) {
            if (instruction instanceof FrameNode || instruction instanceof LabelNode label && entries.contains(label))
                throw new IllegalStateException("JourneyMap resource expression contains a control flow entry");
        }
        method.instructions.insertBefore(classification, new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.set(classification, bridge("isArchiveResource", "(Ljava/net/URL;)Z"));
        method.instructions.set(path, bridge("archivePath", "(Ljava/net/URL;)Ljava/lang/String;"));
        for (int index = 1; index <= 10; index++) method.instructions.remove(instructions.get(offset + index));
        method.instructions.set(file, bridge("filePath", "(Ljava/net/URL;)Ljava/lang/String;"));
    }

    private static MethodInsnNode bridge(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, name, descriptor, false);
    }

    private static MethodInsnNode unique(List<AbstractInsnNode> instructions, int opcode, String owner, String name, String descriptor) {
        var matching = instructions.stream().filter(value -> call(value, opcode, owner, name, descriptor)).toList();
        if (matching.size() != 1) throw new IllegalStateException("Unsupported JourneyMap resource call: " + owner + "." + name);
        return (MethodInsnNode) matching.get(0);
    }

    private static boolean call(AbstractInsnNode value, int opcode, String owner, String name, String descriptor) {
        return value instanceof MethodInsnNode call && call.getOpcode() == opcode && !call.itf && call.owner.equals(owner)
            && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static boolean variable(AbstractInsnNode value, int opcode, int index) {
        return value instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index;
    }

    private static boolean constant(AbstractInsnNode value, String expected) {
        return value instanceof LdcInsnNode constant && expected.equals(constant.cst);
    }
}
