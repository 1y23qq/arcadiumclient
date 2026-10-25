// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.compat.evergreenhud;

import androidx.compose.runtime.Composer;
import androidx.compose.runtime.MutableState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import io.github.arcadiumclient.modern.client.features.ItemCounterRows;
import io.github.arcadiumclient.modern.client.features.LegacyItemQueries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("evergreenhud")
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.ItemCounterHud", remap = false)
public abstract class MixinItemCounterHud {
    @Shadow private String[] item;
    @Shadow private boolean showIcon;

    @Unique
    @Dropdown(title = "Layout", options = {"Vertical", "Horizontal"})
    private int arcadium$itemCounterLayout = 0;

    @Unique
    private transient MutableState<List<ItemCounterRows.Entry>> arcadium$rows;
    @Unique
    private transient MutableState<Boolean> arcadium$multiple;
    @Unique
    private transient MutableState<Boolean> arcadium$horizontal;
    @Unique
    private transient LegacyItemQueries.Family arcadium$legacyFamily;

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void arcadium$legacyItem(String query, CallbackInfoReturnable<Item> cir) {
        arcadium$legacyFamily = LegacyItemQueries.missing(query);
        if (!arcadium$legacyFamily.isEmpty()) cir.setReturnValue(arcadium$legacyFamily.representative());
    }

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void arcadium$legacyCount(ItemStack stack, Item target, CallbackInfoReturnable<Boolean> cir) {
        if (arcadium$legacyFamily != null && !arcadium$legacyFamily.isEmpty()) cir.setReturnValue(arcadium$legacyFamily.matches(stack));
    }

    @Unique
    private void arcadium$ensureState() {
        if (arcadium$rows == null) arcadium$rows = ItemCounterRows.newState();
        if (arcadium$multiple == null) arcadium$multiple = ItemCounterRows.newMode();
        if (arcadium$horizontal == null) arcadium$horizontal = ItemCounterRows.newMode();
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void arcadium$multipleItems(CallbackInfo ci) {
        Hud hud = (Hud) (Object) this;
        if (!hud.isReal()) return;
        var selection = hud.getTree().getProp("item");
        if (selection != null) {
            selection.addMetadata("title", "Items");
            selection.addMetadata("description", "Display a separate counter for each selected item, in this order.");
            selection.addMetadata("maxEntries", 0);
            selection.addMetadata("reorderable", true);
            selection.addMetadata("addText", "Select Items");
        }
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lorg/polyfrost/evergreenhud/client/hud/ItemCounterHud;hideIf(Ljava/lang/String;Ljava/util/function/BooleanSupplier;)V"))
    private void arcadium$anyPotion(@Coerce Object hud, String option, BooleanSupplier condition, Operation<Void> original) {
        original.call(hud, option, (BooleanSupplier) () -> Arrays.stream(item).noneMatch("minecraft:potion"::equals));
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void arcadium$updateRows(CallbackInfoReturnable<Boolean> cir) {
        arcadium$ensureState();
        arcadium$horizontal.setValue(arcadium$itemCounterLayout == 1);
        arcadium$multiple.setValue(item.length > 1);
        if (item.length <= 1) {
            if (!arcadium$rows.getValue().isEmpty()) arcadium$rows.setValue(List.of());
            return;
        }
        List<ItemCounterRows.Entry> next = ItemCounterRows.recompute((Hud) (Object) this, item);
        boolean changed = !next.equals(arcadium$rows.getValue());
        if (changed) arcadium$rows.setValue(next);
        cir.setReturnValue(changed);
    }

    @Inject(method = "Content", at = @At("HEAD"), cancellable = true)
    private void arcadium$drawRows(Composer composer, int changed, CallbackInfo ci) {
        arcadium$ensureState();
        if (!arcadium$multiple.getValue()) return;
        ItemCounterRows.Content((Hud) (Object) this, arcadium$rows.getValue(), showIcon, arcadium$horizontal.getValue(), composer, 0);
        ci.cancel();
    }

    @Inject(method = "clone()Lorg/polyfrost/oneconfig/api/hud/v1/Hud;", at = @At("RETURN"))
    private void arcadium$separateRows(CallbackInfoReturnable<Hud> cir) {
        MixinItemCounterHud copy = (MixinItemCounterHud) (Object) cir.getReturnValue();
        copy.arcadium$rows = ItemCounterRows.newState();
        copy.arcadium$multiple = ItemCounterRows.newMode();
        copy.arcadium$horizontal = ItemCounterRows.newMode();
    }
}
