// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.window;

import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;

public final class WindowsGameIdentity {
    public static final String APP_ID = "com.arcadiumclient.game";
    private static boolean prepared;

    private WindowsGameIdentity() {}

    public static void prepare() {
        if (Platform.get() != Platform.WINDOWS || prepared) return;
        prepared = true;
        GameWindowIcon.buffers();
        try (SharedLibrary shell = Library.loadNative(WindowsGameIdentity.class, "arcadium", "shell32");
             MemoryStack stack = MemoryStack.stackPush()) {
            long function = shell.getFunctionAddress("SetCurrentProcessExplicitAppUserModelID");
            if (function == 0 || JNI.callPI(MemoryUtil.memAddress(stack.UTF16(APP_ID)), function) < 0) {
                System.err.println("Arcadium Windows application identity could not be set");
            }
        } catch (RuntimeException | LinkageError exception) {
            System.err.println("Arcadium Windows application identity is unavailable: " + exception.getClass().getSimpleName());
        }
    }
}
