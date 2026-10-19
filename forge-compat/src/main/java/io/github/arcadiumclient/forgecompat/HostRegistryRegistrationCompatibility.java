package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostRegistryRegistrationCompatibility {
    private static final String API = "net/ornithemc/osl/registries/api/registry/";
    private static final String IMPL = "net/ornithemc/osl/registries/impl/registry/";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostRegistryRegistrationCompatibility";
    private record Names(String namespaced, String callback, String wrapped, String registries, String keys, String resourceKey,
                         String identifier, String registryApi, String synced, String syncedRegistry, String mappings) { }
    private record Scope(NativeAccess access, Object wrapper) { }
    private static volatile Names configured;
    private static final Map<Object, Scope> PENDING = new IdentityHashMap<>();
    private static final Map<ClassLoader, Boolean> INITIALIZED = new WeakHashMap<>();
    private static final ClassValue<NativeAccess> ACCESS = new ClassValue<>() {
        @Override protected NativeAccess computeValue(Class<?> type) { return new NativeAccess(configured, type.getClassLoader()); }
    };

    private static final class NativeAccess {
        final Names names;
        final ClassLoader loader;
        final VarHandle callback;
        volatile OslAccess osl;
        NativeAccess(Names names, ClassLoader loader) {
            this.names = names; this.loader = loader;
            try {
                Class<?> nativeRegistry = type(loader, names.namespaced), callbackType = type(loader, names.callback);
                callback = MethodHandles.privateLookupIn(nativeRegistry, MethodHandles.lookup()).findVarHandle(nativeRegistry, "callback", callbackType);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("The native OSL registration callback could not be linked", failure); }
        }
        OslAccess osl() {
            OslAccess current = osl;
            if (current == null) synchronized (this) { current = osl; if (current == null) osl = current = new OslAccess(this); }
            return current;
        }
    }

    private static final class OslAccess {
        final Class<?> wrapped;
        final Field block, item, syncedCatalog;
        final MethodHandle registry, freeze, identifier, syncedGet, mappings, reset;
        final VarHandle nativeRegistry, frozen;
        OslAccess(NativeAccess access) {
            Names names = access.names; ClassLoader loader = access.loader;
            try {
                wrapped = type(loader, names.wrapped);
                Class<?> keys = type(loader, names.keys), api = type(loader, names.registryApi);
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(wrapped, MethodHandles.lookup());
                block = keys.getField("BLOCK"); item = keys.getField("ITEM");
                nativeRegistry = lookup.findVarHandle(wrapped, "registry", type(loader, names.namespaced));
                frozen = lookup.findVarHandle(wrapped, "frozen", boolean.class);
                registry = lookup.unreflect(type(loader, names.registries).getMethod("get", type(loader, names.resourceKey)))
                    .asType(MethodType.methodType(Object.class, Object.class));
                freeze = lookup.unreflect(wrapped.getMethod("freeze")).asType(MethodType.methodType(Object.class, Object.class));
                identifier = lookup.unreflect(wrapped.getMethod("identifier")).asType(MethodType.methodType(Object.class, Object.class));
                syncedCatalog = type(loader, names.synced).getField("SYNCED_REGISTRIES");
                syncedGet = lookup.unreflect(api.getMethod("get", type(loader, names.identifier))).asType(MethodType.methodType(Object.class, Object.class, Object.class));
                mappings = lookup.unreflect(type(loader, names.syncedRegistry).getMethod("getMappings")).asType(MethodType.methodType(Object.class, Object.class));
                reset = lookup.unreflect(type(loader, names.mappings).getMethod("reset")).asType(MethodType.methodType(void.class, Object.class));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("The native block/item registry lifecycle could not be linked", failure); }
        }
        Object wrapper(Object target) throws Throwable {
            for (Field field : List.of(block, item)) {
                Object key = field.get(null);
                Object value = (Object) registry.invokeExact(key);
                if (value != null && wrapped.isInstance(value) && nativeRegistry.get(value) == target) return value;
            }
            return null;
        }
        void refresh(Object wrapper) throws Throwable {
            Object id = (Object) identifier.invokeExact(wrapper), catalog = syncedCatalog.get(null);
            Object entry = (Object) syncedGet.invokeExact(catalog, id);
            if (entry == null) throw new IllegalStateException("The affected native block/item registry is not synchronized");
            Object state = (Object) mappings.invokeExact(entry);
            reset.invokeExact(state);
        }
    }

    private HostRegistryRegistrationCompatibility() { }

    public static void configure(ForgeMappings mappings) {
        configured = new Names(mappings.classFromSrg("net/minecraft/util/RegistryNamespaced"),
            mappings.classFromSrg("net/ornithemc/osl/registries/impl/access/IdRegistryAccess$RegisterCallback"),
            mappings.classFromSrg(IMPL + "WrappedIdRegistry"), mappings.classFromSrg(API + "Registries"), mappings.classFromSrg(API + "RegistryKeys"),
            mappings.classFromSrg(API + "ResourceKey"), mappings.classFromSrg("net/ornithemc/osl/core/api/util/NamespacedIdentifier"),
            mappings.classFromSrg(API + "Registry"), mappings.classFromSrg(IMPL + "SyncedRegistriesImpl"), mappings.classFromSrg(IMPL + "SyncedRegistry"),
            mappings.classFromSrg(IMPL + "sync/SerializableRegistryMappings"));
    }

    public static Object beginRegistration(Object registry) throws Throwable {
        Names names = configured;
        if (names == null || !inherits(registry.getClass(), names.namespaced)) return null;
        NativeAccess access = ACCESS.get(registry.getClass());
        if (!access.names.equals(names)) { ACCESS.remove(registry.getClass()); access = ACCESS.get(registry.getClass()); }
        // The callback is absent before OSL bootstraps its wrapped registries.
        if (access.callback.get(registry) == null) return null;
        OslAccess osl = access.osl(); Object wrapper = osl.wrapper(registry);
        if (wrapper == null || !(boolean) osl.frozen.get(wrapper)) return null;
        osl.frozen.set(wrapper, false);
        return new Scope(access, wrapper);
    }

    public static void endRegistration(Object token, Throwable failure) throws Throwable {
        if (token == null) return;
        Scope scope = (Scope) token; OslAccess osl = scope.access.osl();
        try { Object ignored = (Object) osl.freeze.invokeExact(scope.wrapper); }
        catch (Throwable cleanup) { if (failure == null) throw cleanup; if (cleanup != failure) failure.addSuppressed(cleanup); }
        if (failure != null) return;
        boolean initialized;
        synchronized (PENDING) { PENDING.put(scope.wrapper, scope); initialized = INITIALIZED.containsKey(scope.access.loader); }
        if (initialized) refresh(scope);
    }

    public static void finishInitialization(Object loader) throws Throwable {
        ClassLoader owner = loader.getClass().getClassLoader();
        while (true) {
            List<Scope> affected;
            synchronized (PENDING) {
                affected = PENDING.values().stream().filter(scope -> scope.access.loader == owner).toList();
                if (affected.isEmpty()) { INITIALIZED.put(owner, true); return; }
            }
            for (Scope scope : affected) refresh(scope);
        }
    }

    private static void refresh(Scope scope) throws Throwable {
        scope.access.osl().refresh(scope.wrapper);
        synchronized (PENDING) { PENDING.remove(scope.wrapper, scope); }
    }

    public static void normalize(ClassNode node) {
        if (!node.name.equals("net/minecraftforge/fml/common/Loader")) return;
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("initializeMods") && candidate.desc.equals("()V")).findFirst()
            .orElseThrow(() -> new IllegalStateException("The Forge initialization completion method is missing"));
        if (Arrays.stream(method.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE) && call.name.equals("finishInitialization"))) return;
        var returns = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).toList();
        long freeze = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals("net/minecraftforge/fml/common/registry/PersistentRegistryManager") && call.name.equals("freezeData") && call.desc.equals("()V")).count();
        if (returns.size() != 1 || freeze != 1) throw new IllegalStateException("The Forge registry freeze completion bytecode is unsupported");
        InsnList finish = new InsnList(); finish.add(new VarInsnNode(Opcodes.ALOAD, 0));
        finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "finishInitialization", "(Ljava/lang/Object;)V", false));
        method.instructions.insertBefore(returns.get(0), finish); method.maxStack = Math.max(1, method.maxStack);
    }

    private static boolean inherits(Class<?> type, String name) { for (Class<?> current = type; current != null; current = current.getSuperclass()) if (current.getName().replace('.', '/').equals(name)) return true; return false; }
    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException { return Class.forName(name.replace('/', '.'), false, loader); }
}
