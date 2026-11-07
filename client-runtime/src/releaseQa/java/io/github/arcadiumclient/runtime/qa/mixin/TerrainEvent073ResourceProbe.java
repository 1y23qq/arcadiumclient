// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainEvent073Evidence;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class TerrainEvent073ResourceProbe {
 @Inject(method="reloadResources()V",at=@At("HEAD")) private void eventReloadHead(CallbackInfo callback){TerrainEvent073Evidence.resource("reload.head");}
 @Inject(method="reloadResources()V",at=@At("RETURN")) private void eventReloadReturn(CallbackInfo callback){TerrainEvent073Evidence.resource("reload.return");}
 @Inject(method={"setWorld(Lnet/minecraft/client/world/ClientWorld;)V","setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V"},at=@At("RETURN")) private void eventWorldReturn(CallbackInfo callback){TerrainEvent073Evidence.resource("world.return");}
}
