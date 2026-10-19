// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.windows.User32;

final class WindowsGameIcons {
    private static long window;
    private static long hwnd;
    private static long largeIcon;
    private static long smallIcon;

    private WindowsGameIcons() {}

    static boolean apply(long target) {
        long nativeWindow = SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(target), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
        if (nativeWindow == 0) return false;
        long send = User32.getLibrary().getFunctionAddress("SendMessageW");
        if (window == target && hwnd == nativeWindow && largeIcon != 0 && smallIcon != 0) {
            JNI.callPPPP(hwnd, User32.WM_SETICON, 0L, smallIcon, send);
            JNI.callPPPP(hwnd, User32.WM_SETICON, 1L, largeIcon, send);
            return true;
        }
        long large = create(Bitmaps.LARGE);
        if (large == 0) return false;
        long small = create(Bitmaps.SMALL);
        if (small == 0) {
            destroy(large);
            return false;
        }
        release(window);
        window = target;
        hwnd = nativeWindow;
        largeIcon = large;
        smallIcon = small;
        JNI.callPPPP(hwnd, User32.WM_SETICON, 0L, smallIcon, send);
        JNI.callPPPP(hwnd, User32.WM_SETICON, 1L, largeIcon, send);
        return true;
    }

    static void release(long target) {
        if (window == 0 || target != window) return;
        long send = User32.getLibrary().getFunctionAddress("SendMessageW");
        JNI.callPPPP(hwnd, User32.WM_SETICON, 0L, 0L, send);
        JNI.callPPPP(hwnd, User32.WM_SETICON, 1L, 0L, send);
        destroy(largeIcon);
        destroy(smallIcon);
        window = 0;
        hwnd = 0;
        largeIcon = 0;
        smallIcon = 0;
    }

    private static long create(ByteBuffer image) {
        long function = User32.getLibrary().getFunctionAddress("CreateIconFromResourceEx");
        int size = image.getInt(4);
        // Windows x64 uses eight-byte argument slots; omit LR_SHARED so DestroyIcon releases both handles.
        return JNI.callPPJPPPPP(MemoryUtil.memAddress(image), image.remaining(), 1L, 0x00030000L, size, size, 0L, function);
    }

    private static void destroy(long icon) {
        if (icon != 0) JNI.callPI(icon, User32.getLibrary().getFunctionAddress("DestroyIcon"));
    }

    private static ByteBuffer bitmap(ByteBuffer rgba) {
        int size = (int) Math.sqrt(rgba.remaining() / 4);
        int maskStride = (size + 31) / 32 * 4;
        ByteBuffer bitmap = ByteBuffer.allocateDirect(40 + size * size * 4 + maskStride * size).order(ByteOrder.LITTLE_ENDIAN);
        bitmap.putInt(40).putInt(size).putInt(size * 2).putShort((short) 1).putShort((short) 32);
        bitmap.putInt(0).putInt(size * size * 4).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = size - 1; y >= 0; y--) for (int x = 0; x < size; x++) {
            int offset = (y * size + x) * 4;
            bitmap.put(rgba.get(offset + 2)).put(rgba.get(offset + 1)).put(rgba.get(offset)).put(rgba.get(offset + 3));
        }
        while (bitmap.hasRemaining()) bitmap.put((byte) 0);
        return bitmap.flip();
    }

    private static final class Bitmaps {
        private static final ByteBuffer LARGE = bitmap(GameWindowIcon.buffers()[2]);
        private static final ByteBuffer SMALL = bitmap(GameWindowIcon.buffers()[4]);
    }
}
