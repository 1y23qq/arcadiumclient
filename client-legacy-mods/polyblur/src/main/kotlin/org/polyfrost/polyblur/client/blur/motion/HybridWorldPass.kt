package org.polyfrost.polyblur.client.blur.motion

//? if >1.21.5 {
/*import net.minecraft.client.render.pipeline.RenderTarget
import org.polyfrost.polyblur.client.blur.phosphor.RenderTargetTracker
import org.polyfrost.polyblur.client.blur.phosphor.WorldSnapshotTracker

object HybridWorldPass {
    @JvmStatic
    fun run(mainTarget: RenderTarget) {
        val snapshot = WorldSnapshotTracker.ensure(mainTarget) ?: return

        if (WorldCamera.velocitySettled) {
            WorldSnapshotTracker.markCaptured(RenderTargetTracker.blit(mainTarget, snapshot))
            return
        }

        MotionVelocityPass.run(mainTarget)
        if (MotionBlurReproject.render(mainTarget, snapshot)) {
            WorldSnapshotTracker.markCaptured(true)
        } else {
            WorldSnapshotTracker.markCaptured(RenderTargetTracker.blit(mainTarget, snapshot))
        }
    }
}
*///?}
