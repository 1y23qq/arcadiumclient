package org.polyfrost.oneconfig.internal;

import org.polyfrost.compose.mc.McFontQueue;
import org.polyfrost.oneconfig.api.config.v1.Property;

public final class FontPreferences {
    public enum Source { RESOURCE_PACK, VANILLA, INTER }
    private static final ThreadLocal<Boolean> MOD_TEXT = ThreadLocal.withInitial(() -> false);
    private static Runnable nativeRefresh;

    private FontPreferences() {}

    public static Source gameSource() {
        return source(OneConfigConfig.useInterInGame, OneConfigConfig.useVanillaFontInGame);
    }

    public static Source modSource() {
        return modSource(OneConfigConfig.useInterForModText,
            OneConfigConfig.useVanillaFontForModText, OneConfigConfig.useVanillaFontInGame);
    }

    public static Source modSource(boolean inter, boolean vanilla, boolean gameVanilla) {
        return source(inter, vanilla || gameVanilla);
    }

    public static Source nativeSource() {
        return MOD_TEXT.get() ? modSource() : gameSource();
    }

    public static boolean beginModText() {
        boolean previous = MOD_TEXT.get();
        MOD_TEXT.set(true);
        return previous;
    }

    public static void endModText(boolean previous) {
        MOD_TEXT.set(previous);
    }

    public static Source source(boolean inter, boolean vanilla) {
        return inter ? Source.INTER : vanilla ? Source.VANILLA : Source.RESOURCE_PACK;
    }

    public static void setVanilla(boolean modText, boolean enabled) {
        OneConfigConfig config = OneConfigConfig.INSTANCE;
        if (config == null || config.getTree() == null) return;
        Property<?> property = config.getTree().getProp(modText ? "useVanillaFontForModText" : "useVanillaFontInGame");
        if (property == null) return;
        property.setAs(enabled);
        config.save();
    }

    public static void refresh() {
        McFontQueue.INSTANCE.setRevision(McFontQueue.INSTANCE.getRevision() + 1);
        if (nativeRefresh != null) nativeRefresh.run();
    }

    public static void setNativeRefresh(Runnable action) { nativeRefresh = action; }
}
