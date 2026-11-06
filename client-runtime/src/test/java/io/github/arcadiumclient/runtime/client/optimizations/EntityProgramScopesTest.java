// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EntityProgramScopesTest {
    private static EntityProgramScopes.Adapter adapter(String name, List<String> events, int token) {
        return new EntityProgramScopes.Adapter() {
            public int begin(int program) { events.add(name + "+" + program); return token; }
            public void end(int value) { events.add(name + "-" + value); }
        };
    }

    @Test void nestedProgramsRestoreInStrictReverseOrder() {
        List<String> events = new ArrayList<>();
        var scopes = new EntityProgramScopes.Stack(adapter("body", events, 1), adapter("modern", events, 2));
        long first = scopes.begin(10), second = scopes.begin(20);
        assertThrows(IllegalStateException.class, () -> scopes.end(first, 10));
        assertThrows(IllegalStateException.class, () -> scopes.end(second, 10));
        assertEquals(4, events.size());
        scopes.end(second, 20); scopes.end(first, 10);
        assertEquals(List.of("body+10", "modern+10", "body+20", "modern+20", "modern-2", "body-1", "modern-2", "body-1"), events);
        assertThrows(IllegalStateException.class, () -> scopes.end(first, 10));
    }

    @Test void secondAdapterFailureUnwindsFirstWithoutPublishingScope() {
        List<String> events = new ArrayList<>();
        var scopes = new EntityProgramScopes.Stack(adapter("body", events, 3), new EntityProgramScopes.Adapter() {
            public int begin(int program) { throw new IllegalArgumentException("begin failed"); }
            public void end(int token) { fail("No modern scope was published"); }
        });
        assertThrows(IllegalArgumentException.class, () -> scopes.begin(4));
        assertEquals(List.of("body+4", "body-3"), events);
    }

    @Test void failingRestorationStillRestoresTheOtherAdapterOnce() {
        List<String> events = new ArrayList<>();
        var scopes = new EntityProgramScopes.Stack(adapter("body", events, 5), new EntityProgramScopes.Adapter() {
            public int begin(int program) { return 6; }
            public void end(int token) { events.add("modern failed"); throw new IllegalArgumentException("end failed"); }
        });
        long token = scopes.begin(7);
        assertThrows(IllegalArgumentException.class, () -> scopes.end(token, 7));
        assertEquals(List.of("body+7", "modern failed", "body-5"), events);
        assertThrows(IllegalStateException.class, () -> scopes.end(token, 7));
    }

    @Test void boundedNestingAndStaleTokensCannotModifyActiveState() {
        List<String> events = new ArrayList<>();
        var scopes = new EntityProgramScopes.Stack(adapter("body", events, 1), adapter("modern", events, 2));
        long[] tokens = new long[16];
        for (int i = 0; i < tokens.length; i++) tokens[i] = scopes.begin(i);
        assertThrows(IllegalStateException.class, () -> scopes.begin(16));
        assertEquals(32, events.size());
        for (int i = 15; i >= 0; i--) scopes.end(tokens[i], i);
        long fresh = scopes.begin(0);
        assertNotEquals(tokens[0], fresh);
        assertThrows(IllegalStateException.class, () -> scopes.end(tokens[0], 0));
        scopes.end(fresh, 0); scopes.end(0, 0);
    }

    @Test void unsupportedProgramsDoNotConsumeScopeCapacityOrRestoreUnownedState() {
        int[] begins = {0}, ends = {0};
        var unused = new EntityProgramScopes.Adapter() {
            public int begin(int program) { begins[0]++; return 0; }
            public void end(int token) { ends[0]++; }
        };
        var scopes = new EntityProgramScopes.Stack(unused, unused);
        for (int i = 0; i < 1024; i++) { assertEquals(0, scopes.begin(i)); scopes.end(0, i); }
        assertEquals(2048, begins[0]); assertEquals(0, ends[0]);
    }

    @Test void reentrantAdapterChangesAreRejectedAndTheOuterScopeCanStillRestore() {
        var holder = new EntityProgramScopes.Stack[1];
        int[] ended = {0};
        var body = new EntityProgramScopes.Adapter() {
            public int begin(int program) { assertThrows(IllegalStateException.class, () -> holder[0].begin(program)); return 1; }
            public void end(int token) { assertThrows(IllegalStateException.class, () -> holder[0].begin(3)); ended[0]++; }
        };
        holder[0] = new EntityProgramScopes.Stack(body, adapter("modern", new ArrayList<>(), 2));
        long first = holder[0].begin(5); holder[0].end(first, 5);
        long second = holder[0].begin(6); holder[0].end(second, 6);
        assertEquals(2, ended[0]);
    }
}
