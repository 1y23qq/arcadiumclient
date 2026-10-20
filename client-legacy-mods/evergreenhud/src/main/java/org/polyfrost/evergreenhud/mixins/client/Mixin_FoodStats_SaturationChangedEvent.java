// Derived from AppleSkin (https://github.com/squeek502/AppleSkin), made by squeek502.
// AppleSkin is licensed under the Unlicense (public domain).

package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.world.food.FoodData;
import org.polyfrost.evergreenhud.client.utils.SaturationTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class Mixin_FoodStats_SaturationChangedEvent {
    @Inject(method = "setSaturation", at = @At("RETURN"))
    private void evergreenhud$onServerSync(float saturation, CallbackInfo ci) {
        SaturationTracker.INSTANCE.onServerSync((FoodData) (Object) this);
    }
}
