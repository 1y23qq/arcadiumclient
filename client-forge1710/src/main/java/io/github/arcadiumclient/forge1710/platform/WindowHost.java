// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
import org.lwjglx.opengl.Display;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public final class WindowHost {
    private static final WindowHost INSTANCE = new WindowHost();
    public static WindowHost instance() { return INSTANCE; }
    public long getHandle() { return Display.getWindow(); }
    public int getWidth() { return Display.getWidth(); }
    public int getHeight() { return Display.getHeight(); }
    public int getWindowWidth() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1); var height = stack.mallocInt(1);
            return SDLVideo.SDL_GetWindowSize(getHandle(), width, height) ? width.get(0) : getWidth();
        }
    }
    public int getWindowHeight() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1); var height = stack.mallocInt(1);
            return SDLVideo.SDL_GetWindowSize(getHandle(), width, height) ? height.get(0) : getHeight();
        }
    }
    public void setTextInputActive(boolean active) {
        if (active) org.lwjgl.sdl.SDLKeyboard.SDL_StartTextInput(getHandle());
        else org.lwjgl.sdl.SDLKeyboard.SDL_StopTextInput(getHandle());
    }
}
