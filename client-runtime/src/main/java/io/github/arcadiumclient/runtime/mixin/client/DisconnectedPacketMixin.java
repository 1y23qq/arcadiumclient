// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import com.google.common.util.concurrent.ListenableFuture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.PacketUtils;
import net.minecraft.network.handler.PacketHandler;
import net.minecraft.util.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketUtils.class)
public abstract class DisconnectedPacketMixin {
    @WrapOperation(method = "ensureOnSameThread", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BlockableEventLoop;executeTask(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"))
    private static ListenableFuture<?> arcadium$discardClosedConnection(BlockableEventLoop executor, Runnable task, Operation<ListenableFuture<?>> schedule, @Local(argsOnly = true) PacketHandler listener) {
        return schedule.call(executor, (Runnable) () -> {
            if (listener instanceof ClientPlayNetworkHandler play && !play.getConnection().isConnected()) return;
            task.run();
        });
    }
}
