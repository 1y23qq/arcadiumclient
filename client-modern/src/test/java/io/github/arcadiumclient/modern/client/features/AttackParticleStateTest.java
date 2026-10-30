package io.github.arcadiumclient.modern.client.features;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AttackParticleStateTest {
    @Test void onlyTheSameSweepingAttackSuppressesExtraCriticalParticles() {
        var state = new AttackParticleState();
        Object player = new Object(), target = new Object(), level = new Object();
        state.begin(player, target, level, true, 100);
        assertTrue(state.suppress(player, target, level, 200));
        assertFalse(state.suppress(new Object(), target, level, 200));
        assertFalse(state.suppress(player, new Object(), level, 200));
        assertFalse(state.suppress(player, target, new Object(), 200));
        assertFalse(state.suppress(player, target, level, 99));
        assertFalse(state.suppress(player, target, level, 2_000_000_101L));
    }

    @Test void actualDecisionOverridesPredictionWithoutReplacingAnotherAttack() {
        var state = new AttackParticleState();
        Object player = new Object(), target = new Object(), level = new Object();
        state.begin(player, target, level, true, 0);
        state.resolve(player, new Object(), level, false);
        assertTrue(state.suppress(player, target, level, 1));
        state.resolve(player, target, level, false);
        assertFalse(state.suppress(player, target, level, 1));
        state.begin(player, target, level, false, 2);
        assertFalse(state.suppress(player, target, level, 3));
        state.resolve(player, target, level, true);
        assertTrue(state.suppress(player, target, level, 4));
    }
}
