package io.github.arcadiumclient.forgecompat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ForgeEventRuntimeProbe {
    private static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String TICK = "net/minecraftforge/fml/common/gameevent/TickEvent";
    private static final String CLIENT = TICK + "$ClientTickEvent";
    private static final List<String> CALLS = new ArrayList<>();

    public static void record(String name) { CALLS.add(name); }
    private static void stage(String value) { System.out.println("ARCADIUM_EVENT_PROBE " + value); }

    public static void main(String[] args) {
        try {
            Path output = Path.of(args[1]);
            ForgeRuntimeHeadlessProbe.run(Path.of(args[0]), output, target -> verify(target, output),
                (urls, parent) -> new ForgeEventMetadataTest.NestedLoader(urls, parent, true));
            stage("SUCCESS");
        } catch (Throwable failure) {
            while ((failure instanceof InvocationTargetException || failure instanceof ExceptionInInitializerError) && failure.getCause() != null) failure = failure.getCause();
            stage("FAILED " + failure.getClass().getName()); failure.printStackTrace(); System.exit(1);
        }
    }

    private static void verify(URLClassLoader target, Path output) throws Exception {
        IClassTransformer transformer = (IClassTransformer) target.loadClass("net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer")
            .getConstructor().newInstance();
        ClassNode world = transformedNode(target, transformer, "net/minecraftforge/event/world/WorldEvent");
        require(world.fields.stream().anyMatch(field -> field.name.equals("LISTENER_LIST")), "WorldEvent listener list was not generated");
        require(world.methods.stream().anyMatch(method -> method.name.equals("<init>") && method.desc.equals("()V")), "WorldEvent default constructor was not generated");
        stage("WORLD_EVENT_TRANSFORMED");
        Class<?> tick = prepare(target, transformer, TICK), client = prepare(target, transformer, CLIENT);
        Object parent = tick.getConstructor().newInstance(), child = client.getConstructor().newInstance();
        Class<?> event = target.loadClass(EVENT.replace('/', '.'));
        Object parentList = event.getMethod("getListenerList").invoke(parent), childList = event.getMethod("getListenerList").invoke(child);
        var parentField = parentList.getClass().getDeclaredField("parent"); parentField.setAccessible(true);
        require(parentField.get(childList) == parentList, "ClientTickEvent lost ListenerList inheritance");
        Class<?> phase = target.loadClass((TICK + "$Phase").replace('/', '.'));
        @SuppressWarnings({"unchecked", "rawtypes"}) Object start = Enum.valueOf((Class) phase, "START");
        Object originalChild = client.getConstructor(phase).newInstance(start);
        require(tick.getField("phase").get(originalChild) == start, "The original event constructor changed");
        stage("REAL_EVENT_HIERARCHY_READY");
        Class<?> listenerType = define(target, "fixture.ForgeEventListener", listener());
        Object listener = listenerType.getConstructor().newInstance();
        Class<?> busType = target.loadClass("net.minecraftforge.fml.common.eventhandler.EventBus");
        Object bus = busType.getConstructor().newInstance();
        busType.getMethod("register", Object.class).invoke(bus, listener);
        CALLS.clear();
        require(Boolean.FALSE.equals(busType.getMethod("post", event).invoke(bus, originalChild)), "A normal event was canceled");
        require(CALLS.size() == 2 && CALLS.contains("parent") && CALLS.contains("child"), "Real EventBus did not dispatch inherited and child listeners");
        stage("PUBLIC_EVENT_BUS_DISPATCHED");
        String canceledName = "fixture/CancelableTick";
        byte[] canceledBytes = transformer.transform(canceledName.replace('/', '.'), canceledName.replace('/', '.'), cancelableEvent());
        Class<?> cancelable = define(target, canceledName.replace('/', '.'), canceledBytes);
        Object canceled = cancelable.getConstructor().newInstance();
        require(Boolean.TRUE.equals(event.getMethod("isCancelable").invoke(canceled)), "Cancelable annotation was not transformed");
        require(Boolean.TRUE.equals(event.getMethod("hasResult").invoke(canceled)), "HasResult annotation was not transformed");
        event.getMethod("setCanceled", boolean.class).invoke(canceled, true);
        CALLS.clear();
        require(Boolean.TRUE.equals(busType.getMethod("post", event).invoke(bus, canceled)), "Canceled EventBus result was lost");
        require(CALLS.isEmpty(), "Default listeners unexpectedly received a canceled event");
        busType.getMethod("unregister", Object.class).invoke(bus, listener);
        CALLS.clear(); busType.getMethod("post", event).invoke(bus, client.getConstructor().newInstance());
        require(CALLS.isEmpty(), "EventBus unregister did not remove listeners");
        Class<?> handler = target.loadClass("net.minecraftforge.fml.common.eventhandler.ASMEventHandler");
        var generated = handler.getDeclaredField("LOADER"); generated.setAccessible(true);
        require(((ClassLoader) generated.get(null)).getParent() == target, "Generated listeners have the wrong host parent");
        stage("CANCEL_FLAGS_AND_UNREGISTER_READY");
        verifyNaturalRegistrations(target, busType, event);
        Files.writeString(output.resolve("event-result.json"), "{\"worldEventTransformed\":true,\"realTickHierarchy\":true,\"listenerListInheritance\":true,\"generatedDefaultConstructors\":true,\"originalConstructor\":true,\"publicEventBusRegistration\":true,\"parentAndChildDispatch\":true,\"cancelAndHasResultAnnotations\":true,\"unregister\":true,\"generatedListenerParent\":true,\"naturalNestedEventHierarchy\":true,\"failureFamiliesRegisteredAndPosted\":16,\"guestModsExecuted\":false}\n");
    }

    private static Class<?> prepare(URLClassLoader target, IClassTransformer transformer, String name) throws Exception {
        var find = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class); find.setAccessible(true);
        require(find.invoke(target, name.replace('/', '.')) == null, "Trusted event was loaded before transformation");
        byte[] bytes;
        try (var input = target.getResourceAsStream(name + ".class")) { require(input != null, "Trusted event is missing"); bytes = input.readAllBytes(); }
        return define(target, name.replace('/', '.'), transformer.transform(name.replace('/', '.'), name.replace('/', '.'), bytes));
    }
    private static ClassNode transformedNode(URLClassLoader target, IClassTransformer transformer, String name) throws Exception {
        byte[] bytes;
        try (var input = target.getResourceAsStream(name + ".class")) { require(input != null, "Trusted event is missing"); bytes = input.readAllBytes(); }
        ClassNode node = new ClassNode(); new ClassReader(transformer.transform(name.replace('/', '.'), name.replace('/', '.'), bytes)).accept(node, 0); return node;
    }
    private static Class<?> define(ClassLoader target, String name, byte[] bytes) throws Exception {
        var define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class); define.setAccessible(true);
        return (Class<?>) define.invoke(target, name, bytes, 0, bytes.length);
    }
    private static byte[] listener() {
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/ForgeEventListener", null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        for (String type : List.of(TICK, CLIENT)) {
            String label = type.equals(TICK) ? "parent" : "child";
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "on" + label, "(L" + type + ";)V", null, null);
            method.visitAnnotation("Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;", true).visitEnd();
            method.visitLdcInsn(label); method.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/arcadiumclient/forgecompat/ForgeEventRuntimeProbe", "record", "(Ljava/lang/String;)V", false);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 2); method.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static void verifyNaturalRegistrations(URLClassLoader target, Class<?> busType, Class<?> event) throws Exception {
        List<String> types = List.of(
            "net/minecraftforge/event/world/WorldEvent$Load",
            "net/minecraftforge/event/world/WorldEvent$Unload",
            "net/minecraftforge/event/entity/EntityEvent$EntityConstructing",
            "net/minecraftforge/fml/common/gameevent/PlayerEvent$PlayerLoggedInEvent",
            "net/minecraftforge/fml/client/event/ConfigChangedEvent$OnConfigChangedEvent",
            "net/minecraftforge/event/entity/player/PlayerEvent$Clone",
            "net/minecraftforge/fml/common/network/FMLNetworkEvent$ClientConnectedToServerEvent",
            "net/minecraftforge/client/event/GuiScreenEvent$DrawScreenEvent$Post",
            "net/minecraftforge/client/event/GuiScreenEvent$InitGuiEvent$Pre",
            "net/minecraftforge/client/event/GuiScreenEvent$MouseInputEvent$Post",
            "net/minecraftforge/client/event/GuiScreenEvent$KeyboardInputEvent$Post",
            "net/minecraftforge/client/event/GuiScreenEvent$ActionPerformedEvent$Post",
            "net/minecraftforge/event/world/ChunkEvent$Load",
            "net/minecraftforge/client/event/TextureStitchEvent$Pre",
            "net/minecraftforge/client/event/RenderGameOverlayEvent$Post", CLIENT);
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/MetadataEventListener", null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        for (int index = 0; index < types.size(); index++) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "event" + index, "(L" + types.get(index) + ";)V", null, null);
            method.visitAnnotation("Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;", true).visitEnd();
            method.visitLdcInsn("event" + index); method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "io/github/arcadiumclient/forgecompat/ForgeEventRuntimeProbe", "record", "(Ljava/lang/String;)V", false);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 2); method.visitEnd();
        }
        writer.visitEnd();
        Class<?> listenerType = define(target, "fixture.MetadataEventListener", writer.toByteArray());
        Object listener = listenerType.getConstructor().newInstance(), bus = busType.getConstructor().newInstance();
        busType.getMethod("register", Object.class).invoke(bus, listener);
        for (int index = 0; index < types.size(); index++) {
            Class<?> type = target.loadClass(types.get(index).replace('/', '.'));
            Object value = type.getConstructor().newInstance();
            CALLS.clear(); busType.getMethod("post", event).invoke(bus, value);
            require(CALLS.equals(List.of("event" + index)), "The real EventBus did not register and post " + types.get(index));
        }
        busType.getMethod("unregister", Object.class).invoke(bus, listener);
        require(((ForgeEventMetadataTest.NestedLoader) target).rawParents.isEmpty(), "An event parent was defined from the nested raw fallback");
        stage("ALL_16_REAL_EVENT_FAMILIES_REGISTERED_AND_POSTED");
    }
    private static byte[] cancelableEvent() {
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/CancelableTick", null, CLIENT, null);
        writer.visitAnnotation("Lnet/minecraftforge/fml/common/eventhandler/Cancelable;", true).visitEnd();
        writer.visitAnnotation("L" + EVENT + "$HasResult;", true).visitEnd();
        constructor(writer, CLIENT); writer.visitEnd(); return writer.toByteArray();
    }
    private static void constructor(ClassWriter writer, String parent) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 1); method.visitEnd();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
