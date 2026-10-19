package io.github.arcadiumclient.forgecompat.transform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MixinVisibilityTest {
    private static final String PREFIX = "Lorg/spongepowered/asm/mixin/";
    @TempDir Path directory;

    @BeforeAll static void mixinEnvironment() throws Exception {
        Method initialize = Class.forName("io.github.arcadiumclient.forgecompat.HostClassMetadataTest").getDeclaredMethod("initializeMixinProperties");
        initialize.setAccessible(true); initialize.invoke(null);
    }

    @Test void guestConfigsConformVisibilityWithoutRelaxingRequiredInjectorsOrChangingLicenses() throws Exception {
        Map<String, byte[]> source = fixture(config(false));
        Path input = jar(directory.resolve("guest.jar"), source); byte[] original = Files.readAllBytes(input);
        Path output = new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null);
        Map<String, byte[]> changed = entries(output);
        JsonObject expected = json(source.get("mixins.guest.json"));
        expected.getAsJsonObject("overwrites").addProperty("conformVisibility", true);
        assertEquals(expected, json(changed.get("mixins.guest.json")));
        assertTrue(json(changed.get("mixins.guest.json")).get("required").getAsBoolean());
        assertEquals(2, json(changed.get("mixins.guest.json")).getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        assertTrue(json(changed.get("mixins.guest.json")).getAsJsonObject("overwrites").get("requireAnnotations").getAsBoolean());
        for (String resource : List.of("mcmod.info", "META-INF/LICENSE.txt", "settings.json")) assertArrayEquals(source.get(resource), changed.get(resource));
        assertArrayEquals(original, Files.readAllBytes(input));
        ClassNode remapped = node(changed.get(MIXIN + ".class"));
        MethodNode overwrite = method(remapped, "m_tick"), injector = method(remapped, "onReturn");
        assertEquals(Opcodes.ACC_PROTECTED, overwrite.access);
        assertNotNull(annotation(overwrite, PREFIX + "Overwrite;"));
        assertEquals("f_timer", remapped.fields.get(0).name);
        assertEquals(Opcodes.ACC_PROTECTED, remapped.fields.get(0).access);
        assertNotNull(remapped.fields.get(0).visibleAnnotations.get(0));
        AnnotationNode injection = annotation(injector, PREFIX + "injection/Inject;");
        assertEquals(2, value(injection, "require")); assertEquals(2, value(injection, "allow"));
        assertEquals(List.of("m_tick"), value(injection, "method"));
        assertEquals("RETURN", value((AnnotationNode) ((List<?>) value(injection, "at")).get(0), "value"));
        assertEquals(1, verify(output));
    }

    @Test void handlesManifestConfigsWithoutPackagesAndProgrammaticPluginConfigs() throws Exception {
        Map<String, byte[]> source = new LinkedHashMap<>();
        source.put("META-INF/MANIFEST.MF", text("Manifest-Version: 1.0\r\nMixinConfigs: short.json\r\n\r\n"));
        source.put("short.json", text("{\"required\":true,\"mixins\":[\"example.FullName\"]}"));
        source.put("custom-plugin.json", text("{\"package\":\"example\",\"plugin\":\"example.Plugin\",\"priority\":900}"));
        source.put("configuration.json", text("{\"package\":\"app\",\"client\":{\"port\":80},\"required\":false}"));
        Path input = jar(directory.resolve("configs.jar"), source);
        Map<String, byte[]> output = entries(new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null));
        for (String name : List.of("short.json", "custom-plugin.json")) {
            JsonObject expected = json(source.get(name)); JsonObject overwrites = new JsonObject(); overwrites.addProperty("conformVisibility", true); expected.add("overwrites", overwrites);
            assertEquals(expected, json(output.get(name)));
        }
        assertArrayEquals(source.get("configuration.json"), output.get("configuration.json"));
    }

    @Test void alreadyConformingAndOfficialInputResourcesStayByteIdentical() throws Exception {
        byte[] already = text("{ \"package\": \"example\", \"mixins\": [], \"required\": true,\n \"overwrites\": { \"conformVisibility\": true, \"requireAnnotations\": true } }\n");
        Path input = jar(directory.resolve("already.jar"), Map.of("already.json", already));
        var transformer = new ForgeJarTransformer();
        assertArrayEquals(already, entries(transformer.transform(input, directory.resolve("cache"), mappings(), false, null)).get("already.json"));
        byte[] explicitFalse = text(config(false));
        Path official = jar(directory.resolve("official.jar"), Map.of("mixins.official.json", explicitFalse));
        assertArrayEquals(explicitFalse, entries(transformer.transform(official, directory.resolve("cache"), mappings(), true, null)).get("mixins.official.json"));
    }

    @Test void rejectsMalformedOverwriteOptionsWithoutModifyingTheInput() throws Exception {
        Map<String, byte[]> source = fixture("{\"package\":\"example\",\"mixins\":[\"GameMixin\"],\"overwrites\":false}");
        Path input = jar(directory.resolve("invalid.jar"), source); byte[] original = Files.readAllBytes(input);
        IOException failure = assertThrows(IOException.class, () -> new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null));
        assertTrue(failure.getMessage().startsWith("Invalid Mixin overwrite options:"));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void pinnedPreprocessorAcceptsTheProtectedOverwriteUsingTheConvertedOption() throws Exception {
        Path input = jar(directory.resolve("mixin.jar"), fixture(config(false)));
        Map<String, byte[]> converted = entries(new ForgeJarTransformer().transform(input, directory.resolve("cache"), mappings(), false, null));
        Class<?> configType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
        Class<?> gsonType = Class.forName("org.spongepowered.include.com.google.gson.Gson"); Object gson = gsonType.getConstructor().newInstance();
        Method parse = gsonType.getMethod("fromJson", Reader.class, Class.class);
        Object rawConfig = parse.invoke(gson, new StringReader(config(false)), configType);
        Object fixedConfig = parse.invoke(gson, new StringReader(new String(converted.get("mixins.guest.json"), StandardCharsets.UTF_8)), configType);
        Method option = configType.getDeclaredMethod("conformOverwriteVisibility"); option.setAccessible(true);
        assertEquals(false, option.invoke(rawConfig)); assertEquals(true, option.invoke(fixedConfig));
        Object info = allocate("org.spongepowered.asm.mixin.transformer.MixinInfo");
        set(info, "name", "VisibilityMixin"); set(info, "parent", rawConfig);
        Object processor = allocate("org.spongepowered.asm.mixin.transformer.MixinPreProcessorStandard"); set(processor, "mixin", info);
        Class<?> methodType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$MixinMethodNode");
        var constructor = methodType.getDeclaredConstructor(info.getClass(), int.class, String.class, String.class, String.class, String[].class); constructor.setAccessible(true);
        MethodNode overwrite = (MethodNode) constructor.newInstance(info, Opcodes.ACC_PROTECTED, "work", "()I", null, null);
        overwrite.visitInsn(Opcodes.ICONST_5); overwrite.visitInsn(Opcodes.IRETURN);
        overwrite.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Overwrite;")));
        List<AbstractInsnNode> instructions = Arrays.asList(overwrite.instructions.toArray());
        Class<?> special = Class.forName("org.spongepowered.asm.mixin.transformer.MixinPreProcessorStandard$SpecialMethod");
        Field overwriteKind = special.getDeclaredField("OVERWRITE"); overwriteKind.setAccessible(true);
        Class<?> context = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTargetContext");
        Method conform = processor.getClass().getDeclaredMethod("conformVisibility", context, methodType, special, MethodNode.class); conform.setAccessible(true);
        MethodNode target = new MethodNode(Opcodes.ACC_PUBLIC, "work", "()I", null, null);
        InvocationTargetException rejected = assertThrows(InvocationTargetException.class, () -> conform.invoke(processor, null, overwrite, overwriteKind.get(null), target));
        assertEquals("org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException", rejected.getCause().getClass().getName());
        assertTrue(rejected.getCause().getMessage().contains("cannot reduce visibiliy"));
        set(info, "parent", fixedConfig);
        assertDoesNotThrow(() -> conform.invoke(processor, null, overwrite, overwriteKind.get(null), target));
        assertEquals(Opcodes.ACC_PROTECTED, overwrite.access);
        assertEquals(Opcodes.ACC_PUBLIC, target.access);
        assertEquals(instructions, Arrays.asList(overwrite.instructions.toArray()));
        assertNotNull(annotation(overwrite, PREFIX + "Overwrite;"));
    }

    private static Map<String, byte[]> fixture(String config) {
        ClassNode node = simple(MIXIN);
        node.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Mixin;", "value", List.of(Type.getObjectType(GAME)))));
        FieldNode shadow = new FieldNode(Opcodes.ACC_PROTECTED, "field_1", "I", null, null); shadow.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Shadow;"))); node.fields.add(shadow);
        MethodNode overwrite = new MethodNode(Opcodes.ACC_PROTECTED, "func_1", "()V", null, null);
        overwrite.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "Overwrite;")));
        Label done = new Label(); overwrite.visitInsn(Opcodes.ICONST_0); overwrite.visitJumpInsn(Opcodes.IFEQ, done); overwrite.visitInsn(Opcodes.RETURN);
        overwrite.visitLabel(done); overwrite.visitFrame(Opcodes.F_SAME, 0, null, 0, null); overwrite.visitInsn(Opcodes.RETURN); node.methods.add(overwrite);
        MethodNode injection = new MethodNode(Opcodes.ACC_PRIVATE, "onReturn", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
        injection.visibleAnnotations = new ArrayList<>(List.of(annotation(PREFIX + "injection/Inject;", "method", List.of("func_1"), "at", List.of(annotation(PREFIX + "injection/At;", "value", "RETURN")), "require", 2, "allow", 2)));
        injection.visitInsn(Opcodes.RETURN); node.methods.add(injection);
        Map<String, byte[]> source = new LinkedHashMap<>(); source.put(MIXIN + ".class", bytes(node)); source.put("mixins.guest.json", text(config));
        source.put("META-INF/MANIFEST.MF", text("Manifest-Version: 1.0\r\nMixinConfigs: mixins.guest.json\r\n\r\n"));
        source.put("mcmod.info", text("[{\"modid\":\"guest\",\"license\":\"User mod original license\"}]"));
        source.put("META-INF/LICENSE.txt", text("Original user mod licensing text\n")); source.put("settings.json", text("{\"license\":\"separate from host MIT permission\",\"required\":true}"));
        return source;
    }
    private static String config(boolean conform) { return "{\"package\":\"example\",\"mixins\":[\"GameMixin\"],\"required\":true,\"minVersion\":\"0.8\",\"compatibilityLevel\":\"JAVA_8\",\"injectors\":{\"defaultRequire\":2,\"maxShiftBy\":3},\"overwrites\":{\"conformVisibility\":" + conform + ",\"requireAnnotations\":true},\"userModLicense\":\"preserve\"}"; }
    private static JsonObject json(byte[] bytes) { return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static AnnotationNode annotation(String descriptor, Object... values) { AnnotationNode node = new AnnotationNode(descriptor); node.values = new ArrayList<>(List.of(values)); return node; }
    private static AnnotationNode annotation(MethodNode method, String descriptor) { return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null); }
    private static Object value(AnnotationNode annotation, String key) { for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1); return null; }
    private static Object allocate(String name) throws Exception { Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true); return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), Class.forName(name)); }
    private static void set(Object owner, String name, Object value) throws Exception { Class<?> type = owner.getClass(); while (type != null) { try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value); return; } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); } } throw new NoSuchFieldException(name); }
}
