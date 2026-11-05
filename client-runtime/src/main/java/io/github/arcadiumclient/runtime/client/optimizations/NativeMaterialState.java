// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class NativeMaterialState {
    private Object model, texture;
    private boolean known, nativeRequired = true;
    private long generation = Long.MIN_VALUE;

    public void clear() { model = texture = null; known = false; nativeRequired = true; generation = Long.MIN_VALUE; }

    public boolean requires(Object model, Object texture, boolean base, boolean glint, boolean required, long currentGeneration) {
        if (generation != currentGeneration) clear();
        if (base) {
            this.model = model; this.texture = texture; generation = currentGeneration;
            known = model != null && texture != null; nativeRequired = !known || required;
        } else if (!glint || !known || this.model != model) return true;
        return nativeRequired;
    }
}
