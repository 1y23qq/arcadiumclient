//? if <= 1.8.9 {
/*package org.polyfrost.oneconfig.internal.legacy;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.lwjgl.system.JNI;
import org.lwjgl.system.windows.GDI32;
import org.lwjgl.system.windows.User32;

record SystemCursorImage(BufferedImage color, BufferedImage invert, int hotX, int hotY) {
    private static final int[] IDS = {32512, 32649, 32513, 32515};
    private static final long LOAD_CURSOR = User32.getLibrary().getFunctionAddress("LoadCursorW");
    private static final long ICON_INFO = User32.getLibrary().getFunctionAddress("GetIconInfo");
    private static final long GET_OBJECT = GDI32.getLibrary().getFunctionAddress("GetObjectW");
    private static final long DELETE_OBJECT = GDI32.getLibrary().getFunctionAddress("DeleteObject");
    private static final long RELEASE_DC = User32.getLibrary().getFunctionAddress("ReleaseDC");
    private static final MethodHandle GET_DIBITS = Linker.nativeLinker().downcallHandle(
            MemorySegment.ofAddress(GDI32.getLibrary().getFunctionAddress("GetDIBits")),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    static SystemCursorImage load(int kind) {
        long cursor = JNI.invokePPP(0L, (long) IDS[kind], LOAD_CURSOR);
        if (cursor == 0) throw new IllegalStateException("Windows cursor unavailable");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(32, 8);
            if (JNI.invokePPI(cursor, info.address(), ICON_INFO) == 0) throw new IllegalStateException("Windows cursor information unavailable");
            long maskHandle = info.get(ValueLayout.JAVA_LONG, 16);
            long colorHandle = info.get(ValueLayout.JAVA_LONG, 24);
            try {
                BufferedImage mask = readBitmap(maskHandle, arena);
                BufferedImage color = colorHandle == 0 ? null : readBitmap(colorHandle, arena);
                int width = mask.getWidth();
                int height = color == null ? mask.getHeight() / 2 : color.getHeight();
                BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                BufferedImage invert = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                boolean alpha = false;
                if (color != null) {
                    for (int y = 0; y < height && !alpha; y++) {
                        for (int x = 0; x < width; x++) if ((color.getRGB(x, y) >>> 24) != 0) { alpha = true; break; }
                    }
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        boolean and = (mask.getRGB(x, y) & 0xffffff) != 0;
                        if (color == null) {
                            boolean xor = (mask.getRGB(x, y + height) & 0xffffff) != 0;
                            if (and && xor) invert.setRGB(x, y, 0xffffffff);
                            else if (!and) pixels.setRGB(x, y, xor ? 0xffffffff : 0xff000000);
                        } else {
                            int argb = color.getRGB(x, y);
                            if (alpha) pixels.setRGB(x, y, straightAlpha(argb));
                            else if (!and) pixels.setRGB(x, y, argb | 0xff000000);
                            else if ((argb & 0xffffff) != 0) invert.setRGB(x, y, 0xffffffff);
                        }
                    }
                }
                return new SystemCursorImage(pixels, invert, info.get(ValueLayout.JAVA_INT, 4), info.get(ValueLayout.JAVA_INT, 8));
            } finally {
                if (maskHandle != 0) JNI.invokePI(maskHandle, DELETE_OBJECT);
                if (colorHandle != 0) JNI.invokePI(colorHandle, DELETE_OBJECT);
            }
        }
    }

    private static BufferedImage readBitmap(long handle, Arena arena) {
        MemorySegment bitmap = arena.allocate(32, 8);
        if (JNI.invokePPI(handle, 32, bitmap.address(), GET_OBJECT) == 0) throw new IllegalStateException("Cursor bitmap information unavailable");
        int width = bitmap.get(ValueLayout.JAVA_INT, 4);
        int height = bitmap.get(ValueLayout.JAVA_INT, 8);
        if (width <= 0 || width > 512 || height <= 0 || height > 1024) throw new IllegalStateException("Invalid cursor bitmap dimensions");
        MemorySegment header = arena.allocate(40, 4);
        header.set(ValueLayout.JAVA_INT, 0, 40);
        header.set(ValueLayout.JAVA_INT, 4, width);
        header.set(ValueLayout.JAVA_INT, 8, -height);
        header.set(ValueLayout.JAVA_SHORT, 12, (short) 1);
        header.set(ValueLayout.JAVA_SHORT, 14, (short) 32);
        MemorySegment bits = arena.allocate((long) width * height * 4, 4);
        long dc = User32.GetDC(0);
        try {
            int rows = (int) GET_DIBITS.invokeExact(MemorySegment.ofAddress(dc), MemorySegment.ofAddress(handle),
                    0, height, bits, header, 0);
            if (rows != height) throw new IllegalStateException("Cursor bitmap pixels unavailable");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, bits.toArray(ValueLayout.JAVA_INT), 0, width);
            return image;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read Windows cursor", failure);
        } finally {
            if (dc != 0) JNI.invokePPI(0L, dc, RELEASE_DC);
        }
    }

    private static int straightAlpha(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) return 0;
        int r = Math.min(255, ((argb >>> 16) & 255) * 255 / alpha);
        int g = Math.min(255, ((argb >>> 8) & 255) * 255 / alpha);
        int b = Math.min(255, (argb & 255) * 255 / alpha);
        return alpha << 24 | r << 16 | g << 8 | b;
    }
}
*///?}
