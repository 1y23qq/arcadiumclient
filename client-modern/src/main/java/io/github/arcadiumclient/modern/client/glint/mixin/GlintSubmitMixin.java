// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import io.github.arcadiumclient.modern.client.glint.*;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeStorage.ItemSubmit.class)
public class GlintSubmitMixin implements GlintItemData {
    @Unique private Data arcadium$data = Data.EMPTY;
    public Data arcadium$glintData() { return arcadium$data; }
    public void arcadium$glintData(Data data) { arcadium$data = data; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void arcadium$capture(CallbackInfo callback) { arcadium$data = ModernGlint.current(); }
}
