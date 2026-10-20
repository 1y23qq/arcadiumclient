// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test;

import org.junit.jupiter.api.Test;
import io.github.arcadiumclient.runtime.client.features.AnimationModeBridge;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationContext;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimationModeBridgeTest {
    public enum Version { V1_7, VANILLA }
    public enum CapeMovementSetting { V1_12, V1_13 }
    public static final class Category {
        public boolean legacy = true;
        public float scale = 1f;
        public float offset = 0f;
        public Version version = Version.V1_7;
        public CapeMovementSetting capeMovement = CapeMovementSetting.V1_13;
    }
    public static final class Config {
        public final Category items = new Category();
        public final Category movement = new Category();
        public final Category screen = new Category();
        public final Category fixes = new Category();
        public final Category other = new Category();
    }

    @Test
    void modeChangesPreserveOriginalObjectsAndAllTheirValues() throws ReflectiveOperationException {
        Config config = new Config();
        Category original = config.items;
        original.scale = 1.7f;
        original.offset = -0.6f;
        AnimationModeBridge bridge = new AnimationModeBridge(config);
        bridge.apply(Map.of("items.legacy", true, "items.version", "V1_7"));
        assertNotSame(original, config.items);
        assertEquals(1.7f, original.scale);
        assertEquals(-0.6f, original.offset);
        assertEquals(1f, config.items.scale);
        assertEquals(0f, config.items.offset);
        assertFalse(config.movement.legacy);
        assertEquals(Version.VANILLA, config.movement.version);
        assertEquals(CapeMovementSetting.V1_12, config.movement.capeMovement);
        bridge.restore();
        assertSame(original, config.items);
        assertEquals(1.7f, config.items.scale);
        assertEquals(-0.6f, config.items.offset);
        assertEquals(CapeMovementSetting.V1_13, config.movement.capeMovement);
    }

    @Test
    void disabledRuntimeAndRestartKeepOverflowSettingsIndependent() throws ReflectiveOperationException {
        Config config = new Config();
        Category stored = config.items;
        AnimationModeBridge first = new AnimationModeBridge(config);
        first.apply(Map.of());
        assertFalse(config.items.legacy);
        stored.offset = 0.75f;
        first.restore();
        AnimationModeBridge second = new AnimationModeBridge(config);
        second.apply(Map.of("items.offset", -1f));
        second.restore();
        assertSame(stored, config.items);
        assertEquals(0.75f, config.items.offset);
    }

    @Test
    void individualUseAnimationsAndAlwaysSwingRemainIndependent() {
        BadlionAnimationState blocking = state(true, false, false, false, false, 100f, false, 0f, 0f);
        assertTrue(blocking.swingWhileUsing("BLOCK"));
        assertFalse(blocking.swingWhileUsing("EAT"));
        assertFalse(blocking.swingWhileUsing("DRINK"));
        assertFalse(blocking.swingWhileUsing("BOW"));
        BadlionAnimationState eating = state(false, true, false, false, false, 100f, false, 0f, 0f);
        assertTrue(eating.swingWhileUsing("EAT"));
        assertTrue(eating.swingWhileUsing("DRINK"));
        assertFalse(eating.swingWhileUsing("BLOCK"));
        BadlionAnimationState bow = state(false, false, true, false, false, 100f, false, 0f, 0f);
        assertTrue(bow.swingWhileUsing("BOW"));
        assertFalse(bow.swingWhileUsing("EAT"));
        BadlionAnimationState always = state(false, false, false, true, false, 100f, false, 0f, 0f);
        assertFalse(always.swingWhileUsing("BLOCK"));
        assertFalse(always.swingWhileUsing("EAT"));
        assertFalse(always.swingWhileUsing("BOW"));
        assertEquals(true, always.overflowValues().get("items.fakeMissPenaltySwing"));
        assertEquals(true, always.overflowValues().get("items.alwaysUsageSwing"));
    }

    @Test
    void badlionScalesReachBlockItemsAndStayFinite() {
        BadlionAnimationState scaled = new BadlionAnimationState(false, false, false, false,
                false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, 100f, 0f, 0f, 0.65f, 1.2f, 1.4f);
        Map<String, Object> values = scaled.overflowValues();
        assertEquals(true, values.get("items.applyCustomizationToBlockItems"));
        assertEquals(0.65f, values.get("items.itemScaleX"));
        assertEquals(1.2f, values.get("items.itemScaleY"));
        assertEquals(1.4f, values.get("items.itemScaleZ"));
        BadlionAnimationState corrupt = new BadlionAnimationState(false, false, false, false,
                false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, 100f, 0f, 0f, Float.NaN, -1f, 999f);
        assertEquals(1f, corrupt.overflowValues().get("items.itemScaleX"));
        assertEquals(0.1f, corrupt.overflowValues().get("items.itemScaleY"));
        assertEquals(3f, corrupt.overflowValues().get("items.itemScaleZ"));
    }

    @Test
    void cameraIntensityAndTitleOffsetsAreFiniteAndBounded() {
        assertEquals(0.35f, state(false, false, false, false, false, 35f, false, 0f, 0f).hurtCameraScale());
        assertEquals(0f, state(false, false, false, false, true, 100f, false, 0f, 0f).hurtCameraScale());
        assertEquals(1f, state(false, false, false, false, false, Float.NaN, false, 0f, 0f).hurtCameraScale());
        BadlionAnimationState moved = state(false, false, false, false, false, 100f, true, 0.1f, -0.2f);
        assertEquals(80f, moved.titleOffsetX(800));
        assertEquals(-120f, moved.titleOffsetY(600));
        assertEquals(0f, state(false, false, false, false, false, 100f, false, 0.1f, -0.2f).titleOffsetX(800));
    }

    @Test
    void previewContextIsNestedAndAlwaysCanBeCleared() {
        assertFalse(BadlionAnimationContext.inInventory());
        BadlionAnimationContext.enterInventory();
        try {
            BadlionAnimationContext.enterInventory();
            try { assertTrue(BadlionAnimationContext.inInventory()); }
            finally { BadlionAnimationContext.exitInventory(); }
            assertTrue(BadlionAnimationContext.inInventory());
        } finally { BadlionAnimationContext.exitInventory(); }
        assertFalse(BadlionAnimationContext.inInventory());
    }

    private static BadlionAnimationState state(boolean block, boolean eat, boolean bow, boolean always,
                                               boolean noShake, float intensity, boolean move, float x, float y) {
        return new BadlionAnimationState(false, false, false, false, block, false, false, false, eat,
                false, false, false, bow, always, false, false, false, false, noShake, false, move, intensity, x, y, 1f, 1f, 1f);
    }
}
