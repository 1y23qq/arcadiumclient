// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.capture;

import io.github.arcadiumclient.window.WindowsGameIdentity;
import org.lwjgl.sdl.SDL;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

import static org.lwjgl.system.windows.User32.CS_BYTEALIGNCLIENT;
import static org.lwjgl.system.windows.User32.CS_OWNDC;

public final class SdlCaptureCompatibility {
    private SdlCaptureCompatibility() {}

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("arcadium.captureCompatibility", "true"));
    }

    public static boolean initVideo(int flags) {
        if ((flags & SDLInit.SDL_INIT_VIDEO) != 0) WindowsGameIdentity.prepare();
        if (Platform.get() != Platform.WINDOWS || !isEnabled() || (flags & SDLInit.SDL_INIT_VIDEO) == 0
                || SDLInit.SDL_WasInit(SDLInit.SDL_INIT_VIDEO) != 0) {
            return SDLInit.SDL_Init(flags);
        }
        // Resolve SDL's exports directly because LWJGL 3.4.3 prefixes these two bindings twice.
        long register = SDL.getLibrary().getFunctionAddress("SDL_RegisterApp");
        long unregister = SDL.getLibrary().getFunctionAddress("SDL_UnregisterApp");
        if (register == 0 || unregister == 0) {
            System.err.println("Arcadium capture window class API is unavailable");
            return SDLInit.SDL_Init(flags);
        }
        boolean registered;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            registered = JNI.invokePPZ(MemoryUtil.memAddress(stack.UTF8("LWJGL")),
                    CS_BYTEALIGNCLIENT | CS_OWNDC, 0L, register);
        }
        if (!registered) {
            System.err.println("Arcadium capture window class could not be registered: " + SDLError.SDL_GetError());
            SDLError.SDL_ClearError();
            return SDLInit.SDL_Init(flags);
        }
        try {
            return SDLInit.SDL_Init(flags);
        } finally {
            // Keep SDL video's own registration until shutdown; release ours on failure too.
            JNI.invokeV(unregister);
        }
    }
}
