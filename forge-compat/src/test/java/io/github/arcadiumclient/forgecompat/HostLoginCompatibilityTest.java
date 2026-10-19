package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.points.BeforeNew;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class HostLoginCompatibilityTest {
    private static final String MANAGER = "io/github/arcadiumclient/forgecompat/fixture/LoginManager";
    private static final String SERVER = Type.getInternalName(Server.class), NETWORK = Type.getInternalName(Network.class);
    private static final String PLAYER = Type.getInternalName(Player.class), HANDLER = Type.getInternalName(Handler.class);
    private static final String JOIN = Type.getInternalName(JoinPacket.class), PROBE = Type.getInternalName(Probe.class);
    private static final String SRG_MANAGER = "net/minecraft/server/management/ServerConfigurationManager";
    private static final String SRG_SERVER = "net/minecraft/server/MinecraftServer";
    private static final String SRG_NETWORK = "net/minecraft/network/NetworkManager", SRG_PLAYER = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String SRG_HANDLER = "net/minecraft/network/NetHandlerPlayServer";

    @BeforeEach void reset() { Probe.events.clear(); Probe.nested = null; Probe.fail = false; }

    @Test void verifiesTheTwoArgumentBodyAndPreservesTheProvidedForgeHandlerOnTheJvm() throws Exception {
        Class<?> manager = load(); Server server = new Server(false, "owner"); Object instance = manager.getConstructor(Server.class).newInstance(server);
        Player player = new Player("guest");
        manager.getMethod("onLogin", Network.class, Player.class).invoke(instance, new Network(), player);
        assertNotNull(player.handler); assertTrue(player.handler.createdAfterPreparation);
        assertEquals(List.of("prepare", "construct", "join", "send", "forge"), Probe.events);
        Probe.events.clear(); server.prepared = false;
        Player second = new Player("guest2"); Handler supplied = new Handler(server, new Network(), second); Probe.events.clear();
        manager.getMethod("initializeConnectionToPlayer", Network.class, Player.class, Handler.class)
            .invoke(instance, new Network(), second, supplied);
        assertSame(supplied, second.handler);
        assertEquals(List.of("prepare", "sync", "serialize", "join", "send", "forge", "login"), Probe.events);
    }

    @Test void preservesTheSinglePlayerOwnerRegistryCondition() throws Exception {
        Class<?> manager = load(); Server server = new Server(true, "owner"); Object instance = manager.getConstructor(Server.class).newInstance(server);
        Player owner = new Player("owner"); Handler handler = new Handler(server, new Network(), owner); Probe.events.clear();
        manager.getMethod("initializeConnectionToPlayer", Network.class, Player.class, Handler.class).invoke(instance, new Network(), owner, handler);
        assertEquals(List.of("prepare", "join", "send", "forge", "login"), Probe.events);
        Player guest = new Player("guest"); Handler guestHandler = new Handler(server, new Network(), guest); Probe.events.clear();
        manager.getMethod("initializeConnectionToPlayer", Network.class, Player.class, Handler.class).invoke(instance, new Network(), guest, guestHandler);
        assertEquals(List.of("prepare", "sync", "serialize", "join", "send", "forge", "login"), Probe.events);
    }

    @Test void propagatesFailuresAndSupportsReentrantLoginsWithoutSharedHandlerState() throws Exception {
        Class<?> manager = load(); Server server = new Server(false, "owner"); Object instance = manager.getConstructor(Server.class).newInstance(server);
        var login = manager.getMethod("initializeConnectionToPlayer", Network.class, Player.class, Handler.class);
        Player outer = new Player("outer"), inner = new Player("inner");
        Handler outerHandler = new Handler(server, new Network(), outer), innerHandler = new Handler(server, new Network(), inner);
        Probe.events.clear();
        Probe.nested = () -> { try { login.invoke(instance, new Network(), inner, innerHandler); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } };
        login.invoke(instance, new Network(), outer, outerHandler);
        assertSame(outerHandler, outer.handler); assertSame(innerHandler, inner.handler);
        assertEquals(2, Collections.frequency(Probe.events, "login")); assertEquals(2, Collections.frequency(Probe.events, "forge"));
        Probe.events.clear(); Probe.fail = true;
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> login.invoke(instance, new Network(), outer, outerHandler));
        assertInstanceOf(IllegalStateException.class, failure.getCause()); assertEquals(List.of("prepare"), Probe.events);
        Probe.fail = false; Probe.events.clear();
        login.invoke(instance, new Network(), outer, outerHandler);
        assertEquals(1, Collections.frequency(Probe.events, "login")); assertSame(outerHandler, outer.handler);
    }

    @Test void isIdempotentAndLeavesOtherClassesUntouched() throws Exception {
        ClassNode node = fixture(); var mappings = fixtureMappings(); HostLoginCompatibility.normalize(node, mappings);
        byte[] once = bytes(node); HostLoginCompatibility.normalize(node, mappings); assertArrayEquals(once, bytes(node));
        ClassNode other = fixture(); other.name = "fixture/Other"; byte[] before = bytes(other);
        HostLoginCompatibility.normalize(other, mappings); assertArrayEquals(before, bytes(other));
    }

    @Test void loginDoesNotLinkTheRemovedClientPackSharingService() throws Exception {
        ClassNode node = fixture(); HostLoginCompatibility.normalize(node, fixtureMappings());
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.startsWith("org/polyfrost/polyplus/"), call.owner);
                    assertFalse(call.owner.endsWith("/HostSharedPack"), call.owner);
                }
            }
        }
        Class<?> manager = load();
        Server server = new Server(false, "owner");
        Object instance = manager.getConstructor(Server.class).newInstance(server);
        Player player = new Player("guest");
        Handler supplied = new Handler(server, new Network(), player); Probe.events.clear();
        manager.getMethod("initializeConnectionToPlayer", Network.class, Player.class, Handler.class)
            .invoke(instance, new Network(), player, supplied);
        assertEquals(List.of("prepare", "sync", "serialize", "join", "send", "forge", "login"), Probe.events);
    }

    @Test void rejectsUnknownHandlerShapesBeforeChangingTheForgeBody() throws Exception {
        ClassNode node = fixture(); MethodNode method = method(node, "initializeConnectionToPlayer");
        var assignment = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("handler")).findFirst().orElseThrow();
        method.instructions.set(assignment, new InsnNode(Opcodes.POP2));
        byte[] before = bytes(node);
        assertThrows(IllegalStateException.class, () -> HostLoginCompatibility.normalize(node, fixtureMappings()));
        assertArrayEquals(before, bytes(node));
    }

    @Test void restoresActualOslTargetsAndRetainsEveryOriginalForgeInstruction() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path game = root.resolve("build/forge-compat/runtime-qa/20261015-141452-d8ba1222de/jei/game");
        Path runtime = game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isRegularFile(runtime) && Files.isRegularFile(tiny));
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) {
            mappings = new ForgeMappings(resolver(Files.readString(tiny)), reader);
        }
        Path patched;
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/game"))) { patched = files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        new ForgeJarTransformer().index(patched, mappings, true);
        ClassNode node = read(runtime, mappings.classFromSrg(SRG_MANAGER) + ".class");
        MethodNode original = method(node, "initializeConnectionToPlayer");
        List<AbstractInsnNode> originalInstructions = List.of(original.instructions.toArray());
        List<String> originalOpcodes = opcodes(original);
        String oldName = mappings.methodFromSrg(SRG_MANAGER, "func_72355_a", "(L" + SRG_NETWORK + ";L" + SRG_PLAYER + ";)V");
        assertTrue(node.methods.stream().noneMatch(method -> method.name.equals(oldName)));
        Map<String,MethodNode> callbacks = new LinkedHashMap<>();
        for (String module : List.of("osl-networking-impl-", "osl-registries-")) {
            Path jar;
            try (var paths = Files.list(game.resolve(".fabric/processedMods"))) { jar = paths.filter(path -> path.getFileName().toString().startsWith(module)).findFirst().orElseThrow(); }
            String name = module.startsWith("osl-networking-") ? "net/ornithemc/osl/networking/impl/mixin/common/PlayerManagerMixin.class" : "net/ornithemc/osl/registries/impl/mixin/common/PlayerManagerMixin.class";
            ClassNode mixin = read(jar, name); MethodNode callback = mixin.methods.stream().filter(method -> method.name.contains("handleLogin") || method.name.contains("syncRegistries")).findFirst().orElseThrow();
            assertTrue(callback.desc.startsWith("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"));
            var inject = callback.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            assertEquals(List.of(oldName), value(inject, "method"));
            assertFalse(inject.values.contains("require"));
            callbacks.put(module, callback);
        }
        HostLoginCompatibility.normalize(node, mappings);
        MethodNode restored = method(node, oldName);
        String packet = mappings.classFromSrg("net/minecraft/network/play/server/S01PacketJoinGame");
        var point = new BeforeNew(new InjectionPointData(HostCompatibilityPatchesTest.context(node.name), "NEW", List.of(), "L" + packet + ";", "", -1, -1, "osl-registry-sync", 0));
        List<AbstractInsnNode> targets = new ArrayList<>(); point.find(restored.desc, restored.instructions, targets); assertEquals(1, targets.size());
        assertEquals(1, Arrays.stream(restored.instructions.toArray()).filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).count());
        int last = -1;
        for (var instruction : originalInstructions) { int index = original.instructions.indexOf(instruction); assertTrue(index > last); last = index; }
        assertEquals(originalOpcodes, originalInstructions.stream().filter(instruction -> instruction.getOpcode() >= 0).map(HostLoginCompatibilityTest::symbol).toList());
        assertEquals(0, Arrays.stream(restored.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call && call.name.startsWith("arcadium$")).count());
        ClassNode serialized = new ClassNode(); new ClassReader(bytes(node)).accept(serialized, 0);
        for (MethodNode method : serialized.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(serialized.name, method);
        assertEquals(publicCalls(callbacks.get("osl-networking-impl-")), publicCalls(method(node, "arcadium$fireConnectionLogin")));
        assertEquals(publicCalls(callbacks.get("osl-registries-")), publicCalls(method(node, "arcadium$syncLoginRegistries")));
    }

    private static Class<?> load() throws Exception {
        ClassNode node = fixture(); HostLoginCompatibility.normalize(node, fixtureMappings());
        Map<String,String> api = Map.ofEntries(
            Map.entry("net/ornithemc/osl/core/api/events/Event", Type.getInternalName(Event.class)),
            Map.entry("net/ornithemc/osl/core/api/util/NamespacedIdentifier", Type.getInternalName(Identifier.class)),
            Map.entry("net/ornithemc/osl/core/api/util/function/IOConsumer", Type.getInternalName(IOConsumer.class)),
            Map.entry("net/ornithemc/osl/networking/api/PacketBuffer", Type.getInternalName(Buffer.class)),
            Map.entry("net/ornithemc/osl/networking/impl/access/ServerNetworkHandlerAccess", Type.getInternalName(Access.class)),
            Map.entry("net/ornithemc/osl/networking/impl/server/ServerConnectionContext", Type.getInternalName(Context.class)),
            Map.entry("net/ornithemc/osl/networking/api/server/ServerConnectionEvents", Type.getInternalName(Events.class)),
            Map.entry("net/ornithemc/osl/registries/impl/Constants", Type.getInternalName(Constants.class)),
            Map.entry("net/ornithemc/osl/registries/impl/registry/sync/SyncedRegistriesPacketSerializer", Type.getInternalName(Serializer.class)),
            Map.entry("net/ornithemc/osl/networking/api/server/ServerPlayNetworking", Type.getInternalName(Networking.class)));
        ClassWriter writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return api.getOrDefault(name, name); } }));
        class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(MANAGER.replace('/', '.'), bytes, 0, bytes.length); } }
        return new Loader().define(writer.toByteArray());
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, MANAGER, null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "server", "L" + SERVER + ";", null, null));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(L" + SERVER + ";)V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1); constructor.visitFieldInsn(Opcodes.PUTFIELD, MANAGER, "server", "L" + SERVER + ";"); constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(2, 2); node.methods.add(constructor);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "initializeConnectionToPlayer", "(L" + NETWORK + ";L" + PLAYER + ";L" + HANDLER + ";)V", null, null);
        Label ready = new Label(); method.visitVarInsn(Opcodes.ALOAD, 1); method.visitJumpInsn(Opcodes.IFNONNULL, ready);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException"); method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false); method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(ready); method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitFieldInsn(Opcodes.GETFIELD, MANAGER, "server", "L" + SERVER + ";"); method.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE, "prepare", "(L" + SERVER + ";)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 2); method.visitVarInsn(Opcodes.ALOAD, 3); method.visitFieldInsn(Opcodes.PUTFIELD, PLAYER, "handler", "L" + HANDLER + ";");
        method.visitVarInsn(Opcodes.ALOAD, 3); method.visitTypeInsn(Opcodes.NEW, JOIN); method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL, JOIN, "<init>", "()V", false); method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HANDLER, "send", "(L" + JOIN + ";)V", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE, "forge", "()V", false); method.visitInsn(Opcodes.RETURN); method.visitMaxs(3, 4); node.methods.add(method); return node;
    }

    private static ForgeMappings fixtureMappings() throws Exception {
        String tiny = "tiny\t2\t0\tofficial\tintermediary\nc\tlx\t" + MANAGER + "\n\tf\tLsv;\th\tserver\n\tm\t(Lek;Llf;)V\ta\tonLogin\nc\tek\t" + NETWORK + "\nc\tlf\t" + PLAYER + "\n\tf\tLlm;\ta\thandler\n\tm\t()Ljava/lang/String;\tn\tname\nc\tlm\t" + HANDLER + "\nc\tgt\t" + JOIN + "\nc\tsv\t" + SERVER + "\n\tm\t()Z\ts\tsingleplayer\n\tm\t()Ljava/lang/String;\to\towner\n";
        String srg = "CL: lx " + SRG_MANAGER + "\nCL: ek " + SRG_NETWORK + "\nCL: lf " + SRG_PLAYER + "\nCL: lm " + SRG_HANDLER + "\nCL: sv " + SRG_SERVER + "\nCL: gt net/minecraft/network/play/server/S01PacketJoinGame\nFD: lx/h " + SRG_MANAGER + "/field_72400_f\nFD: lf/a " + SRG_PLAYER + "/field_71135_a\nMD: lx/a (Lek;Llf;)V " + SRG_MANAGER + "/func_72355_a (L" + SRG_NETWORK + ";L" + SRG_PLAYER + ";)V\nMD: sv/s ()Z " + SRG_SERVER + "/func_71262_S ()Z\nMD: sv/o ()Ljava/lang/String; " + SRG_SERVER + "/func_71214_G ()Ljava/lang/String;\nMD: lf/n ()Ljava/lang/String; " + SRG_PLAYER + "/func_70005_c_ ()Ljava/lang/String;\n";
        return new ForgeMappings(resolver(tiny), new StringReader(srg));
    }
    private static MappingResolver resolver(String tiny) throws Exception { MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(tiny), tree); var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true); return (MappingResolver) constructor.newInstance(tree, "intermediary"); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(ClassNode owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(owner.version, owner.access, owner.name, null, owner.superName, null); node.methods.add(method); return bytes(node); }
    private static ClassNode read(Path jar, String name) throws Exception { ClassNode node = new ClassNode(); try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name))) { new ClassReader(input.readAllBytes()).accept(node, 0); } return node; }
    private static Object value(AnnotationNode annotation, String key) { for (int i=0; annotation.values != null && i<annotation.values.size(); i+=2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i+1); return null; }
    private static List<String> opcodes(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).map(HostLoginCompatibilityTest::symbol).toList(); }
    private static String symbol(AbstractInsnNode instruction) { return instruction instanceof MethodInsnNode call ? call.owner + "." + call.name + call.desc : instruction instanceof FieldInsnNode field ? field.owner + "." + field.name + ":" + field.desc : instruction instanceof VarInsnNode variable ? "V:"+variable.getOpcode()+":"+variable.var : Integer.toString(instruction.getOpcode()); }
    private static List<String> publicCalls(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode).map(HostLoginCompatibilityTest::symbol).toList(); }

    public static final class Server { public boolean prepared; public final boolean single; public final String owner; public Server(boolean single, String owner) { this.single=single;this.owner=owner; } public boolean singleplayer(){return single;} public String owner(){return owner;} }
    public static final class Network {}
    public static final class Player { public Handler handler; private final String name; public Player(String name){this.name=name;} public String name(){return name;} }
    public interface Access { Context osl$networking$connectionContext(); }
    public static final class Context {}
    public static final class Handler implements Access { public final boolean createdAfterPreparation; public Handler(Server server,Network network,Player player){createdAfterPreparation=server.prepared;player.handler=this;Probe.events.add("construct");} public void send(JoinPacket packet){Probe.events.add("send");} public Context osl$networking$connectionContext(){return new Context();} }
    public static final class JoinPacket { public JoinPacket(){Probe.events.add("join");} }
    public static final class Event { public Object invoker(){return (Consumer<Context>) context -> Probe.events.add("login");} }
    public static final class Events { public static final Event LOGIN=new Event(); }
    public static final class Identifier {}
    public static final class Constants { public static final Identifier OSL_REGISTRY_SYNC_CHANNEL=new Identifier(); }
    public static final class Buffer {}
    public interface IOConsumer<T> { void accept(T value) throws IOException; }
    public static final class Serializer { public static void serialize(Buffer buffer){Probe.events.add("serialize");} }
    public static final class Networking { public static void sendNoCheck(Player player,Identifier channel,IOConsumer<Buffer> serializer) throws IOException { Probe.events.add("sync");serializer.accept(new Buffer()); } }
    public static final class Probe { static final List<String> events=new ArrayList<>();static Runnable nested;static boolean fail;public static void prepare(Server server){events.add("prepare");if(fail)throw new IllegalStateException("fixture login failed");server.prepared=true;}public static void forge(){events.add("forge");Runnable callback=nested;nested=null;if(callback!=null)callback.run();} }
}
