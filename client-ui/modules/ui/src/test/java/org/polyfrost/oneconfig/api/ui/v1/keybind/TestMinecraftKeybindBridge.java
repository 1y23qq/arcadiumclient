package org.polyfrost.oneconfig.api.ui.v1.keybind;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TestMinecraftKeybindBridge implements MinecraftKeybindBridge {
    record Mapping(int[] keys, int[] mouse, byte modifiers) {}
    private static final Map<OneConfigKeybind, Mapping> MAPPINGS = new IdentityHashMap<>();
    private static final Map<OneConfigKeybind, Integer> SYNCS = new IdentityHashMap<>();
    public void register(OneConfigKeybind bind) { MAPPINGS.put(bind, snapshot(bind)); }
    public void unregister(OneConfigKeybind bind) { MAPPINGS.remove(bind); SYNCS.remove(bind); }
    public void sync(OneConfigKeybind bind) {
        if (!MAPPINGS.containsKey(bind)) return;
        MAPPINGS.put(bind, snapshot(bind));
        SYNCS.merge(bind, 1, Integer::sum);
    }
    static Mapping mapping(OneConfigKeybind bind) { return MAPPINGS.get(bind); }
    static int syncs(OneConfigKeybind bind) { return SYNCS.getOrDefault(bind, 0); }
    private static Mapping snapshot(OneConfigKeybind bind) {
        return new Mapping(copy(bind.getKeyCodes()), copy(bind.getMouseBtns()), bind.getMods());
    }
    private static int[] copy(int[] value) { return value == null ? null : value.clone(); }
}
