// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkinDetailBudgetTest {
    @Test void nearestPlayersStayDetailedWithinTheBudget() {
        List<SkinDetailBudget.Candidate> candidates=new ArrayList<>();for(int i=0;i<40;i++)candidates.add(new SkinDetailBudget.Candidate(new UUID(0,i),(double)i*i));
        var selected=SkinDetailBudget.choose(candidates,12,Set.of());assertEquals(12,selected.size());assertTrue(selected.contains(new UUID(0,0)));assertTrue(selected.contains(new UUID(0,11)));assertFalse(selected.contains(new UUID(0,12)));
    }
    @Test void smallDistanceChangesDoNotConstantlyReplaceSkinMeshes() {
        UUID current=new UUID(0,1),other=new UUID(0,2);
        assertEquals(Set.of(current),SkinDetailBudget.choose(List.of(new SkinDetailBudget.Candidate(current,10),new SkinDetailBudget.Candidate(other,9)),1,Set.of(current)));
        assertEquals(Set.of(other),SkinDetailBudget.choose(List.of(new SkinDetailBudget.Candidate(current,10),new SkinDetailBudget.Candidate(other,5)),1,Set.of(current)));
    }
}
