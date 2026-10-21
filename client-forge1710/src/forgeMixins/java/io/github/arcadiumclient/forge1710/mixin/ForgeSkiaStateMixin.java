// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState", remap = false)
public abstract class ForgeSkiaStateMixin {
    @Inject(method = "capture", at = @At("HEAD"))
    private void arcadium$flushGameDraws(CallbackInfo callback) {
        GLStateManager.beforeUncapturedStateChange();
    }

    @Inject(method = "restore", at = @At("RETURN"))
    private void arcadium$restoreGameState(CallbackInfo callback) {
        BackendManager.RENDER_BACKEND.invalidateStateMirror();
        GLStateManager.replayStateToBackend();
    }
}
