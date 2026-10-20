package org.polyfrost.vanillahud.mixin;

//? if = 1.7.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GameGui;
import net.minecraft.util.profiler.Profiler;
import org.polyfrost.vanillahud.compat.LegacyDrawContext;
import org.polyfrost.vanillahud.hud.HotbarHud;
import org.polyfrost.vanillahud.hud.Huds;
import org.polyfrost.vanillahud.hud.VanillaHud;
import org.polyfrost.vanillahud.render.HudTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public class LegacyInlineHudMixin {
    @Unique private VanillaHud vanillahud$region;
    @Unique private String vanillahud$section;
    @Unique private int vanillahud$nested;
    @Unique private boolean vanillahud$frames;
    @Unique private float vanillahud$slot;
    @Unique private long vanillahud$slotTime;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V"))
    private void vanillahud$beginRegion(Profiler profiler, String section, Operation<Void> original) {
        original.call(profiler, section);
        if (vanillahud$region != null) {
            vanillahud$nested++;
            return;
        }
        VanillaHud hud = switch (section) {
            case "actionBar", "expBar", "expLevel", "jumpBar" -> Huds.INSTANCE.getHotbar();
            case "toolHighlight" -> Huds.INSTANCE.getHeldItemTooltip();
            case "overlayMessage" -> Huds.INSTANCE.getActionBar();
            default -> null;
        };
        if (hud != null) {
            vanillahud$region = hud;
            vanillahud$section = section;
            HudTransform.begin(LegacyDrawContext.INSTANCE, hud);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiler/Profiler;pop()V"))
    private void vanillahud$endRegion(Profiler profiler, Operation<Void> original) {
        if (vanillahud$region != null) {
            if (vanillahud$nested > 0) vanillahud$nested--;
            else {
                HudTransform.end(LegacyDrawContext.INSTANCE);
                vanillahud$region = null;
                vanillahud$section = null;
            }
        }
        original.call(profiler);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V"))
    private void vanillahud$texture(GameGui gui, int x, int y, int u, int v, int width, int height,
                                   Operation<Void> original) {
        HotbarHud hud = Huds.INSTANCE.getHotbar();
        if (width == 182 && height == 22 && u == 0 && v == 0) {
            if (!hud.shouldDraw()) return;
            HudTransform.begin(LegacyDrawContext.INSTANCE, hud);
            vanillahud$frames = true;
            original.call(gui, x, y, u, v, width, height);
            return;
        }
        if (width == 24 && height == 22 && u == 0 && v == 22) {
            if (!vanillahud$frames) return;
            var player = Minecraft.getInstance().player;
            int selected = player == null ? 0 : player.inventory.selectedSlot;
            long now = System.nanoTime();
            if (vanillahud$slotTime == 0L || !hud.getAnimation() || Math.abs(selected - vanillahud$slot) > 4.5f) {
                vanillahud$slot = selected;
            } else {
                float ticks = (now - vanillahud$slotTime) / 50_000_000f;
                vanillahud$slot += (selected - vanillahud$slot) * Math.min(1f, ticks * 0.6f);
            }
            vanillahud$slotTime = now;
            x = LegacyDrawContext.INSTANCE.guiWidth() / 2 - 92 + Math.round(vanillahud$slot * 20f);
            try {
                original.call(gui, x, y, u, v, width, height);
            } finally {
                vanillahud$frames = false;
                HudTransform.end(LegacyDrawContext.INSTANCE);
            }
            return;
        }
        if (vanillahud$region != null && !vanillahud$region.shouldDraw()) return;
        if (height == 5 && (v == 64 || v == 69) && !hud.getExperienceBar()) return;
        original.call(gui, x, y, u, v, width, height);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GameGui;renderItemSlot(IIIF)V"))
    private void vanillahud$item(GameGui gui, int slot, int x, int y, float tickDelta, Operation<Void> original) {
        HotbarHud hud = Huds.INSTANCE.getHotbar();
        if (!hud.shouldDraw()) return;
        HudTransform.beginUpright(LegacyDrawContext.INSTANCE, hud, x + 8f, y + 8f);
        try {
            original.call(gui, slot, x, y, tickDelta);
        } finally {
            HudTransform.endUpright(LegacyDrawContext.INSTANCE);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;draw(Ljava/lang/String;III)I"))
    private int vanillahud$text(Font font, String text, int x, int y, int color, Operation<Integer> original) {
        if (vanillahud$region != null && !vanillahud$region.shouldDraw()) return 0;
        if ("expLevel".equals(vanillahud$section)) {
            HotbarHud hud = Huds.INSTANCE.getHotbar();
            if (!hud.getExperienceLevel()) return 0;
            HudTransform.beginUpright(LegacyDrawContext.INSTANCE, hud,
                    LegacyDrawContext.INSTANCE.guiWidth() / 2f,
                    LegacyDrawContext.INSTANCE.guiHeight() - HotbarHud.LEVEL_CENTER_Y);
            try {
                return original.call(font, text, x, y, color);
            } finally {
                HudTransform.endUpright(LegacyDrawContext.INSTANCE);
            }
        }
        if ("overlayMessage".equals(vanillahud$section)) return font.drawWithShadow(text, x, y, color);
        return original.call(font, text, x, y, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawWithShadow(Ljava/lang/String;III)I"))
    private int vanillahud$shadowText(Font font, String text, int x, int y, int color, Operation<Integer> original) {
        if (vanillahud$region != null && !vanillahud$region.shouldDraw()) return 0;
        return original.call(font, text, x, y, color);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/GameGui;itemSelectedTimer:I", ordinal = 0))
    private int vanillahud$tooltipVisible(int timer) {
        return Huds.INSTANCE.getHeldItemTooltip().getFadeOut() ? timer : Integer.MAX_VALUE;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/GameGui;itemSelectedTimer:I", ordinal = 1))
    private int vanillahud$tooltipAlpha(int timer) {
        var hud = Huds.INSTANCE.getHeldItemTooltip();
        return hud.getFadeOut() && !hud.getInstantFade() ? timer : Integer.MAX_VALUE;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/GameGui;overlayMessageTinted:Z"))
    private boolean vanillahud$rainbow(boolean tinted) {
        return tinted && Huds.INSTANCE.getActionBar().getRainbowTimer();
    }
}
//?}
