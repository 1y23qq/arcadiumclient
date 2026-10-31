package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.Features062Qa;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientPlayerEntity.class)
public class Skin062Probe {
    @Inject(method="getSkinTextureLocation()Lnet/minecraft/resource/Identifier;",at=@At("HEAD"),cancellable=true)
    private void release$skin(CallbackInfoReturnable<Identifier> ci){if(Features062Qa.enabled() && Features062Qa.skin!=null)ci.setReturnValue(Features062Qa.skin);}
}
