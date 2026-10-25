// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.util.LinkedHashMap;
import java.util.Map;

public record BadlionAnimationState(
        boolean itemHeld, boolean itemSwitch, boolean hitBox, boolean inventory,
        boolean blockHit, boolean heartAnimation, boolean damageAnimation, boolean sneakingAnimation,
        boolean eatAnimation, boolean thrownItems, boolean fishingRod, boolean swordSwing,
        boolean bowAnimation, boolean alwaysSwing, boolean removeTitles, boolean oldEnchantGlint,
        boolean leftHanded, boolean noSmallArms, boolean noShake, boolean fixLiquidPlace,
        boolean movableTitles, float shakeIntensity, float titleX, float titleY,
        float itemScaleX, float itemScaleY, float itemScaleZ) {

    public boolean swingWhileUsing(String action) {
        return switch (action) {
            case "BLOCK" -> blockHit;
            case "EAT", "DRINK" -> eatAnimation;
            case "BOW" -> bowAnimation;
            default -> false;
        };
    }

    public float hurtCameraScale() {
        return noShake ? 0f : Float.isFinite(shakeIntensity) ? Math.max(0f, Math.min(100f, shakeIntensity)) / 100f : 1f;
    }

    public float titleOffsetX(int width) {
        return movableTitles && Float.isFinite(titleX) ? Math.max(-0.45f, Math.min(0.45f, titleX)) * width : 0f;
    }

    public float titleOffsetY(int height) {
        return movableTitles && Float.isFinite(titleY) ? Math.max(-0.45f, Math.min(0.45f, titleY)) * height : 0f;
    }

    public Map<String, Object> overflowValues() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items.itemScaleX", itemScale(itemScaleX));
        out.put("items.itemScaleY", itemScale(itemScaleY));
        out.put("items.itemScaleZ", itemScale(itemScaleZ));
        out.put("items.applyCustomizationToBlockItems", true);
        out.put("items.firstPersonItemPositions", itemHeld ? "V1_7" : "V1_8");
        out.put("items.thirdPersonItemPositions", itemHeld ? "V1_7" : "V1_8");
        out.put("items.equipAnimationVersion", itemSwitch ? "V1_7" : "VANILLA");
        out.put("items.punchingDuringUsage", blockHit || eatAnimation || bowAnimation || alwaysSwing ? "V1_7" : "V1_8");
        out.put("items.alwaysUsageSwing", alwaysSwing);
        out.put("items.fakeMissPenaltySwing", alwaysSwing);
        out.put("items.droppedItems2D", thrownItems ? "FLAT" : "MODEL");
        out.put("items.itemDropsFaceCamera", thrownItems);
        out.put("items.fishingRodVersion", fishingRod ? "V1_7" : "VANILLA");
        out.put("items.itemGlint", oldEnchantGlint ? "V1_7" : "VANILLA");
        out.put("items.armorGlint", oldEnchantGlint ? "V1_8" : "VANILLA");
        out.put("items.potionGlint", oldEnchantGlint ? "V1_7" : "VANILLA");
        out.put("screen.healthBarFlashing", heartAnimation ? "V1_7" : "V1_8");
        out.put("movement.sneakAnimation", sneakingAnimation ? "V1_7" : "VANILLA");
        out.put("movement.longerUnsneak", sneakingAnimation ? "LONG" : "MATCH_SNEAK_ANIMATION");
        out.put("movement.bowArmMovement", bowAnimation);
        out.put("other.hitColorArmor", damageAnimation ? "RED" : "UNCHANGED");
        out.put("other.damageTintHeldItems", damageAnimation ? "BOTH" : "OFF");
        out.put("other.damageTintCape", damageAnimation);
        out.put("other.damageTintStyle", damageAnimation ? "V1_7" : "VANILLA");
        out.put("other.thirdPersonBlockAnimation", blockHit ? "V1_7" : "V1_8");
        out.put("other.lockBlockingArmRotation", blockHit);
        return out;
    }

    private static float itemScale(float value) {
        return Float.isFinite(value) ? Math.max(0.1f, Math.min(3f, value)) : 1f;
    }
}
