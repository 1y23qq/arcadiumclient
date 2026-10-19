// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.options.ServerListEntry;
import org.lwjgl.input.Mouse;
import io.github.arcadiumclient.runtime.client.gui.ServerListDrag;
import io.github.arcadiumclient.runtime.mixin.client.access.LegacyListWidgetAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MixinServerListDrag extends Screen {
    @Shadow private MultiplayerServerListWidget serverList;
    @Unique private final ServerListDrag<ServerListEntry> arcadium$drag = new ServerListDrag<>();
    @Unique private int arcadium$target = -1;
    @Unique private long arcadium$frameTime;
    @Unique private double arcadium$scrollRemainder;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$press(int x, int y, int button, CallbackInfo ci) {
        if (serverList == null) return;
        if (button != 0 || !serverList.isMouseInList(y)) {
            arcadium$cancel();
            return;
        }
        var screen = (MultiplayerScreen) (Object) this;
        var servers = screen.getServerList();
        int index = serverList.getEntryAt(x, y);
        int left = arcadium$rowLeft();
        if (index < 0 || index >= servers.size() || x <= left + 32 || x >= left + serverList.getRowWidth()) {
            arcadium$cancel();
            return;
        }
        if (!arcadium$drag.begin(servers.get(index), index, x, y, button)) return;
        arcadium$target = index;
        arcadium$frameTime = System.nanoTime();
        arcadium$scrollRemainder = 0;
        screen.moveToServer(index);
        serverList.setScrolling(false);
        ci.cancel();
    }

    @WrapOperation(
        method = "handleMouse",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/menu/multiplayer/MultiplayerServerListWidget;handleMouse()V")
    )
    private void arcadium$keepWheelScrolling(MultiplayerServerListWidget list, Operation<Void> original) {
        if (!arcadium$drag.active() || Mouse.getEventDWheel() != 0) original.call(list);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void arcadium$trackDrag(int x, int y, float tickDelta, CallbackInfo ci) {
        if (!arcadium$drag.active()) return;
        if (!Mouse.isButtonDown(0)) {
            arcadium$cancel();
            return;
        }
        arcadium$drag.update(x, y);
        long now = System.nanoTime();
        var bounds = (LegacyListWidgetAccessor) serverList;
        if (arcadium$drag.dragging()) {
            arcadium$scrollRemainder += ServerListDrag.scrollDelta(x >= arcadium$rowLeft() - 24
                && x <= arcadium$rowLeft() + serverList.getRowWidth() + 24 ? y : (bounds.arcadium$top() + bounds.arcadium$bottom()) / 2,
                bounds.arcadium$top(), bounds.arcadium$bottom(), (now - arcadium$frameTime) / 1_000_000.0);
            int scroll = (int) arcadium$scrollRemainder;
            if (scroll != 0) {
                serverList.scroll(scroll);
                arcadium$scrollRemainder -= scroll;
            }
        }
        arcadium$frameTime = now;
        arcadium$target = arcadium$targetAt(y);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void arcadium$dropMarker(int x, int y, float tickDelta, CallbackInfo ci) {
        if (!arcadium$drag.dragging() || arcadium$target < 0) return;
        var bounds = (LegacyListWidgetAccessor) serverList;
        int row = arcadium$target + (arcadium$target > arcadium$drag.source() ? 1 : 0);
        int lineY = bounds.arcadium$top() + 4 + bounds.arcadium$headerHeight()
            - serverList.getScrollAmount() + row * serverList.getEntryHeight();
        lineY = Math.max(bounds.arcadium$top() + 1, Math.min(bounds.arcadium$bottom() - 2, lineY));
        int left = arcadium$rowLeft();
        GuiElement.fill(left, lineY, left + serverList.getRowWidth() - 4, lineY + 2, 0xFFE2E2E2);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void arcadium$release(int x, int y, int button, CallbackInfo ci) {
        if (button != 0 || !arcadium$drag.active()) return;
        var screen = (MultiplayerScreen) (Object) this;
        var pressed = arcadium$drag.pressed();
        int left = arcadium$rowLeft();
        boolean inside = serverList.isMouseInList(y) && x >= left && x < left + serverList.getRowWidth();
        var result = arcadium$drag.release(x, y, arcadium$targetAt(y), inside, net.minecraft.client.Minecraft.getTime());
        serverList.setScrolling(true);
        arcadium$target = -1;
        arcadium$scrollRemainder = 0;
        if (result.kind() == ServerListDrag.Kind.DROP) {
            var servers = screen.getServerList();
            var entries = new ArrayList<ServerListEntry>(servers.size());
            for (int i = 0; i < servers.size(); i++) entries.add(servers.get(i));
            int from = entries.indexOf(pressed);
            if (ServerListDrag.reorder(entries, from, result.target())) {
                for (int i = 0; i < entries.size(); i++) servers.set(i, entries.get(i));
                servers.save();
                serverList.setServers(servers);
                screen.moveToServer(result.target());
            }
        } else if (result.kind() == ServerListDrag.Kind.DOUBLE_CLICK) {
            screen.connect();
        }
        ci.cancel();
    }

    @Inject(method = "connect()V", at = @At("HEAD"), cancellable = true)
    private void arcadium$preventJoinWhileDragging(CallbackInfo ci) {
        if (arcadium$drag.active()) ci.cancel();
    }

    @Inject(method = {"removed", "init"}, at = @At("HEAD"))
    private void arcadium$reset(CallbackInfo ci) {
        arcadium$cancel();
    }

    @Unique
    private int arcadium$rowLeft() {
        var bounds = (LegacyListWidgetAccessor) serverList;
        return bounds.arcadium$left() + (bounds.arcadium$width() - serverList.getRowWidth()) / 2 + 2;
    }

    @Unique
    private int arcadium$targetAt(int y) {
        var bounds = (LegacyListWidgetAccessor) serverList;
        return ServerListDrag.targetIndex(y, bounds.arcadium$top() + 4 + bounds.arcadium$headerHeight(),
            serverList.getScrollAmount(), serverList.getEntryHeight(), ((MultiplayerScreen) (Object) this).getServerList().size());
    }

    @Unique
    private void arcadium$cancel() {
        arcadium$drag.cancel();
        arcadium$target = -1;
        arcadium$scrollRemainder = 0;
        if (serverList != null) serverList.setScrolling(true);
    }
}
