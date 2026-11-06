// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ModernArmorProjectiveMatrices {
    private final ModernGlintMatrices arena;
    ModernArmorProjectiveMatrices(ModernGlintMatrices arena){this.arena=arena;}
    int remaining(){return arena.remaining();}
    int intern(float[] matrix){return arena.internProjective(matrix);}
}
