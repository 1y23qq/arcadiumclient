// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import java.nio.FloatBuffer;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public final class NativeSdl {
    public static int SDL_GetMouseState(FloatBuffer x, FloatBuffer y) {
        return org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(x, y);
    }
    public static boolean SDL_SetHintWithPriority(CharSequence name, CharSequence value, int priority) {
        return org.lwjgl.sdl.SDLHints.SDL_SetHintWithPriority(name, value, priority);
    }
}
