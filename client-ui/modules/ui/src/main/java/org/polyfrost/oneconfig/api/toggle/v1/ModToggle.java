// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.toggle.v1;

public interface ModToggle {
    String ENTRYPOINT = "oneconfig:toggle";

    boolean isEnabled();
    void setEnabled(boolean enabled);
    default boolean needsSetup() { return false; }
}
