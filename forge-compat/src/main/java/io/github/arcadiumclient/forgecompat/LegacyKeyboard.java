package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class LegacyKeyboard {
    private static final Class<?> KEYBOARD = keyboard();
    private static final int SIZE = size();
    private static final MethodHandle KEY_DOWN = keyDown();
    private static final MethodHandle KEY_NAME = keyName();

    private LegacyKeyboard() { }

    public static boolean isKeyDown(int key) {
        if (key < 0 || key >= SIZE) return false;
        try { return (boolean) KEY_DOWN.invokeExact(key); }
        catch (RuntimeException | Error exception) { throw exception; }
        catch (Throwable exception) { throw new IllegalStateException("Could not query keyboard state.", exception); }
    }

    public static String getKeyName(int key) {
        if (key < 0 || key >= SIZE) key = 0;
        try { return (String) KEY_NAME.invokeExact(key); }
        catch (RuntimeException | Error exception) { throw exception; }
        catch (Throwable exception) { throw new IllegalStateException("Could not query keyboard key name.", exception); }
    }

    private static Class<?> keyboard() {
        try { return Class.forName("org.lwjgl.input.Keyboard", true, LegacyKeyboard.class.getClassLoader()); }
        catch (ClassNotFoundException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static int size() {
        try { return KEYBOARD.getField("KEYBOARD_SIZE").getInt(null); }
        catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static MethodHandle keyDown() {
        try { return MethodHandles.publicLookup().findStatic(KEYBOARD, "isKeyDown", MethodType.methodType(boolean.class, int.class)); }
        catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static MethodHandle keyName() {
        try { return MethodHandles.publicLookup().findStatic(KEYBOARD, "getKeyName", MethodType.methodType(String.class, int.class)); }
        catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
    }
}
