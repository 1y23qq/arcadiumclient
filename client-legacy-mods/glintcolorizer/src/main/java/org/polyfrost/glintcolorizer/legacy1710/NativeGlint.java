// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.glintcolorizer.legacy1710;

//? if =1.7.10 {
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.polyfrost.glintcolorizer.config.GlintConfig;
import org.polyfrost.glintcolorizer.config.GlintEffectOptions;

public final class NativeGlint {
    public static final int HELD = 0, GUI = 1, DROPPED = 2, FRAMED = 3, ARMOR = 4;
    public static final Identifier TEXTURE = new Identifier("textures/misc/enchanted_item_glint.png");
    private static ItemStack stack;
    private static int scope = -1, stroke, depth;
    private static boolean active, textureMatrix;
    private static float brightness = 1;
    private static State[] states = new State[16];

    private static final class State {
        ItemStack stack;
        int scope, stroke;
        boolean active, textureMatrix;
        float brightness;
    }

    private NativeGlint() {}

    public static void enter(ItemStack next, int target) {
        if (depth == states.length) states = Arrays.copyOf(states, states.length * 2);
        State state = states[depth];
        if (state == null) states[depth] = state = new State();
        state.stack = stack; state.scope = scope; state.stroke = stroke;
        state.active = active; state.textureMatrix = textureMatrix; state.brightness = brightness;
        depth++;
        stack = next; scope = target; stroke = 0; active = false; textureMatrix = false; brightness = 1;
    }

    public static void exit() {
        State state = states[--depth];
        stack = state.stack; scope = state.scope; stroke = state.stroke;
        active = state.active; textureMatrix = state.textureMatrix; brightness = state.brightness;
        state.stack = null;
    }

    public static void texture(Identifier id) {
        active = GlintConfig.INSTANCE.getEnabled() && TEXTURE.equals(id);
        if (active) { stroke = 0; brightness = 1; }
    }

    public static void matrix(int mode) { textureMatrix = mode == GL11.GL_TEXTURE; }

    public static long time(long original) {
        GlintEffectOptions options = active ? options() : null;
        return options == null ? original : (long) (original * (double) options.getSpeed());
    }

    public static void color(float red, float green, float blue) {
        if (active && red != 1f && green != 1f && blue != 1f && blue > 0) brightness = blue / .8f;
    }

    public static float scale(float value) {
        GlintEffectOptions options = active && textureMatrix ? options() : null;
        return options == null ? value : value * options.getScale();
    }

    public static float rotation(float original, float x, float y, float z) {
        if (!active || !textureMatrix || x != 0 || y != 0 || z == 0) return original;
        if (scope == ARMOR) {
            int color = GlintConfig.INSTANCE.getArmorGlintToggle() ? 0 : GlintConfig.INSTANCE.getArmorColor().getArgb();
            applyColor(color, brightness);
            return original;
        }
        GlintEffectOptions options = options();
        if (options == null) return original;
        boolean first = stroke++ == 0;
        applyColor(color(options, first), brightness);
        return first ? options.getStrokeRotOne() : options.getStrokeRotTwo();
    }

    private static GlintEffectOptions options() {
        return switch (scope) {
            case HELD -> GlintConfig.INSTANCE.getHeldItem();
            case GUI -> potion() && GlintConfig.INSTANCE.getPotionGlintBackground() ? GlintConfig.INSTANCE.getShinyPots() : GlintConfig.INSTANCE.getGuiItem();
            case DROPPED -> GlintConfig.INSTANCE.getDroppedItem();
            case FRAMED -> GlintConfig.INSTANCE.getFramedItem();
            default -> null;
        };
    }

    private static boolean potion() {
        return scope == GUI && stack != null && stack.getItem() instanceof PotionItem && GlintConfig.INSTANCE.getPotionGlint();
    }

    private static int color(GlintEffectOptions options, boolean first) {
        if (potion() && GlintConfig.INSTANCE.getPotionBasedColor()) return 0xFF000000 | ((PotionItem) stack.getItem()).getPotionColor(stack.getMetadata());
        return options.getIndividualStrokes() ? (first ? options.getStrokeOneColor() : options.getStrokeTwoColor()).getArgb() : options.getGlintColor().getArgb();
    }

    private static void applyColor(int argb, float light) {
        float opacity = (argb >>> 24) / 255f;
        GL11.glColor4f((argb >> 16 & 255) / 255f * light * opacity, (argb >> 8 & 255) / 255f * light * opacity,
            (argb & 255) / 255f * light * opacity, opacity);
    }

    public static void gui(int x, int y, int width, int height, float z) {
        GlintEffectOptions options = GlintConfig.INSTANCE.getGuiItem();
        boolean shiny = potion();
        if (!shiny || !GlintConfig.INSTANCE.getPotionGlintForeground()) guiPass(x, y, width, height, z, options, false);
        if (shiny) {
            GlintEffectOptions custom = GlintConfig.INSTANCE.getPotionGlintBackground() ? GlintConfig.INSTANCE.getShinyPots() : options;
            if (GlintConfig.INSTANCE.getPotionGlintSize()) { x -= 2; y -= 2; width += 4; height += 4; }
            guiPass(x, y, width, height, z, custom, true);
        }
    }

    private static void guiPass(int x, int y, int width, int height, float z, GlintEffectOptions options, boolean shiny) {
        int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            if (shiny && GlintConfig.INSTANCE.getPotionGlintSize()) GL11.glDepthFunc(GL11.GL_LEQUAL);
            for (int pass = 0; pass < 2; pass++) {
                GL11.glLoadIdentity();
                float scale = Math.max(.001f, options.getScale());
                GL11.glScalef(scale, scale, 1);
                GL11.glTranslatef((float) ((Minecraft.getTime() * (double) options.getSpeed()) % (pass == 0 ? 3000 : 4873)) / (pass == 0 ? 3000f : -4873f), 0, 0);
                GL11.glRotatef(pass == 0 ? options.getStrokeRotOne() : options.getStrokeRotTwo(), 0, 0, 1);
                applyColor(color(options, pass == 0), 1);
                Tesselator vertices = Tesselator.INSTANCE;
                vertices.begin();
                vertices.vertex(x, y + height, z, 0, .25);
                vertices.vertex(x + width, y + height, z, .25, .25);
                vertices.vertex(x + width, y, z, .25, 0);
                vertices.vertex(x, y, z, 0, 0);
                vertices.end();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrix); GL11.glPopAttrib();
        }
    }
}
//?}
