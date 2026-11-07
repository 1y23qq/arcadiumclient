// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainFullFrameOracle073;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="org.polyfrost.polyblur.client.blur.motion.MotionBlur",remap=false)
public abstract class TerrainFullFrameOracle073BlurProbe {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private static void terrain073$blur(@Coerce Object target,CallbackInfo callback){TerrainFullFrameOracle073.blurRender();}
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private static void terrain073$blurExit(@Coerce Object target,CallbackInfo callback){TerrainFullFrameOracle073.blurReturned();}
}
