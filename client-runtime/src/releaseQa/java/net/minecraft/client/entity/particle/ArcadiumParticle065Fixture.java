// SPDX-License-Identifier: GPL-3.0-only
package net.minecraft.client.entity.particle;

import io.github.arcadiumclient.runtime.qa.ParticleNative065Reference;

public final class ArcadiumParticle065Fixture extends ParticleNative065Reference.NativeParticle {
    public ArcadiumParticle065Fixture() { super(); }
    public static void copyAppearance(Particle source, Particle target) {
        target.size=source.size;target.spriteRow=source.spriteRow;target.spriteColumn=source.spriteColumn;target.sprite=source.sprite;
        target.red=source.red;target.green=source.green;target.blue=source.blue;target.alpha=source.alpha;
    }
    public static float[] colors(Particle particle) { return new float[]{particle.red,particle.green,particle.blue,particle.alpha}; }
}
