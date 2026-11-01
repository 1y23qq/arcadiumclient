// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.entity.Entity;

public final class ParticleNative065Reference {
    public record Sample(String scenario, int[] packet, int[] original) { }
    private static final float[] DELTAS = {0, .001f, .125f, .5f, .875f, .999f, 1};
    private static final float[] COLORS = {0, .003f, .125f, .5f, .996f, 1, 1.5f, -.25f};
    private static final int[] LIGHTS = {0, 0x00F000F0, 0x00E00020, 0x1234ABCD, 0xFFFF8000};

    private ParticleNative065Reference() { }

    public static List<Sample> samples(int count) {
        double oldX = Particle.cameraX, oldY = Particle.cameraY, oldZ = Particle.cameraZ;
        List<Sample> samples = new ArrayList<>(count);
        Random random = new Random(0x650B17A5L);
        try {
            for (int i = 0; i < count; i++) {
                NativeParticle particle = new NativeParticle();
                double origin = i % 17 == 0 ? 29_999_900 : i % 19 == 0 ? -29_999_900 : 1000;
                Particle.cameraX = origin + random.nextDouble() * 5;
                Particle.cameraY = 70 + random.nextDouble() * 5;
                Particle.cameraZ = origin - random.nextDouble() * 5;
                particle.configure(i, random);
                float delta = DELTAS[i % DELTAS.length];
                float yaw = (float) (random.nextDouble() * Math.PI * 2);
                float pitch = (float) ((random.nextDouble() - .5) * Math.PI);
                float dx = (float) Math.cos(yaw), dy = (float) Math.cos(pitch), dz = (float) Math.sin(yaw);
                float forwards = -dz * (float) Math.sin(pitch), sideways = dx * (float) Math.sin(pitch);
                BufferBuilder buffer = new BufferBuilder(128);
                buffer.begin(7, DefaultVertexFormat.PARTICLE);
                particle.render(buffer, null, delta, dx, dy, dz, forwards, sideways);
                buffer.end();
                int[] original = words(buffer);
                require(original.length == 28 && buffer.getVertexCount() == 4, "Original particle did not emit one quad");
                int[] packet = particle.packet(delta, dx, dy, dz, forwards, sideways, original[5]);
                samples.add(new Sample((i % 2 == 0 ? "grid" : "sprite") + "/frame-" + (i % 11) + "/delta-" + delta + "/case-" + i,
                        packet, original));
            }
        } finally {
            Particle.cameraX = oldX;
            Particle.cameraY = oldY;
            Particle.cameraZ = oldZ;
        }
        return samples;
    }

    public static int[] words(BufferBuilder buffer) {
        var source = buffer.getBuffer().duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        int[] words = new int[buffer.getVertexCount() * buffer.getFormat().getIntSize()];
        source.position(0).limit(words.length);
        source.get(words);
        return words;
    }

    public static int verifyMath(List<Sample> samples) throws ReflectiveOperationException {
        Method quads = Class.forName("io.github.arcadiumclient.runtime.client.optimizations.ParticlePacketMath")
                .getMethod("quads", int[].class, int.class);
        for (Sample sample : samples) {
            int[] padded = new int[24];
            Arrays.fill(padded, 0x6A72BC19);
            System.arraycopy(sample.packet, 0, padded, 3, 16);
            int[] before = padded.clone();
            int[] actual = (int[]) quads.invoke(null, padded, 3);
            require(Arrays.equals(before, padded), "Packet math changed its input: " + sample.scenario);
            compare(sample.original, actual, sample.scenario);
        }
        return samples.size();
    }

    public static void compare(int[] expected, int[] actual, String scenario) {
        require(expected.length == actual.length, scenario + ": vertex data length differs");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) throw new IllegalStateException(scenario + ": native word " + i
                    + " expected=0x" + Integer.toHexString(expected[i]) + " actual=0x" + Integer.toHexString(actual[i])
                    + " expectedFloat=" + Float.intBitsToFloat(expected[i]) + " actualFloat=" + Float.intBitsToFloat(actual[i]));
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void main(String[] args) throws Exception {
        int cases = args.length == 0 ? 2048 : Integer.parseInt(args[0]);
        long started = System.nanoTime();
        int checked = verifyMath(samples(cases));
        System.out.printf(java.util.Locale.ROOT, "{\"passed\":true,\"nativeParticleCases\":%d,\"nativeVertices\":%d,\"nativeWords\":%d,\"seconds\":%.6f}%n",
                checked, checked * 4, checked * 28, (System.nanoTime() - started) / 1_000_000_000d);
    }

    public static class NativeParticle extends Particle {
        private int packedLight;
        private boolean animated;

        public NativeParticle() { super(null, 0, 0, 0); }
        public void stableAppearance() { animated=false; }

        public void configure(int index, Random random) {
            lastX = Particle.cameraX + random.nextDouble() * 30 - 15;
            lastY = Particle.cameraY + random.nextDouble() * 30 - 15;
            lastZ = Particle.cameraZ + random.nextDouble() * 30 - 15;
            x = lastX + random.nextDouble() * 5 - 2.5;
            y = lastY + random.nextDouble() * 5 - 2.5;
            z = lastZ + random.nextDouble() * 5 - 2.5;
            size = index % 23 == 0 ? .0001f : .05f + random.nextFloat() * 12;
            spriteRow = index % 16;
            spriteColumn = index / 16 % 16;
            sprite = index % 2 == 0 ? null : new NativeSprite(index % 11);
            red = COLORS[index % COLORS.length];
            green = COLORS[(index + 3) % COLORS.length];
            blue = COLORS[(index + 5) % COLORS.length];
            alpha = COLORS[(index + 1) % COLORS.length];
            packedLight = LIGHTS[index % LIGHTS.length];
            animated = index % 3 == 0;
        }

        @Override public int getLightLevel(float delta) { return packedLight; }

        @Override public void render(BufferBuilder buffer, Entity camera, float delta, float dx, float dy, float dz, float forwards, float sideways) {
            if (animated) {
                size *= .2f + delta * 1.3f;
                alpha *= 1 - delta * .75f;
            }
            super.render(buffer, camera, delta, dx, dy, dz, forwards, sideways);
        }

        int[] packet(float delta, float dx, float dy, float dz, float forwards, float sideways, int packedColor) {
            float u0 = sprite == null ? spriteRow / 16f : sprite.getUMin();
            float u1 = sprite == null ? u0 + .0624375f : sprite.getUMax();
            float v0 = sprite == null ? spriteColumn / 16f : sprite.getVMin();
            float v1 = sprite == null ? v0 + .0624375f : sprite.getVMax();
            return new int[]{bits((float) (lastX + (x - lastX) * delta - cameraX)),
                    bits((float) (lastY + (y - lastY) * delta - cameraY)),
                    bits((float) (lastZ + (z - lastZ) * delta - cameraZ)), bits(.1f * size),
                    bits(u0), bits(u1), bits(v0), bits(v1), packedColor, packedLight,
                    bits(dx), bits(dy), bits(dz), bits(forwards), bits(sideways), 0};
        }

        private static int bits(float value) { return Float.floatToRawIntBits(value); }
    }

    private static final class NativeSprite extends TextureAtlasSprite {
        private final int frame;
        NativeSprite(int frame) { super("arcadium:qa_particle_" + frame); this.frame = frame; }
        @Override public float getUMin() { return frame * .023f; }
        @Override public float getUMax() { return getUMin() + .03121875f; }
        @Override public float getVMin() { return .7f - frame * .017f; }
        @Override public float getVMax() { return getVMin() + .04678125f; }
    }
}
