package io.github.arcadiumclient.capture;

import io.github.arcadiumclient.window.GameWindowIcon;
import io.github.arcadiumclient.window.WindowsGameIdentity;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.windows.Kernel32;
import org.lwjgl.system.windows.User32;

public final class SdlGameIconProbe {
    public static void main(String[] args) {
        require(Platform.get() == Platform.WINDOWS, "This native probe requires Windows");
        ByteBuffer[] icons = GameWindowIcon.buffers();
        require(icons != null && icons.length == 6 && icons[0].remaining() == 256 * 256 * 4, "Cached icon sizes are incomplete");
        require(icons == GameWindowIcon.buffers(), "Window icon pixels were decoded again");
        int allocations = -1;
        int userObjects = -1;
        for (int cycle = 0; cycle < 8; cycle++) {
            System.setProperty("arcadium.captureCompatibility", Boolean.toString((cycle & 1) == 0));
            require(SdlCaptureCompatibility.initVideo(SDLInit.SDL_INIT_VIDEO), "SDL failed: " + SDLError.SDL_GetError());
            requireAppId();
            long window = SDLVideo.SDL_CreateWindow("Arcadium game icon probe", 64, 32, SDLVideo.SDL_WINDOW_HIDDEN);
            require(window != 0, "Window creation failed");
            long hwnd = SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(window), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.callocInt(1);
                long originalLarge = User32.GetClassLongPtr(error, hwnd, User32.GCL_HICON);
                long originalSmall = User32.GetClassLongPtr(error, hwnd, User32.GCL_HICONSM);
                for (int replacement = 0; replacement < 24; replacement++) {
                    require(GameWindowIcon.applySdl(window), "Icon failed: " + SDLError.SDL_GetError());
                    assertIcon(hwnd);
                }
                require(SDLVideo.SDL_SetWindowSize(window, 120, 64), "Resize failed");
                assertIcon(hwnd);
                require(User32.GetClassLongPtr(error, hwnd, User32.GCL_HICON) == originalLarge, "Large class icon was changed");
                long restoredSmall = User32.GetClassLongPtr(error, hwnd, User32.GCL_HICONSM);
                require(restoredSmall == originalSmall, "Small class icon was changed");
            } finally {
                GameWindowIcon.releaseSdl(window);
                SDLVideo.SDL_DestroyWindow(window);
                SDLInit.SDL_Quit();
            }
            int remaining = SDLStdinc.SDL_GetNumAllocations();
            if (remaining >= 0) {
                if (allocations == -1) allocations = remaining;
                require(remaining == allocations, "SDL icon allocations accumulated: " + remaining);
            }
            long resources = User32.getLibrary().getFunctionAddress("GetGuiResources");
            int objects = JNI.callPI(Kernel32.GetCurrentProcess(), 1, resources);
            if (userObjects == -1) userObjects = objects;
            require(objects == userObjects, "Window or icon handles accumulated: " + objects + " vs " + userObjects);
            for (ByteBuffer icon : icons) require(icon.position() == 0, "Cached icon pixels were consumed");
            System.out.println("PASS cycle=" + cycle + " replacements=24 icon=small,big class=preserved userObjects=" + objects + " sdlAllocations=" + remaining);
        }
    }

    private static void assertIcon(long hwnd) {
        long function = User32.getLibrary().getFunctionAddress("SendMessageW");
        long large = JNI.callPPPP(hwnd, User32.WM_GETICON, 1L, 0L, function);
        long small = JNI.callPPPP(hwnd, User32.WM_GETICON, 0L, 0L, function);
        require(large != 0 && small != 0, "Window icon is missing");
    }

    private static void requireAppId() {
        try (SharedLibrary shell = Library.loadNative(SdlGameIconProbe.class, "arcadium", "shell32");
             SharedLibrary ole = Library.loadNative(SdlGameIconProbe.class, "arcadium", "ole32");
             MemoryStack stack = MemoryStack.stackPush()) {
            var value = stack.callocLong(1);
            int result = JNI.callPI(MemoryUtil.memAddress(value), shell.getFunctionAddress("GetCurrentProcessExplicitAppUserModelID"));
            require(result >= 0 && value.get(0) != 0, "Windows application identity is missing");
            try {
                require(WindowsGameIdentity.APP_ID.equals(MemoryUtil.memUTF16(value.get(0))), "Windows application identity differs");
            } finally {
                JNI.callPV(value.get(0), ole.getFunctionAddress("CoTaskMemFree"));
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
