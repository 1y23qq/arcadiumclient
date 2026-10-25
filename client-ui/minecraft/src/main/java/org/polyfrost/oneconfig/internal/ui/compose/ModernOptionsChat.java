// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.compose;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
import net.minecraft.client.gui.GuiGraphics;
//? if >= 26.2 {
//import net.minecraft.client.renderer.state.gui.GuiRenderState;
//? } else {
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
//? }
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ContentChangeMode;
import org.jetbrains.skia.Paint;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.internal.mixin.render.GameRendererAccessor;
import org.polyfrost.oneconfig.internal.mixin.render.GuiRendererAccessor;
import org.polyfrost.oneconfig.internal.mixin.skia.ModernChatGuiAccess;
import org.polyfrost.oneconfig.internal.ui.SkiaOffscreenTarget;
import org.polyfrost.oneconfig.internal.ui.hud.GuiTargetRedirect;
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen;

public final class ModernOptionsChat {
    private static final boolean DEBUG = Boolean.getBoolean("arcadium.chat.debug");
    private static int deferredFrames, captureFrames, compositeFrames, lastTextCount;
    private static DeltaTracker deferred;
    private static boolean replaying;
    private static boolean hasContent;
    private static boolean failed;
    private static final SkiaOffscreenTarget OFFSCREEN = new SkiaOffscreenTarget();
    private static final Paint PAINT = new Paint();
    static { SkiaCtx.INSTANCE.addPostComposeRenderer(() -> drawInto(SkiaCtx.INSTANCE.getCanvas())); }
    private ModernOptionsChat() { }

    public static boolean defer(DeltaTracker tracker) {
        if (replaying || failed || !SkiaCtx.INSTANCE.isReady()) return false;
        Minecraft client = Minecraft.getInstance();
        //~ if >= 26.2 '.screen' -> '.gui.screen()'
        if (!(client.screen instanceof OneConfigUIScreen) || client.level == null || client.player == null) {
            deferred = null;
            return false;
        }
        deferred = tracker;
        if (DEBUG) deferredFrames++;
        return true;
    }

    public static void prepare() {
        hasContent = false;
        if (SkiaCtx.INSTANCE.isDeferredComposeBackend()) return;
        DeltaTracker tracker = takeFrame();
        if (tracker == null) return;
        Minecraft client = Minecraft.getInstance();
        int width = Platform.screen().viewportWidth(), height = Platform.screen().viewportHeight();
        try {
            if (!OFFSCREEN.resolveTarget(width, height)) return;
            var state = new GuiRenderState();
            //~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
            var graphics = new GuiGraphics(client, state, Platform.screen().guiWidth(), Platform.screen().guiHeight());
            replay(graphics, tracker);
            if (DEBUG) {
                int[] counts = new int[2];
                state.forEachText(text -> counts[0]++);
                state.forEachElement(element -> counts[1]++, GuiRenderState.TraverseRange.ALL);
                lastTextCount = counts[0];
                //~ if >= 26.2 'gui.getChat' -> 'gui.hud.getChat'
                if (captureFrames++ < 12) org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").info("Options chat capture: texts={}, elements={}, deferred={}, nativeFocus={}, target={}x{}, gui={}x{}", counts[0], counts[1], deferredFrames, client.gui.getChat().isChatFocused(), width, height, Platform.screen().guiWidth(), Platform.screen().guiHeight());
            }
            OFFSCREEN.clearTarget();
            var renderer = ((GameRendererAccessor) client.gameRenderer).oneconfig$getGuiRenderer();
            var access = (GuiRendererAccessor) renderer;
            var originalState = access.oneconfig$getRenderState();
            var originalTarget = GuiTargetRedirect.target;
            try {
                access.oneconfig$setRenderState(state);
                GuiTargetRedirect.target = OFFSCREEN.getTarget();
                //? if >= 26.2 {
                //renderer.render();
                //? } else {
                var fog = ((GameRendererAccessor) client.gameRenderer).oneconfig$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE);
                renderer.render(fog);
                //? }
            } finally {
                GuiTargetRedirect.target = originalTarget;
                access.oneconfig$setRenderState(originalState);
            }
            OFFSCREEN.ensureSubmitted();
            hasContent = true;
        } catch (RuntimeException error) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").error("Could not capture native chat above Options", error);
        }
    }

    private static DeltaTracker takeFrame() {
        DeltaTracker tracker = deferred;
        deferred = null;
        Minecraft client = Minecraft.getInstance();
        //? if >= 26.2 {
        //if (!(client.gui.screen() instanceof OneConfigUIScreen) || client.level == null || client.player == null || client.gui.hud.isHidden()) return null;
        //? } else {
        if (!(client.screen instanceof OneConfigUIScreen) || client.level == null || client.player == null || client.options.hideGui) return null;
        //? }
        return tracker != null ? tracker : client.getDeltaTracker();
    }

    //~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
    public static void render(GuiGraphics graphics) {
        if (!SkiaCtx.INSTANCE.isDeferredComposeBackend()) return;
        DeltaTracker tracker = takeFrame();
        if (tracker != null) replay(graphics, tracker);
    }

    //~ if >= 26.2 'GuiGraphics' -> 'GuiGraphicsExtractor'
    private static void replay(GuiGraphics graphics, DeltaTracker tracker) {
        Minecraft client = Minecraft.getInstance();
        boolean previousReplay = HudManager.isLiveChatReplay;
        boolean previousScreen = HudManager.isGuiScreenOpen;
        replaying = true;
        HudManager.isLiveChatReplay = true;
        HudManager.isGuiScreenOpen = false;
        try {
            //~ if >= 26.2 'client.gui)' -> 'client.gui.hud)'
            ((ModernChatGuiAccess) client.gui).arcadium$renderChat(graphics, tracker);
        } finally {
            HudManager.isGuiScreenOpen = previousScreen;
            HudManager.isLiveChatReplay = previousReplay;
            replaying = false;
        }
    }

    private static void drawInto(Canvas canvas) {
        Minecraft client = Minecraft.getInstance();
        //~ if >= 26.2 '.screen' -> '.gui.screen()'
        if (!hasContent || !(client.screen instanceof OneConfigUIScreen) || client.level == null || SkiaCtx.INSTANCE.isDeferredComposeBackend()) return;
        var surface = OFFSCREEN.getSurface();
        if (surface == null) return;
        if (DEBUG && compositeFrames++ < 12) org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").info("Options chat composite: texts={}, captures={}, deferred={}, surface={}x{}", lastTextCount, captureFrames, deferredFrames, surface.getWidth(), surface.getHeight());
        surface.notifyContentWillChange(ContentChangeMode.RETAIN);
        surface.draw(canvas, 0, 0, PAINT);
    }
}
*///?}
