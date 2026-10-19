package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.reflect.ForgeReflection;
import org.objectweb.asm.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ForgeJava25ReflectionRuntimeProbe {
    private static void stage(String value) { System.out.println("ARCADIUM_REFLECTION_PROBE " + value); }

    public static void main(String[] args) {
        try {
            Path output = Path.of(args[1]);
            ForgeRuntimeHeadlessProbe.run(Path.of(args[0]), output, target -> verify(target, output));
            stage("SUCCESS");
        } catch (Throwable failure) {
            while ((failure instanceof InvocationTargetException || failure instanceof ExceptionInInitializerError) && failure.getCause() != null) failure = failure.getCause();
            stage("FAILED " + failure.getClass().getName()); failure.printStackTrace(); System.exit(1);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify(URLClassLoader target, Path output) throws Exception {
        Class<?> helper = target.loadClass("net.minecraftforge.common.util.EnumHelper");
        int primitiveCases = ForgeFailsafeFieldRuntimeCheck.verify(helper);
        require(primitiveCases == 208, "The real public EnumHelper setter matrix did not complete");
        stage("REAL_PUBLIC_FAILSAFE_FIELD_SETTER_VERIFIED");
        Class<? extends Enum> creature = (Class) mc(target, "net.minecraft.entity.EnumCreatureAttribute");
        Enum<?>[] before = creature.getEnumConstants(); Enum.valueOf(creature, before[0].name());
        Enum<?> added = (Enum<?>) helper.getMethod("addCreatureAttribute", String.class).invoke(null, "ARCADIUM_HOLDER_PROBE");
        require(added.getDeclaringClass() == creature && added.ordinal() == before.length, "The real Minecraft enum constructor did not produce the next ordinal");
        require(creature.getEnumConstants().length == before.length + 1, "The Class enumConstants cache was not refreshed");
        require(Enum.valueOf(creature, added.name()) == added, "The Enum.valueOf cache was not refreshed");
        require(Arrays.asList((Object[]) creature.getMethod("values").invoke(null)).contains(added), "The actual enum values method was not updated");
        Class<? extends Enum> sky = (Class) mc(target, "net.minecraft.world.EnumSkyBlock");
        int skyLength = sky.getEnumConstants().length;
        Object extraSky = helper.getMethod("addSkyBlock", String.class, int.class).invoke(null, "ARCADIUM_DIM", 7);
        Field light = ForgeReflection.getDeclaredField(sky, "field_77198_c"); light.setAccessible(true);
        require(light.getInt(extraSky) == 7 && ((Enum<?>) extraSky).ordinal() == skyLength, "The real vanilla enum constructor lost its custom state");
        stage("REAL_MINECRAFT_ENUMS_EXTENDED");

        Class<?> item = mc(target, "net.minecraft.item.Item"), stack = mc(target, "net.minecraft.item.ItemStack");
        Class<?> location = mc(target, "net.minecraft.util.ResourceLocation");
        Object registeredItem = item.getConstructor().newInstance();
        Object key = location.getConstructor(String.class).newInstance("arcadium_holder:fixture");
        Class<?> gameData = target.loadClass("net.minecraftforge.fml.common.registry.GameData");
        Object registry = gameData.getMethod("getItemRegistry").invoke(null);
        var register = registry.getClass().getDeclaredMethod("add", int.class, location, Object.class); register.setAccessible(true);
        register.invoke(registry, 24000, key, registeredItem);
        Class<?> fields = define(target, "fixture.HolderFields", holderFields(item, stack));
        Class<?> objectRef = target.loadClass("net.minecraftforge.fml.common.registry.ObjectHolderRef");
        Constructor<?> constructor = objectRef.getDeclaredConstructor(Field.class, location, boolean.class); constructor.setAccessible(true);
        var apply = objectRef.getDeclaredMethod("apply"); apply.setAccessible(true);
        Field reference = fields.getField("REFERENCE");
        Object holder = constructor.newInstance(reference, key, false);
        apply.invoke(holder); require(reference.get(null) == registeredItem, "The real ObjectHolderRef did not inject its static-final Item");
        require(Modifier.isFinal(reference.getModifiers()), "Holder injection changed the reflected final modifier");
        Field mutable = fields.getField("mutable"); apply.invoke(constructor.newInstance(mutable, key, false));
        require(mutable.get(null) == registeredItem, "The real ObjectHolderRef did not inject a mutable field");
        Field missing = fields.getField("MISSING"); Object originalMissing = missing.get(null);
        Object absent = location.getConstructor(String.class).newInstance("arcadium_holder:missing");
        apply.invoke(constructor.newInstance(missing, absent, false));
        require(missing.get(null) == originalMissing, "An optional missing holder was overwritten");
        expectCause(IllegalStateException.class, () -> constructor.newInstance(fields.getField("WRONG"), key, false));
        expectCause(IllegalArgumentException.class, () -> constructor.newInstance(fields.getField("instance"), key, false));
        stage("REAL_OBJECT_HOLDERS_INJECTED");

        Class<?> stackRef = target.loadClass("net.minecraftforge.fml.common.registry.ItemStackHolderRef");
        Constructor<?> stackConstructor = stackRef.getDeclaredConstructor(Field.class, String.class, int.class, String.class); stackConstructor.setAccessible(true);
        var applyStack = stackRef.getDeclaredMethod("apply"); applyStack.setAccessible(true);
        Field stackField = fields.getField("STACK");
        applyStack.invoke(stackConstructor.newInstance(stackField, "arcadium_holder:fixture", 5, "{ArcadiumTest:7}"));
        Object actualStack = stackField.get(null); require(actualStack != null, "The real ItemStackHolderRef did not inject its stack");
        require(ForgeReflection.getDeclaredMethod(stack, "func_77973_b", new Class<?>[0]).invoke(actualStack) == registeredItem, "The stack holder used a different Item");
        require(Integer.valueOf(5).equals(ForgeReflection.getDeclaredMethod(stack, "func_77960_j", new Class<?>[0]).invoke(actualStack)), "The stack metadata changed");
        Object nbt = ForgeReflection.getDeclaredMethod(stack, "func_77978_p", new Class<?>[0]).invoke(actualStack);
        require(Integer.valueOf(7).equals(ForgeReflection.getDeclaredMethod(nbt.getClass(), "func_74762_e", new Class<?>[]{String.class}).invoke(nbt, "ArcadiumTest")), "The stack NBT changed");
        require(Modifier.isFinal(stackField.getModifiers()), "The ItemStack holder changed its final modifier");
        expectCause(RuntimeException.class, () -> applyStack.invoke(stackConstructor.newInstance(stackField, "arcadium_holder:fixture", 5, "{broken")));
        require(stackField.get(null) == actualStack, "An invalid stack request changed a previously injected field");
        stage("REAL_ITEMSTACK_HOLDER_AND_ERRORS_PRESERVED");
        Files.writeString(output.resolve("reflection-result.json"), "{\"java25\":true,\"publicGenericEnumHelperSetter\":true,\"privateStaticAndInstanceFinalReferences\":true,\"nullReferencesAndCovariantArrays\":true,\"primitiveOracleCases\":208,\"staticTargetIgnored\":true,\"originalSetterExceptionWrapping\":true,\"staticInitializationOrder\":true,\"realMinecraftEnums\":true,\"enumConstructorState\":true,\"enumValuesArray\":true,\"enumConstantsCache\":true,\"enumValueOfCache\":true,\"realObjectHolderRef\":true,\"staticFinalModifiersPreserved\":true,\"mutableHolder\":true,\"missingOptionalHolderPreserved\":true,\"invalidHolderErrors\":true,\"realItemStackHolderRef\":true,\"itemMetadataAndNbt\":true,\"invalidNbtErrorPreserved\":true,\"guestModsExecuted\":false}\n");
    }

    private static byte[] holderFields(Class<?> item, Class<?> stack) {
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/HolderFields", null, "java/lang/Object", null);
        String itemDesc = Type.getDescriptor(item);
        for (String name : new String[]{"REFERENCE", "MISSING"}) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, itemDesc, null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mutable", itemDesc, null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "instance", itemDesc, null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "STACK", Type.getDescriptor(stack), null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "WRONG", "Ljava/lang/String;", null, null).visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitTypeInsn(Opcodes.NEW, Type.getInternalName(item)); initializer.visitInsn(Opcodes.DUP);
        initializer.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(item), "<init>", "()V", false);
        initializer.visitFieldInsn(Opcodes.PUTSTATIC, "fixture/HolderFields", "MISSING", itemDesc);
        initializer.visitInsn(Opcodes.RETURN); initializer.visitMaxs(2, 0); initializer.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }

    private static Class<?> mc(ClassLoader target, String name) throws Exception { return ForgeReflection.forName(name, false, target); }
    private static Class<?> define(ClassLoader target, String name, byte[] bytes) throws Exception { var define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class); define.setAccessible(true); return (Class<?>) define.invoke(target, name, bytes, 0, bytes.length); }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static void expectCause(Class<? extends Throwable> type, CheckedAction action) throws Exception {
        try { action.run(); } catch (InvocationTargetException failure) { if (type.isInstance(failure.getCause())) return; throw failure; }
        throw new IllegalStateException("The expected Forge failure was not raised: " + type.getName());
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
