// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

public final class TerrainTrackingInteriorSurvey073 {
    public record Survey(int trackingX, int trackingZ, int targetTickX, int targetTickZ, double x, double z) { }
    private TerrainTrackingInteriorSurvey073() { }
    public static int trackingChunk(double position) { bounded(position); return ((int) position) >> 4; }
    public static int tickingChunk(double position) { bounded(position); return (int) Math.floor(position / 16); }
    public static Survey forTarget(double x, double z) {
        int tx = trackingChunk(x), tz = trackingChunk(z), fx = tickingChunk(x), fz = tickingChunk(z);
        if (tx == fx && tz == fz) return null;
        double ix = tx * 16.0 + 8, iz = tz * 16.0 + 8;
        if (trackingChunk(ix) != tx || trackingChunk(iz) != tz || tickingChunk(ix) != tx || tickingChunk(iz) != tz) throw new IllegalStateException("Owned survey does not align both native centers");
        return new Survey(tx, tz, fx, fz, ix, iz);
    }
    private static void bounded(double position) { if (!Double.isFinite(position) || Math.abs(position) > 4096) throw new IllegalArgumentException("Survey is outside the owned bounded terrain fixture"); }
}
