// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
public interface ParticleRecipeAccess {
    Object arcadium$recipe();
    long arcadium$recipeEpoch();
    void arcadium$recipe(Object value,long epoch);
}
