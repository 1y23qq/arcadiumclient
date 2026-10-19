package org.polyfrost.oneconfig.api.ui.v1.keybind;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.polyfrost.oneconfig.api.platform.v1.CompatibilityPlatform;
import org.polyfrost.oneconfig.api.platform.v1.Keys;
import org.polyfrost.oneconfig.api.platform.v1.ModInfo;
import org.polyfrost.oneconfig.api.platform.v1.Options;

public final class TestKeybindPlatform implements CompatibilityPlatform {
    private final Keys keys = (Keys) Proxy.newProxyInstance(Keys.class.getClassLoader(), new Class<?>[]{Keys.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "getKeyLeftControl" -> 224;
            case "getKeyLeftShift" -> 225;
            case "getKeyLeftAlt" -> 226;
            case "getKeyLeftSuper" -> 227;
            case "getKeyRightControl" -> 228;
            case "getKeyRightShift" -> 229;
            case "getKeyRightAlt" -> 230;
            case "getKeyRightSuper" -> 231;
            case "keyName", "mouseName" -> String.valueOf(args[0]);
            default -> 0;
        });

    @Override public void displayChatMessage(Component text) {}
    @Override public Set<ModInfo> getMods() { return Collections.emptySet(); }
    @Override public boolean isDevelopment() { return false; }
    @Override public String version() { return "test"; }
    @Override public String loader() { return "test"; }
    @Override public Options options() { throw new UnsupportedOperationException(); }
    @Override public Keys keys() { return keys; }
    @Override public long windowHandle() { return 0; }
    @Override public int fps() { return 0; }
    @Override public String resolveComponent(Component component) { return component.toString(); }
    @Override public Object wrapPlatformComponent(Object component) { return component; }
}
