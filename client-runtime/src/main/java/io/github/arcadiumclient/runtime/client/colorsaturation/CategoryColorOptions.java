// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.colorsaturation;

public interface CategoryColorOptions {
    boolean arcadium$colorEnabled();
    boolean arcadium$separateEntityColors();
    void arcadium$readColors(CategoryColorProfile world, CategoryColorProfile entities);
}
