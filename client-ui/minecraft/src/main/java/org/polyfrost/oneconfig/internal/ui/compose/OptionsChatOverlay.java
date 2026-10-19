package org.polyfrost.oneconfig.internal.ui.compose;

//? if = 1.8.9 {
/*import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.gui.chat.ChatGui;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen;
import org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState;

public final class OptionsChatOverlay {
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(4);
    private static final StoredGLState STATE = new StoredGLState(330);
    private static ChatGui deferred;
    private static int ticks;
    private static int framebuffer;
    private static boolean replaying;
    private static boolean failed;

    private OptionsChatOverlay() {}

    public static boolean isReplaying() { return replaying; }

    public static boolean defer(ChatGui chat, int currentTicks) {
        if (replaying || failed || !SkiaCtx.INSTANCE.isReady() || !(Platform.screen().current() instanceof OneConfigUIScreen)) return false;
        MODELVIEW.clear(); PROJECTION.clear(); VIEWPORT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        deferred = chat;
        ticks = currentTicks;
        return true;
    }

    public static void render() {
        ChatGui chat = deferred;
        deferred = null;
        if (chat == null || !(Platform.screen().current() instanceof OneConfigUIScreen)) return;
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        STATE.capture();
        replaying = true;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(PROJECTION);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(MODELVIEW);
        boolean previousLiveChat = HudManager.isLiveChatReplay;
        HudManager.isLiveChatReplay = true;
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glViewport(VIEWPORT.get(0), VIEWPORT.get(1), VIEWPORT.get(2), VIEWPORT.get(3));
            chat.render(ticks);
        } catch (RuntimeException failure) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("Arcadium/Chat").warn("Could not replay chat above Options", failure);
        } finally {
            HudManager.isLiveChatReplay = previousLiveChat;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            replaying = false;
            STATE.restore();
            org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLStateKt.resyncTextureBindCache();
        }
    }
}
*///?}
