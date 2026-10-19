//? if = 1.8.9 {
/*package org.polyfrost.oneconfig.internal.legacy;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

public final class FullscreenCursorRenderProbe {
    public static void main(String[] args) throws Exception {
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) throw new AssertionError("SDL video initialization failed");
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_COMPATIBILITY);
        long window = SDLVideo.SDL_CreateWindow("Arcadium cursor test", 256, 256, SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
        long context = SDLVideo.SDL_GL_CreateContext(window);
        if (context == 0) throw new AssertionError("OpenGL context creation failed");
        try {
            SDLVideo.SDL_GL_MakeCurrent(window, context);
            GL.createCapabilities();
            SDLMouse.SDL_HideCursor();
            Method draw = FullscreenCursor.class.getDeclaredMethod("draw", float.class, float.class, int.class, int.class);
            draw.setAccessible(true);
            int fbo = GL30.glGenFramebuffers();
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
            int[] baseline = new int[4];
            for (float density : new float[] {1, 1.5f, 2}) {
                float coordinate = FullscreenCursor.framebufferCoordinate(-1824, -1920, Math.round(256 * density), 256);
                if (coordinate != 96 * density) throw new AssertionError("Incorrect desktop-to-framebuffer scaling");
                for (int icon = 0; icon < 4; icon++) {
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
                    GL11.glClearColor(.1f, .12f, .15f, 1);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GL11.glViewport(4, 6, 180, 190);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(0, 0, 1, 1);
                    GL13.glActiveTexture(GL13.GL_TEXTURE1);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
                    FullscreenCursor.setIcon(icon);
                    draw.invoke(null, 96f, 96f, 256, 256);
                    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                    if (viewport.get(0) != 4 || viewport.get(1) != 6 || viewport.get(2) != 180 || viewport.get(3) != 190
                            || !GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                            || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE1
                            || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != fbo) {
                        throw new AssertionError("Cursor changed the caller's OpenGL state");
                    }
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                    GL11.glReadBuffer(GL11.GL_BACK);
                    pixels.clear();
                    GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    int visible = 0;
                    int backgroundR = Byte.toUnsignedInt(pixels.get(0));
                    int backgroundG = Byte.toUnsignedInt(pixels.get(1));
                    int backgroundB = Byte.toUnsignedInt(pixels.get(2));
                    for (int p = 0; p < pixels.limit(); p += 4) {
                        if (Math.abs(Byte.toUnsignedInt(pixels.get(p)) - backgroundR) > 2
                                || Math.abs(Byte.toUnsignedInt(pixels.get(p + 1)) - backgroundG) > 2
                                || Math.abs(Byte.toUnsignedInt(pixels.get(p + 2)) - backgroundB) > 2) visible++;
                    }
                    if (visible < 10) throw new AssertionError("Software cursor was not visible: " + icon);
                    if (density == 1) baseline[icon] = visible;
                    else if (visible != baseline[icon]) throw new AssertionError("DPI scaled the Windows cursor twice");
                    SystemCursorImage system = SystemCursorImage.load(icon);
                    if (SDLMouse.SDL_CursorVisible()) throw new AssertionError("System cursor became visible");
                    if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL cursor error");
                    System.out.println("PASS pointer=" + icon + " scale=" + density + " pixels=" + visible
                            + " nativeSize=" + system.color().getWidth() + "x" + system.color().getHeight()
                            + " hotspot=" + system.hotX() + "," + system.hotY());
                }
            }
            ImageIO.write(FullscreenCursor.createAtlas(), "png", Path.of(args[0]).toFile());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(fbo);
        } finally {
            SDLVideo.SDL_GL_DestroyContext(context);
            SDLVideo.SDL_DestroyWindow(window);
            SDLInit.SDL_Quit();
        }
    }
}
*///?}
