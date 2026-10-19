package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.AccountChooserQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen", remap = false)
public class AccountChooserInputMixin {
    @Inject(target = @Desc(value = "pointerPosition-F1C5BW0", ret = long.class), at = @At("HEAD"), cancellable = true)
    private void arcadium$fixturePointer(CallbackInfoReturnable<Long> cir) {
        if (AccountChooserQa.enabled()) cir.setReturnValue(AccountChooserQa.pointer());
    }
}
