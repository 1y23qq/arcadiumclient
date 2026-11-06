// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Pseudo
@Mixin(targets="org.polyfrost.overflowanimations.legacy.tint.LegacyGlint",remap=false)
public abstract class PlayerFeatures073ModernClockProbe {
    @ModifyVariable(method="setupModernTexturing",at=@At("STORE"),ordinal=0,remap=false)
    private static long features073$clock(long original,float scale,double speed){return PlayerFeatures073Qa.modernClock(original,speed);}
}
