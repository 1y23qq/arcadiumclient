package tomeko.screenshotmessageenhancer.compat;

//? if = 1.7.10 {
public final class PixelRows {
    private PixelRows() {}

    public static void flipVertical(int[] pixels, int width, int height) {
        if (width <= 0 || height <= 1) return;
        if (Math.multiplyExact(width, height) > pixels.length) throw new IllegalArgumentException("Invalid screenshot dimensions");
        int[] row = new int[width];
        for (int y = 0; y < height / 2; y++) {
            int top = y * width;
            int bottom = (height - 1 - y) * width;
            System.arraycopy(pixels, top, row, 0, width);
            System.arraycopy(pixels, bottom, pixels, top, width);
            System.arraycopy(row, 0, pixels, bottom, width);
        }
    }
}
//?}
