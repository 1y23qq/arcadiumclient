package org.polyfrost.oneconfig.internal.ui.compose;

//? if <= 1.8.9 {
/*import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.polyfrost.oneconfig.internal.FontPreferences;

public final class LegacyGameFont {
    private static final int ATLAS_SIZE = 512;
    private static final float OVERSAMPLE = 3f;
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
    private static final Map<Character, InterGlyph> INTER = new HashMap<>();
    private static final Map<Integer, DynamicTexture> VANILLA_PAGES = new HashMap<>();
    private static final ArrayList<DynamicTexture> INTER_PAGES = new ArrayList<>();
    private static final int[] ASCII_INDEX = new int[65536];
    private static final int[] ASCII_WIDTHS = new int[256];
    private static Font inter;
    private static boolean fontAttempted;
    private static BufferedImage vanillaAscii;
    private static byte[] vanillaSizes;
    private static boolean vanillaAttempted;
    private static DynamicTexture atlas;
    private static int atlasX = 1;
    private static int atlasY = 1;
    private static int atlasRowHeight;
    private static int rasterizations;
    private static FontPreferences.Source lastGameSource;

    private LegacyGameFont() {}

    public static void initialize() {
        interFont();
        lastGameSource = FontPreferences.gameSource();
        FontPreferences.setNativeRefresh(() -> {
            FontPreferences.Source source = FontPreferences.gameSource();
            if (source == lastGameSource) return;
            lastGameSource = source;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.tell(() -> {
                if (minecraft.gui != null) minecraft.gui.getChat().reset();
            });
        });
    }

    public static boolean handles(char character) {
        return character != '§' && Character.getType(character) != Character.PRIVATE_USE
            && character != '\u2063' && character != '\u2064'
            && FontPreferences.nativeSource() != FontPreferences.Source.RESOURCE_PACK;
    }

    public static int width(char character, boolean unicode) {
        if (FontPreferences.nativeSource() == FontPreferences.Source.INTER && interFont() != null && inter.canDisplay(character)) {
            return interGlyph(character).advance;
        }
        ensureVanilla();
        if (character == ' ') return 4;
        int index = ASCII_INDEX[character];
        if (!unicode && index >= 0) return ASCII_WIDTHS[index];
        int size = vanillaSizes == null ? 0 : vanillaSizes[character] & 255;
        if (size == 0) return 0;
        int left = size >>> 4;
        int right = size & 15;
        if (right > 7) { left = 0; right = 15; }
        return (right + 1 - left) / 2 + 1;
    }

    public static float draw(char character, boolean italic, boolean unicode, float x, float y) {
        int advance = width(character, unicode);
        if (Character.isWhitespace(character)) return advance;
        if (FontPreferences.nativeSource() == FontPreferences.Source.INTER && interFont() != null && inter.canDisplay(character)) {
            InterGlyph glyph = interGlyph(character);
            if (glyph.texture == null) rasterize(glyph);
            if (glyph.texture != null) {
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                quad(glyph.texture, x + glyph.offsetX, y + glyph.offsetY, glyph.width / OVERSAMPLE,
                    glyph.height / OVERSAMPLE, glyph.u, glyph.v, glyph.u + glyph.width / (float) ATLAS_SIZE,
                    glyph.v + glyph.height / (float) ATLAS_SIZE, italic);
            }
            return advance;
        }
        int index = ASCII_INDEX[character];
        if (!unicode && index >= 0 && vanillaAscii != null) {
            DynamicTexture texture = VANILLA_PAGES.computeIfAbsent(-1, ignored -> new DynamicTexture(vanillaAscii));
            float u = (index % 16) / 16f;
            float v = (index / 16) / 16f;
            float glyphWidth = Math.max(0, advance - 1);
            quad(texture, x, y, glyphWidth, 7.99f, u, v, u + glyphWidth / 128f, v + 7.99f / 128f, italic);
        } else if (vanillaSizes != null && (vanillaSizes[character] & 255) != 0) {
            int page = character >>> 8;
            DynamicTexture texture = VANILLA_PAGES.computeIfAbsent(page, LegacyGameFont::loadVanillaPage);
            int size = vanillaSizes[character] & 255;
            int left = size >>> 4;
            int right = (size & 15) + 1;
            if (texture != null) {
                float u = ((character % 16) * 16 + left) / 256f;
                float v = ((character & 255) / 16) / 16f;
                quad(texture, x, y, (right - left - 0.02f) / 2f, 7.99f,
                    u, v, u + (right - left - 0.02f) / 256f, v + 15.98f / 256f, italic);
            }
        }
        return advance;
    }

    public static int rasterizationCount() { return rasterizations; }

    public static float shadowCorrection(char character, boolean unicode) {
        if (FontPreferences.nativeSource() != FontPreferences.Source.INTER || interFont() == null || !inter.canDisplay(character)) return 0f;
        ensureVanilla();
        return unicode ? 0.5f : ASCII_INDEX[character] < 0 ? 1f : 0f;
    }

    private static Font interFont() {
        if (fontAttempted) return inter;
        fontAttempted = true;
        try (InputStream input = LegacyGameFont.class.getResourceAsStream("/assets/oneconfig/fonts/Inter-Regular.ttf")) {
            if (input != null) inter = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(9f * OVERSAMPLE);
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger("Arcadium/Fonts").warn("Could not load Inter", exception);
        }
        return inter;
    }

    private static InterGlyph interGlyph(char character) {
        return INTER.computeIfAbsent(character, key -> {
            GlyphVector vector = inter.createGlyphVector(FONT_CONTEXT, new char[] { key });
            java.awt.Rectangle bounds = vector.getPixelBounds(FONT_CONTEXT, 0, 0);
            return new InterGlyph(vector, bounds.x, bounds.y, bounds.width, bounds.height,
                Math.max(0, (int) Math.ceil(vector.getGlyphMetrics(0).getAdvanceX() / OVERSAMPLE)));
        });
    }

    private static void rasterize(InterGlyph glyph) {
        if (glyph.width == 0 || glyph.height == 0) return;
        BufferedImage image = new BufferedImage(glyph.width + 2, glyph.height + 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setColor(Color.WHITE);
        graphics.drawGlyphVector(glyph.vector, 1 - glyph.pixelX, 1 - glyph.pixelY);
        graphics.dispose();
        if (atlasX + image.getWidth() >= ATLAS_SIZE) {
            atlasX = 1;
            atlasY += atlasRowHeight + 1;
            atlasRowHeight = 0;
        }
        if (atlas == null || atlasY + image.getHeight() >= ATLAS_SIZE) {
            atlas = new DynamicTexture(ATLAS_SIZE, ATLAS_SIZE);
            INTER_PAGES.add(atlas);
            atlasX = atlasY = 1;
            atlasRowHeight = 0;
        }
        GlStateManager.bindTexture(atlas.getGlId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        IntBuffer pixels = BufferUtils.createIntBuffer(image.getWidth() * image.getHeight());
        pixels.put(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, atlasX, atlasY, image.getWidth(), image.getHeight(),
            GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        glyph.texture = atlas;
        glyph.u = (atlasX + 1) / (float) ATLAS_SIZE;
        glyph.v = (atlasY + 1) / (float) ATLAS_SIZE;
        atlasX += image.getWidth() + 1;
        atlasRowHeight = Math.max(atlasRowHeight, image.getHeight());
        rasterizations++;
    }

    private static void ensureVanilla() {
        if (vanillaAttempted) return;
        vanillaAttempted = true;
        Arrays.fill(ASCII_INDEX, -1);
        try {
            try (InputStream input = vanillaResource("textures/font/ascii.png")) { vanillaAscii = ImageIO.read(input); }
            try (InputStream input = vanillaResource("font/glyph_sizes.bin")) { vanillaSizes = input.readAllBytes(); }
            try (InputStream input = LegacyGameFont.class.getResourceAsStream("/assets/oneconfig/font/legacy_default.json")) {
                JsonObject root = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : root.getAsJsonArray("providers")) {
                    JsonObject provider = element.getAsJsonObject();
                    if (!provider.has("file") || !provider.get("file").getAsString().equals("minecraft:font/ascii.png")) continue;
                    int index = 0;
                    for (JsonElement row : provider.getAsJsonArray("chars")) {
                        for (char character : row.getAsString().toCharArray()) {
                            if (character != 0) ASCII_INDEX[character] = index;
                            index++;
                        }
                    }
                }
            }
            int cellWidth = vanillaAscii.getWidth() / 16;
            int cellHeight = vanillaAscii.getHeight() / 16;
            for (int index = 0; index < 256; index++) {
                int width = 0;
                for (int px = cellWidth - 1; px >= 0 && width == 0; px--) {
                    for (int py = 0; py < cellHeight; py++) {
                        if ((vanillaAscii.getRGB(index % 16 * cellWidth + px, index / 16 * cellHeight + py) >>> 24) != 0) {
                            width = px + 1;
                            break;
                        }
                    }
                }
                ASCII_WIDTHS[index] = Math.round(width * 8f / cellWidth) + 1;
            }
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger("Arcadium/Fonts").warn("Could not load vanilla font assets", exception);
        }
    }

    public static InputStream vanillaResource(String path) throws java.io.IOException {
        return Minecraft.getInstance().getResourcePacks().defaultPack.getResource(new Identifier("minecraft", path));
    }

    private static DynamicTexture loadVanillaPage(int page) {
        try (InputStream input = vanillaResource(String.format(java.util.Locale.ROOT, "textures/font/unicode_page_%02x.png", page))) {
            return new DynamicTexture(ImageIO.read(input));
        } catch (Exception ignored) { return null; }
    }

    private static void quad(DynamicTexture texture, float x, float y, float width, float height,
            float u0, float v0, float u1, float v1, boolean italic) {
        GlStateManager.bindTexture(texture.getGlId());
        float skew = italic ? 1f : 0f;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glTexCoord2f(u0, v0); GL11.glVertex3f(x + skew, y, 0f);
        GL11.glTexCoord2f(u0, v1); GL11.glVertex3f(x - skew, y + height, 0f);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex3f(x + width + skew, y, 0f);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(x + width - skew, y + height, 0f);
        GL11.glEnd();
    }

    private static final class InterGlyph {
        final GlyphVector vector;
        final int pixelX, pixelY, width, height, advance;
        final float offsetX, offsetY;
        DynamicTexture texture;
        float u, v;
        InterGlyph(GlyphVector vector, int x, int y, int width, int height, int advance) {
            this.vector = vector;
            pixelX = x; pixelY = y; this.width = width; this.height = height; this.advance = advance;
            offsetX = x / OVERSAMPLE;
            offsetY = 7f + y / OVERSAMPLE;
        }
    }
}
*///?}
