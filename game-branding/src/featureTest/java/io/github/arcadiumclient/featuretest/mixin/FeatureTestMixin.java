package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;
import io.github.arcadiumclient.featuretest.FeatureQa028;
import io.github.arcadiumclient.featuretest.ForgeQa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FeatureTestMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$featureTick(CallbackInfo callback) { if (io.github.arcadiumclient.featuretest.Feature045Qa.enabled()) { io.github.arcadiumclient.featuretest.Feature045Qa.tick(); return; } if (io.github.arcadiumclient.featuretest.ConnectingQa.enabled()) { io.github.arcadiumclient.featuretest.ConnectingQa.tick(); return; } if (io.github.arcadiumclient.featuretest.Background043Qa.enabled()) { io.github.arcadiumclient.featuretest.Background043Qa.tick(); return; } if (io.github.arcadiumclient.featuretest.Rendering042Qa.enabled()) { io.github.arcadiumclient.featuretest.Rendering042Qa.tick(); return; } if (ForgeQa.enabled()) { ForgeQa.tick(); return; } if (FeatureQa028.enabled()) FeatureQa028.tick(); else FeatureQa.tick(); }

    @Inject(method = "runGame", at = @At("TAIL"))
    private void arcadium$featureFrame(CallbackInfo callback) { if (io.github.arcadiumclient.featuretest.Feature045Qa.enabled()) { io.github.arcadiumclient.featuretest.Feature045Qa.afterFrame(); return; } if (io.github.arcadiumclient.featuretest.ConnectingQa.enabled()) { io.github.arcadiumclient.featuretest.ConnectingQa.afterFrame(); return; } if (io.github.arcadiumclient.featuretest.Background043Qa.enabled()) { io.github.arcadiumclient.featuretest.Background043Qa.afterFrame(); return; } if (io.github.arcadiumclient.featuretest.Rendering042Qa.enabled()) { io.github.arcadiumclient.featuretest.Rendering042Qa.afterFrame(); return; } if (ForgeQa.enabled()) { ForgeQa.afterFrame(); return; } if (FeatureQa028.enabled()) FeatureQa028.afterFrame(); else FeatureQa.afterFrame(); }
}
