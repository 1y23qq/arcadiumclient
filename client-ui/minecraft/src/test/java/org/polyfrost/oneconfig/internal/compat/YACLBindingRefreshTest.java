// SPDX-License-Identifier: GPL-3.0-only
//? yacl_compat {
package org.polyfrost.oneconfig.internal.compat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.platform.v1.ModInfo;
import static org.junit.jupiter.api.Assertions.*;

class YACLBindingRefreshTest {
    @Test void externalImportsReplaceOnlyTheSelectedModsPendingValuesWithoutApplyingOrSaving() throws Exception {
        FakeOption selected = new FakeOption(), other = new FakeOption();
        selected.binding.value = selected.pending = false;
        other.binding.value = other.pending = false;
        FakeYaclA first = new FakeYaclA(selected);
        FakeYaclB second = new FakeYaclB(other);
        Tree firstTree = parse(first), secondTree = parse(second);
        selected.requestSet(false);
        other.requestSet(false);
        selected.binding.value = true;
        other.binding.value = true;

        assertEquals(0, YACLCompat.refreshFromBindings("not-registered"));
        assertEquals(1, YACLCompat.refreshFromBindings(firstTree.getID()));
        assertTrue(selected.pendingValue());
        assertFalse(other.pendingValue());
        assertEquals(0, selected.applies);
        assertEquals(0, first.saves);
        assertTrue(selected.binding.value);
        assertEquals(1, YACLCompat.refreshFromBindings(secondTree.getID()));
        assertTrue(other.pendingValue());
        assertEquals(0, other.applies);
        assertEquals(0, second.saves);
    }

    @Test void reparsingTheSameModRefreshesOnlyItsCurrentOptions() throws Exception {
        FakeOption stale = new FakeOption(), current = new FakeOption();
        parse(new FakeYaclA(stale));
        Tree registered = parse(new FakeYaclA(current));
        stale.requestSet(false);
        current.requestSet(false);
        assertEquals(1, YACLCompat.refreshFromBindings(registered.getID()));
        assertFalse(stale.pendingValue());
        assertTrue(current.pendingValue());
        assertEquals(0, current.applies);
    }

    private static Tree parse(Object yacl) throws Exception {
        Method parse = YACLCompat.class.getDeclaredMethod("parseYACLInstance", Object.class, ModInfo.class);
        parse.setAccessible(true);
        return (Tree) parse.invoke(YACLCompat.INSTANCE, yacl, null);
    }

    public static class FakeYacl {
        private final FakeOption option;
        int saves;
        FakeYacl(FakeOption option) { this.option = option; }
        public List<FakeCategory> categories() { return List.of(new FakeCategory(option)); }
        public Runnable saveFunction() { return () -> saves++; }
    }
    public static final class FakeYaclA extends FakeYacl { FakeYaclA(FakeOption option) { super(option); } }
    public static final class FakeYaclB extends FakeYacl { FakeYaclB(FakeOption option) { super(option); } }
    public record FakeCategory(FakeOption option) {
        public String name() { return "General"; }
        public List<FakeGroup> groups() { return List.of(new FakeGroup(option)); }
    }
    public record FakeGroup(FakeOption option) {
        public String name() { return "Options"; }
        public boolean isRoot() { return true; }
        public List<FakeOption> options() { return List.of(option); }
    }
    public static final class FakeBinding {
        boolean value = true;
        public boolean defaultValue() { return true; }
        public boolean getValue() { return value; }
        public void setValue(boolean value) { this.value = value; }
    }
    public static final class FakeOption {
        final FakeBinding binding = new FakeBinding();
        boolean pending = true;
        int applies;
        public String name() { return "Enabled"; }
        public FakeBinding binding() { return binding; }
        public boolean pendingValue() { return pending; }
        public void requestSet(boolean value) { pending = value; }
        public boolean applyValue() { applies++; binding.value = pending; return true; }
        public void forgetPendingValue() { pending = binding.value; }
        public boolean available() { return true; }
    }
}
//? }
