package io.github.arcadiumclient.capture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.User32;

public final class SdlCaptureProbe {
    public static void main(String[] args) {
        require(Platform.get() == Platform.WINDOWS, "This native probe requires Windows");
        int remainingAllocations = -1;
        for (boolean enabled : new boolean[] {true, false, true, false}) {
            System.setProperty("arcadium.captureCompatibility", Boolean.toString(enabled));
            require(SDLInit.SDL_SetAppMetadata("Arcadium Client", "capture probe", "io.github.arcadiumclient"), "Metadata failed");
            require(SdlCaptureCompatibility.initVideo(SDLInit.SDL_INIT_VIDEO), "SDL video failed: " + SDLError.SDL_GetError());
            long window = SDLVideo.SDL_CreateWindow("Arcadium Client 1.8.9 capture probe", 64, 32, SDLVideo.SDL_WINDOW_HIDDEN);
            require(window != 0, "Window creation failed");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int properties = SDLVideo.SDL_GetWindowProperties(window);
                long handle = SDLProperties.SDL_GetPointerProperty(properties, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
                require(handle != 0, "Native window handle missing");
                ByteBuffer className = stack.calloc(512);
                long getClassName = User32.getLibrary().getFunctionAddress("GetClassNameW");
                require(getClassName != 0, "GetClassNameW unavailable");
                int length = JNI.callPPI(handle, MemoryUtil.memAddress(className), 256, getClassName);
                require(length > 0, "Native class name missing");
                String actual = MemoryUtil.memUTF16(className, length);
                String expected = enabled ? "LWJGL" : "SDL_app";
                require(expected.equals(actual), "Expected " + expected + ", got " + actual);
                IntBuffer error = stack.callocInt(1);
                long style = User32.GetClassLongPtr(error, handle, User32.GCL_STYLE);
                require(style == (User32.CS_BYTEALIGNCLIENT | User32.CS_OWNDC), "Window class style changed: " + style);
                require(SDLVideo.SDL_SetWindowSize(window, 96, 48), "Window resize failed");
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                require(SDLVideo.SDL_GetWindowSize(window, width, height), "Window size unavailable");
                require(width.get(0) == 96 && height.get(0) == 48, "Window size did not update");
                System.out.println("PASS enabled=" + enabled + " class=" + actual + " style=0x" + Long.toHexString(style) + " resized=96x48");
            } finally {
                SDLVideo.SDL_DestroyWindow(window);
                SDLInit.SDL_Quit();
            }
            int remaining = SDLStdinc.SDL_GetNumAllocations();
            if (remainingAllocations == -1) remainingAllocations = remaining;
            require(remaining == remainingAllocations, "SDL allocation count changed after repeated shutdown: " + remaining);
        }
        System.setProperty("arcadium.captureCompatibility", "true");
        require(SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DRIVER, "arcadium_missing_video_driver"), "Driver hint failed");
        require(!SdlCaptureCompatibility.initVideo(SDLInit.SDL_INIT_VIDEO), "Invalid video driver unexpectedly succeeded");
        SDLInit.SDL_Quit();
        require(SDLStdinc.SDL_GetNumAllocations() == remainingAllocations, "Failed initialization retained SDL allocations");
        require(SdlCaptureCompatibility.initVideo(SDLInit.SDL_INIT_VIDEO), "SDL failed to recover after initialization failure");
        SDLInit.SDL_Quit();
        require(SDLStdinc.SDL_GetNumAllocations() == remainingAllocations, "Recovery retained SDL allocations");
        System.out.println("PASS failed initialization released registration and recovered");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
