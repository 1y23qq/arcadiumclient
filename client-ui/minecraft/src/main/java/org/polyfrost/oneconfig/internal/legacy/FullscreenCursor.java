//? if = 1.8.9 {
/*package org.polyfrost.oneconfig.internal.legacy;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

public final class FullscreenCursor {
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private static int cell;
    private static SystemCursorImage[] images;
    private static boolean software;
    private static boolean unavailable;
    private static int icon;
    private static int texture;

    private FullscreenCursor() {}

    public static void setIcon(int kind) {
        icon = kind;
    }

    public static void render() {
        if (!WINDOWS || unavailable || !Mouse.isCreated() || !Display.isCreated()) return;
        boolean menu = Minecraft.getInstance().screen != null && !Mouse.isGrabbed();
        boolean wanted = menu && Display.isActive() && Display.isFullscreen();
        if (!wanted) {
            if (software || menu && Display.isActive() && !SDLMouse.SDL_CursorVisible()) {
                software = false;
                if (Mouse.isGrabbed()) SDLMouse.SDL_HideCursor();
                else SDLMouse.SDL_ShowCursor();
            }
            return;
        }
        int width = Display.getWidth();
        int height = Display.getHeight();
        if (width <= 0 || height <= 0) return;
        float x;
        float y;
        long handle = Platform.compatibility().windowHandle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pointerX = stack.callocFloat(1);
            FloatBuffer pointerY = stack.callocFloat(1);
            IntBuffer originX = stack.callocInt(1);
            IntBuffer originY = stack.callocInt(1);
            IntBuffer windowW = stack.callocInt(1);
            IntBuffer windowH = stack.callocInt(1);
            SDLMouse.SDL_GetGlobalMouseState(pointerX, pointerY);
            if (!SDLVideo.SDL_GetWindowPosition(handle, originX, originY)
                    || !SDLVideo.SDL_GetWindowSize(handle, windowW, windowH) || windowW.get(0) <= 0 || windowH.get(0) <= 0) return;
            x = framebufferCoordinate(pointerX.get(0), originX.get(0), width, windowW.get(0));
            y = framebufferCoordinate(pointerY.get(0), originY.get(0), height, windowH.get(0));
        }
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        try {
            draw(x, y, width, height);
        } catch (RuntimeException | LinkageError failure) {
            unavailable = true;
            software = false;
            SDLMouse.SDL_ShowCursor();
            org.apache.logging.log4j.LogManager.getLogger("Arcadium Cursor").error("Could not read or draw the Windows cursor", failure);
            return;
        }
        software = true;
        if (SDLMouse.SDL_CursorVisible()) SDLMouse.SDL_HideCursor();
    }

    static float framebufferCoordinate(float position, int origin, int pixels, int windowSize) {
        return (position - origin) * pixels / windowSize;
    }

    private static void draw(float x, float y, int width, int height) {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL20.glUseProgram(0);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColorMask(true, true, true, true);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, width, height, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            if (texture == 0) texture = uploadAtlas();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            float size = cell;
            float left = Math.round(x) - images[icon].hotX();
            float top = Math.round(y) - images[icon].hotY();
            float u = icon / 4f;
            quad(left, top, size, u, 0);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .5f);
            GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            quad(left, top, size, u, .5f);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL20.glUseProgram(program);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glPopAttrib();
        }
    }

    private static void quad(float left, float top, float size, float u, float v) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u, v); GL11.glVertex2f(left, top);
        GL11.glTexCoord2f(u + .25f, v); GL11.glVertex2f(left + size, top);
        GL11.glTexCoord2f(u + .25f, v + .5f); GL11.glVertex2f(left + size, top + size);
        GL11.glTexCoord2f(u, v + .5f); GL11.glVertex2f(left, top + size);
        GL11.glEnd();
    }

    private static int uploadAtlas() {
        BufferedImage atlas = createAtlas();
        ByteBuffer pixels = BufferUtils.createByteBuffer(atlas.getWidth() * atlas.getHeight() * 4);
        for (int y = 0; y < atlas.getHeight(); y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                int rgba = atlas.getRGB(x, y);
                pixels.put((byte) (rgba >> 16)).put((byte) (rgba >> 8)).put((byte) rgba).put((byte) (rgba >> 24));
            }
        }
        pixels.flip();
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlas.getWidth(), atlas.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return id;
        } finally {
            GL11.glPopClientAttrib();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        }
    }

    static BufferedImage createAtlas() {
        images = new SystemCursorImage[4];
        cell = 0;
        for (int i = 0; i < images.length; i++) {
            images[i] = SystemCursorImage.load(i);
            cell = Math.max(cell, Math.max(images[i].color().getWidth(), images[i].color().getHeight()));
        }
        BufferedImage atlas = new BufferedImage(cell * 4, cell * 2, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < images.length; i++) {
            var image = images[i];
            for (int y = 0; y < image.color().getHeight(); y++) {
                for (int x = 0; x < image.color().getWidth(); x++) {
                    atlas.setRGB(i * cell + x, y, image.color().getRGB(x, y));
                    atlas.setRGB(i * cell + x, cell + y, image.invert().getRGB(x, y));
                }
            }
        }
        return atlas;
    }
}
*///?}
