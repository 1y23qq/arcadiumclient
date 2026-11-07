//? if <= 1.8.9 {
/*package org.polyfrost.oneconfig.internal.legacy;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLMouse;

public final class FullscreenCursor {
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private static boolean warned;
    private static final NativeCursor NATIVE = new NativeCursor() {
        @Override public boolean visible() { return SDLMouse.SDL_CursorVisible(); }
        @Override public boolean show() { return SDLMouse.SDL_ShowCursor(); }
    };

    private FullscreenCursor() { }

    public static void render() {
        if (!WINDOWS || !Display.isCreated() || !Mouse.isCreated()) return;
        try {
            if (synchronize(true, Minecraft.getInstance().screen != null, Mouse.isGrabbed(), Display.isActive(), NATIVE)) {
                warned = false;
            } else if (!warned) {
                warned = true;
                org.apache.logging.log4j.LogManager.getLogger("Arcadium Cursor").warn("Could not show the native UI cursor");
            }
        } catch (RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                org.apache.logging.log4j.LogManager.getLogger("Arcadium Cursor").warn("Native UI cursor unavailable", failure);
            }
        }
    }

    interface NativeCursor {
        boolean visible();
        boolean show();
    }

    static boolean synchronize(boolean ready, boolean menu, boolean grabbed, boolean focused, NativeCursor cursor) {
        if (!ready || !menu || grabbed || !focused) return true;
        return cursor.visible() || cursor.show();
    }
}
*///?}
