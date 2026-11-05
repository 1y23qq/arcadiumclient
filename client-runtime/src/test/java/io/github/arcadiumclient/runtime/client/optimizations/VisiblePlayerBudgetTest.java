// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisiblePlayerBudgetTest {
    @Test void anEmptySecondPassDoesNotEraseTheFirstPassPlayers() {
        VisiblePlayerBudget budget = new VisiblePlayerBudget();
        Object one=new Object(), two=new Object();
        budget.beginFrame();budget.observe(one);budget.observe(two);
        budget.beginFrame();
        assertEquals(2,budget.previousFrame());
        budget.observe(one);budget.observe(two);budget.observe(one);
        assertEquals(2,budget.previousFrame());
        budget.beginFrame();assertEquals(2,budget.previousFrame());
    }

    @Test void repeatedPassesAndArmorDoNotInflateTheVisiblePlayerCount() {
        VisiblePlayerBudget budget = new VisiblePlayerBudget();Object player=new Object();
        for(int pass=0;pass<4;pass++)for(int layer=0;layer<6;layer++)budget.observe(player);
        budget.beginFrame();assertEquals(1,budget.previousFrame());
    }

    @Test void aFrameWithoutPlayersAndAWorldResetReleaseTheBudget() {
        VisiblePlayerBudget budget = new VisiblePlayerBudget();budget.observe(new Object());
        budget.beginFrame();assertEquals(1,budget.previousFrame());
        budget.beginFrame();assertEquals(0,budget.previousFrame());
        budget.observe(new Object());budget.clear();budget.beginFrame();assertEquals(0,budget.previousFrame());
    }
}
