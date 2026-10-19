package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostArmorCompatibilityTest {
    private static final String LAYER = "net/minecraft/client/renderer/entity/layers/LayerArmorBase";
    private static final String ARMOR = "net/minecraft/item/ItemArmor", MATERIAL = ARMOR + "$ArmorMaterial";
    private static final String ENTITY = "net/minecraft/entity/Entity", STACK = "net/minecraft/item/ItemStack";
    private static final String RESOURCE = "net/minecraft/util/ResourceLocation", ITEM = "net/minecraft/item/Item";
    private static final String HOOKS = "net/minecraftforge/client/ForgeHooksClient";
    private static final String SARCIOMIXIN = "dev/rdh/sarcio/mixin/mem/alloc/render/AbstractArmorLayerMixin";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String OWNER = Type.getInternalName(HostArmorCompatibilityTest.class) + "$GeneratedLayer";
    private static final String OWN_ARMOR = Type.getInternalName(Armor.class), OWN_MATERIAL = Type.getInternalName(Material.class);
    private static final String OWN_ENTITY = Type.getInternalName(Entity.class), OWN_STACK = Type.getInternalName(Stack.class);
    private static final String OWN_RESOURCE = Type.getInternalName(Resource.class);
    private static final String FORMAT = "%s:textures/models/armor/%s_layer_%d%s.png";

    @Test void restoresOriginalSarcioCallAbiAndPreservesEveryOtherActualMethod() throws Exception {
        ForgeMappings mappings = actualMappings(); ClassNode node = actualLayer(mappings);
        String helper = mappings.methodFromSrg(LAYER, "func_177178_a", "(L" + ARMOR + ";ZLjava/lang/String;)L" + RESOURCE + ";");
        MethodNode source = method(node, "getArmorResource");
        List<String> customFormat = formatBlock(source), hookTail = hookTail(source);
        Map<String, byte[]> untouched = new LinkedHashMap<>();
        for (MethodNode m : node.methods) if (m != source) untouched.put(m.name + m.desc, methodBytes(node.name, m));
        assertEquals(0, calls(source, node.name, helper));
        HostArmorCompatibility.normalize(node, mappings);
        MethodNode restored = method(node, "getArmorResource");
        assertEquals(1, calls(restored, node.name, helper));
        assertEquals(1, calls(restored, HOOKS, "getArmorTexture"));
        assertEquals(11, restored.maxLocals);
        assertEquals(customFormat, formatBlock(restored));
        assertEquals(hookTail, hookTail(restored));
        for (MethodNode m : node.methods) if (untouched.containsKey(m.name + m.desc)) assertArrayEquals(untouched.get(m.name + m.desc), methodBytes(node.name, m));
        MethodNode base = method(node, mappings.methodFromSrg(LAYER, "func_177181_a", "(L" + ARMOR + ";Z)L" + RESOURCE + ";"));
        assertEquals(2, calls(restored, node.name, helper) + calls(base, node.name, helper));
        MethodNode cache = method(actualSarcio(), "sarcio$cacheArmorTexture");
        assertEquals("(L" + node.name + ";L" + mappings.classFromSrg(ARMOR) + ";ZLjava/lang/String;L" + OPERATION + ";)L" + mappings.classFromSrg(RESOURCE) + ";", cache.desc);
        for (MethodNode m : node.methods) if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, m);
        byte[] once = bytes(node, ClassWriter.COMPUTE_MAXS);
        ClassNode decoded = new ClassNode(); new ClassReader(once).accept(decoded, 0);
        HostArmorCompatibility.normalize(decoded, mappings);
        assertArrayEquals(once, bytes(decoded, ClassWriter.COMPUTE_MAXS));
    }

    @Test void actualForgeBodyWithNativeCacheMatchesAllMaterialSlotAndSuffixCombinationsOnJvm() throws Exception {
        Class<?> reference = define(fixture(false, false)), compatible = define(fixture(true, true));
        Object before = reference.getConstructor().newInstance(), after = compatible.getConstructor().newInstance();
        for (String material : List.of("iron", "leather", "IRON", "mod:bronze", "minecraft:diamond", ":steel", "mod:foo:bar")) {
            Armor armor = new Armor(new Material(material));
            for (int slot : new int[]{0, 1, 2, 3, 4, 9}) for (String suffix : Arrays.asList(null, "overlay", "", "trim", "alternate")) {
                Entity entity = new Entity(null); Stack stack = new Stack(armor, "tag");
                Hooks.calls.clear(); Resource expected = invoke(before, entity, stack, slot, suffix); HookCall expectedCall = Hooks.calls.get(0);
                assertEquals(1, Hooks.calls.size());
                Hooks.calls.clear(); Resource result = invoke(after, entity, stack, slot, suffix);
                assertEquals(expected.toString(), result.toString(), material + "/" + slot + "/" + suffix);
                assertEquals(List.of(expectedCall), Hooks.calls);
            }
        }
    }

    @Test void nativeCacheHitsStillInvokeContextSensitiveForgeHookExactlyOnce() throws Exception {
        Class<?> type = define(fixture(true, true)); Object layer = type.getConstructor().newInstance();
        Armor armor = new Armor(new Material("iron")); Stack stack = new Stack(armor, "first");
        Hooks.calls.clear(); Resource first = null;
        for (int i = 0; i < 12; i++) {
            Resource value = invoke(layer, new Entity(null), stack, 1, null);
            if (first == null) first = value; else assertSame(first, value);
        }
        assertEquals(12, Hooks.calls.size());
        assertEquals(1, type.getField("formats").getInt(null));
        Entity custom = new Entity("demo:armor/custom"); stack.tag = "changed";
        Hooks.calls.clear(); Resource selected = invoke(layer, custom, stack, 1, null);
        assertEquals("demo:armor/custom/changed/1/null", selected.toString());
        assertSame(custom, Hooks.calls.get(0).entity()); assertSame(stack, Hooks.calls.get(0).stack());
        assertEquals("minecraft:textures/models/armor/iron_layer_1.png", Hooks.calls.get(0).defaultTexture());
        assertEquals(1, Hooks.calls.size()); assertEquals(1, type.getField("formats").getInt(null));
        Hooks.fail = true;
        try { assertInstanceOf(IllegalStateException.class, assertThrows(InvocationTargetException.class, () -> invoke(layer, custom, stack, 1, null)).getCause()); }
        finally { Hooks.fail = false; }
        assertEquals(2, Hooks.calls.size());
    }

    @Test void nativeFourEntryCacheDoesNotConflateForgeCustomSuffixesOrNamespaces() throws Exception {
        Class<?> type = define(fixture(true, true)); Object layer = type.getConstructor().newInstance();
        Armor ordinary = new Armor(new Material("leather")); Stack stack = new Stack(ordinary, "tag");
        for (int slot : new int[]{1, 2}) for (String suffix : Arrays.asList(null, "overlay")) {
            for (int repeat = 0; repeat < 3; repeat++) invoke(layer, new Entity(null), stack, slot, suffix);
        }
        assertEquals(4, type.getField("formats").getInt(null));
        for (String suffix : List.of("alternate", "trim", "")) {
            Hooks.calls.clear(); Resource result = invoke(layer, new Entity(null), stack, 2, suffix);
            assertEquals("minecraft:textures/models/armor/leather_layer_2_" + suffix + ".png", result.toString());
            assertEquals(1, Hooks.calls.size());
        }
        assertEquals(4, type.getField("formats").getInt(null));
        Stack namespaced = new Stack(new Armor(new Material("workshop:steel")), "tag");
        Resource first = invoke(layer, new Entity(null), namespaced, 2, "overlay");
        Resource second = invoke(layer, new Entity(null), namespaced, 2, "overlay");
        assertSame(first, second);
        assertEquals("workshop:textures/models/armor/steel_layer_2_overlay.png", first.toString());
        assertEquals(4, type.getField("formats").getInt(null));
        var cache = type.getDeclaredField("sarcio$armorTextures"); cache.setAccessible(true);
        assertEquals(1, ((Map<?, ?>) cache.get(null)).size());
    }

    @Test void malformedOriginalLayoutIsRejectedBeforeMutationAndVanillaIsUnchanged() throws Exception {
        ForgeMappings mappings = actualMappings();
        ClassNode node = actualLayer(mappings);
        MethodNode original = method(node, "getArmorResource");
        IntInsnNode separator = Arrays.stream(original.instructions.toArray()).filter(i -> i instanceof IntInsnNode integer && integer.operand == ':').map(IntInsnNode.class::cast).findFirst().orElseThrow();
        separator.operand = '/'; byte[] changed = bytes(node, 0);
        assertThrows(IllegalStateException.class, () -> HostArmorCompatibility.normalize(node, mappings));
        assertArrayEquals(changed, bytes(node, 0));
        ClassNode other = actualLayer(mappings); other.name = "fixture/OtherLayer"; byte[] before = bytes(other, 0);
        HostArmorCompatibility.normalize(other, mappings); assertArrayEquals(before, bytes(other, 0));
        Path baseline = root().resolve("build/forge-compat/runtime-qa/20261015-134245-0701a0e1bc/baseline/game/.fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        Assumptions.assumeTrue(Files.isRegularFile(baseline));
        ClassNode vanilla = read(baseline, mappings.classFromSrg(LAYER)); byte[] untouched = bytes(vanilla, 0);
        HostArmorCompatibility.normalize(vanilla, mappings); assertArrayEquals(untouched, bytes(vanilla, 0));
    }

    public static class Item { }
    public record Material(String name) { }
    public static final class Armor extends Item {
        private final Material material;
        public Armor(Material material) { this.material = material; }
        public Material material() { return material; }
    }
    public static final class Stack {
        private final Item item;
        public String tag;
        public Stack(Item item, String tag) { this.item = item; this.tag = tag; }
        public Item item() { return item; }
    }
    public record Entity(String override) { }
    public static final class Resource {
        private final String value;
        public Resource(String input) {
            int colon = input.indexOf(':'); String domain = colon < 0 ? "minecraft" : input.substring(0, colon);
            value = (domain.isEmpty() ? "minecraft" : domain.toLowerCase()) + ":" + (colon < 0 ? input : input.substring(colon + 1));
        }
        @Override public String toString() { return value; }
    }
    public record HookCall(Entity entity, Stack stack, String defaultTexture, int slot, String suffix) { }
    public static final class Hooks {
        public static final List<HookCall> calls = new ArrayList<>();
        public static boolean fail;
        public static String getArmorTexture(Entity entity, Stack stack, String texture, int slot, String suffix) {
            calls.add(new HookCall(entity, stack, texture, slot, suffix));
            if (fail) throw new IllegalStateException("Owned armor hook failure");
            return entity.override() == null ? texture : entity.override() + "/" + stack.tag + "/" + slot + "/" + suffix;
        }
    }
    public interface Operation { Object call(Object... arguments) throws Exception; }
    public static final class TextureOperation implements Operation {
        public TextureOperation() { }
        @Override public Object call(Object... arguments) throws Exception {
            Method method = arguments[0].getClass().getDeclaredMethod("texture", Armor.class, boolean.class, String.class);
            method.setAccessible(true); return method.invoke(arguments[0], arguments[1], arguments[2], arguments[3]);
        }
    }

    private static ClassNode fixture(boolean normalize, boolean nativeCache) throws Exception {
        ForgeMappings mappings = actualMappings(); ClassNode source = actualLayer(mappings);
        String helper = mappings.methodFromSrg(LAYER, "func_177178_a", "(L" + ARMOR + ";ZLjava/lang/String;)L" + RESOURCE + ";");
        String base = mappings.methodFromSrg(LAYER, "func_177181_a", "(L" + ARMOR + ";Z)L" + RESOURCE + ";");
        String legs = mappings.methodFromSrg(LAYER, "func_177180_b", "(I)Z");
        ClassNode selected = new ClassNode(); selected.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, source.name, null, "java/lang/Object", null);
        for (String name : List.of("getArmorResource", helper, base, legs)) selected.methods.add(method(source, name));
        Map<String, String> types = Map.of(mappings.classFromSrg(LAYER), OWNER, mappings.classFromSrg(ARMOR), OWN_ARMOR,
            mappings.classFromSrg(MATERIAL), OWN_MATERIAL, mappings.classFromSrg(ENTITY), OWN_ENTITY,
            mappings.classFromSrg(STACK), OWN_STACK, mappings.classFromSrg(RESOURCE), OWN_RESOURCE,
            mappings.classFromSrg(ITEM), Type.getInternalName(Item.class), HOOKS, Type.getInternalName(Hooks.class));
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return types.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return owner.equals(source.name) && descriptor.equals("Ljava/util/Map;") ? "textures" : name; }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                if (owner.equals(source.name)) { if (name.equals(helper)) return "texture"; if (name.equals(base)) return "baseTexture"; if (name.equals(legs)) return "leggings"; }
                if (owner.equals(mappings.classFromSrg(ARMOR))) return "material";
                if (owner.equals(mappings.classFromSrg(MATERIAL))) return "name";
                if (owner.equals(mappings.classFromSrg(STACK))) return "item";
                return name;
            }
        };
        ClassNode node = new ClassNode(); selected.accept(new ClassRemapper(node, remapper));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "textures", "Ljava/util/Map;", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "formats", "I", null, null));
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitVarInsn(Opcodes.ALOAD, 0); init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); init.visitInsn(Opcodes.RETURN); init.maxStack = 1; init.maxLocals = 1; node.methods.add(init);
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        allocateMap(clinit, "textures", "java/util/HashMap");
        if (nativeCache) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "sarcio$armorTextures", "Ljava/util/Map;", null, null));
            allocateMap(clinit, "sarcio$armorTextures", "java/util/IdentityHashMap");
        }
        clinit.visitInsn(Opcodes.RETURN); clinit.maxStack = 2; node.methods.add(clinit);
        MethodNode texture = method(node, "texture"); InsnList count = new InsnList();
        count.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "formats", "I")); count.add(new InsnNode(Opcodes.ICONST_1)); count.add(new InsnNode(Opcodes.IADD)); count.add(new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "formats", "I")); texture.instructions.insert(count);
        if (normalize) {
            ClassNode unlinked = new ClassNode(); node.accept(new ClassRemapper(unlinked, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return name.equals(Type.getInternalName(Hooks.class)) ? HOOKS : name; } }));
            node = unlinked; HostArmorCompatibility.normalize(node, fixtureMappings());
        }
        if (nativeCache) addNativeCache(node, mappings, types);
        return node;
    }

    private static void addNativeCache(ClassNode node, ForgeMappings mappings, Map<String, String> types) throws Exception {
        ClassNode source = actualSarcio(), selected = new ClassNode(); selected.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, source.name, null, "java/lang/Object", null);
        for (MethodNode m : source.methods) if (m.name.equals("sarcio$cacheArmorTexture") || m.name.startsWith("lambda$sarcio$cacheArmorTexture")) selected.methods.add(m);
        ClassNode translated = new ClassNode(); selected.accept(new ClassRemapper(translated, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { if (name.equals(source.name)) return OWNER; if (name.equals(OPERATION)) return Type.getInternalName(Operation.class); return types.getOrDefault(name, name); }
            @Override public String mapMethodName(String owner, String name, String descriptor) { return owner.equals(mappings.classFromSrg(ARMOR)) ? "material" : name; }
        }));
        MethodNode handler = method(translated, "sarcio$cacheArmorTexture");
        for (MethodNode m : translated.methods) { m.signature = null; m.visibleAnnotations = null; m.invisibleAnnotations = null; node.methods.add(m); }
        for (String target : List.of("getArmorResource", "baseTexture")) {
            MethodNode m = method(node, target);
            List<MethodInsnNode> calls = Arrays.stream(m.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call && call.owner.equals(OWNER) && call.name.equals("texture")).map(MethodInsnNode.class::cast).toList();
            assertEquals(1, calls.size()); MethodInsnNode call = calls.get(0);
            AbstractInsnNode first = call;
            while (!(first instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 0)) first = first.getPrevious();
            if (target.equals("getArmorResource")) first = first.getPrevious().getPrevious();
            assertTrue(first instanceof VarInsnNode receiver && receiver.var == 0);
            m.instructions.insertBefore(first, new VarInsnNode(Opcodes.ALOAD, 0));
            InsnList operation = new InsnList(); String impl = Type.getInternalName(TextureOperation.class);
            operation.add(new TypeInsnNode(Opcodes.NEW, impl)); operation.add(new InsnNode(Opcodes.DUP)); operation.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, impl, "<init>", "()V", false));
            m.instructions.insertBefore(call, operation); m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, handler.name, handler.desc, false));
        }
    }
    private static void allocateMap(MethodNode method, String field, String implementation) {
        method.visitTypeInsn(Opcodes.NEW, implementation); method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL, implementation, "<init>", "()V", false); method.visitFieldInsn(Opcodes.PUTSTATIC, OWNER, field, "Ljava/util/Map;");
    }
    private static Class<?> define(ClassNode node) {
        ClassNode linked = new ClassNode(); node.accept(new ClassRemapper(linked, new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return name.equals(HOOKS) ? Type.getInternalName(Hooks.class) : name; } }));
        byte[] bytes = bytes(linked, ClassWriter.COMPUTE_MAXS);
        class Loader extends ClassLoader { Loader() { super(HostArmorCompatibilityTest.class.getClassLoader()); } Class<?> define() { return defineClass(OWNER.replace('/', '.'), bytes, 0, bytes.length); } }
        return new Loader().define();
    }
    private static Resource invoke(Object layer, Entity entity, Stack stack, int slot, String suffix) throws Exception { return (Resource) layer.getClass().getMethod("getArmorResource", Entity.class, Stack.class, int.class, String.class).invoke(layer, entity, stack, slot, suffix); }
    private static ForgeMappings fixtureMappings() throws Exception {
        Map<String, String> types = Map.of(LAYER, OWNER, ARMOR, OWN_ARMOR, ENTITY, OWN_ENTITY, STACK, OWN_STACK, RESOURCE, OWN_RESOURCE);
        StringBuilder srg = new StringBuilder(); types.forEach((legacy, runtime) -> srg.append("CL: ").append(runtime).append(' ').append(legacy).append('\n'));
        String descriptor = "(L" + ARMOR + ";ZLjava/lang/String;)L" + RESOURCE + ";";
        String mapped = new Remapper(Opcodes.ASM9) { @Override public String map(String name) { return types.getOrDefault(name, name); } }.mapMethodDesc(descriptor);
        srg.append("MD: ").append(OWNER).append("/texture ").append(mapped).append(' ').append(LAYER).append("/func_177178_a ").append(descriptor).append('\n');
        srg.append("MD: ").append(OWNER).append("/leggings (I)Z ").append(LAYER).append("/func_177180_b (I)Z\n");
        return new ForgeMappings(new MappingResolver() {
            @Override public List<String> getNamespaces() { return List.of("official", "intermediary"); }
            @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
            @Override public String mapClassName(String namespace, String name) { return name; }
            @Override public String unmapClassName(String namespace, String name) { return name; }
            @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
            @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
        }, new StringReader(srg.toString()));
    }
    private static ForgeMappings actualMappings() throws Exception {
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny"), srg = root().resolve("build/forge-compat/dependencies/forge-1.8.9.srg");
        Assumptions.assumeTrue(Files.isRegularFile(tiny) && Files.isRegularFile(srg)); MemoryMappingTree tree = new MemoryMappingTree();
        try (var input = Files.newBufferedReader(tiny)) { MappingReader.read(input, tree); }
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var input = Files.newBufferedReader(srg)) { return new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), input); }
    }
    private static ClassNode actualLayer(ForgeMappings mappings) throws Exception { Path jar = root().resolve("build/forge-compat/runtime-qa/20261015-142622-46d4047a83/jei/game/.fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar"); Assumptions.assumeTrue(Files.isRegularFile(jar)); return read(jar, mappings.classFromSrg(LAYER)); }
    private static ClassNode actualSarcio() throws Exception { Path jar = ForgeTestFixtures.nativeJar("Sarcio-1.1.0.jar"); Assumptions.assumeTrue(Files.isRegularFile(jar)); return read(jar, SARCIOMIXIN); }
    private static Path root() { return Path.of("..").toAbsolutePath().normalize(); }
    private static ClassNode read(Path jar, String name) throws Exception { try (ZipFile zip = new ZipFile(jar.toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) { ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, 0); return node; } }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static long calls(MethodNode method, String owner, String name) { return Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)).count(); }
    private static List<String> formatBlock(MethodNode method) { List<String> result = new ArrayList<>(); boolean copying = false; for (AbstractInsnNode i : method.instructions) { if (i instanceof LdcInsnNode constant && FORMAT.equals(constant.cst)) copying = true; if (copying && i.getOpcode() >= 0) result.add(instruction(i)); if (copying && i instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ASTORE && variable.var == 9) break; } return result; }
    private static List<String> hookTail(MethodNode method) { List<String> result = new ArrayList<>(); boolean copying = false; for (AbstractInsnNode i : method.instructions) { if (i instanceof MethodInsnNode call && call.owner.equals(HOOKS) && call.name.equals("getArmorTexture")) copying = true; if (copying && i.getOpcode() >= 0) result.add(instruction(i)); } return result; }
    private static String instruction(AbstractInsnNode i) { return i.getOpcode() + (i instanceof MethodInsnNode m ? " " + m.owner + "." + m.name + m.desc : i instanceof FieldInsnNode f ? " " + f.owner + "." + f.name + ":" + f.desc : i instanceof VarInsnNode v ? " " + v.var : i instanceof LdcInsnNode c ? " " + c.cst : i instanceof TypeInsnNode t ? " " + t.desc : i instanceof IntInsnNode n ? " " + n.operand : ""); }
    private static byte[] bytes(ClassNode node, int flags) { ClassWriter writer = new ClassWriter(flags); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(String owner, MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); node.methods.add(method); return bytes(node, 0); }
}
