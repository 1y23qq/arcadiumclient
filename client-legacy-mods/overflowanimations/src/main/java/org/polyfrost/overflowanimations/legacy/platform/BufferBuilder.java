package org.polyfrost.overflowanimations.legacy.platform;

//? if = 1.7.10 {
public final class BufferBuilder {
    private final net.minecraft.client.render.vertex.LegacyTesselatorBackend tessellator;
    private double x, y, z;
    BufferBuilder(net.minecraft.client.render.vertex.LegacyTesselatorBackend tessellator) { this.tessellator = tessellator; }
    public void begin(int mode, VertexFormat format) { tessellator.begin(mode); }
    public BufferBuilder vertex(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
    public BufferBuilder texture(double u, double v) { tessellator.texture(u, v); return this; }
    public BufferBuilder texture(int u, int v) { tessellator.brightness((u << 16) | (v & 65535)); return this; }
    public BufferBuilder color(int r, int g, int b, int a) { tessellator.color(r, g, b, a); return this; }
    public BufferBuilder color(float r, float g, float b, float a) { tessellator.color(r, g, b, a); return this; }
    public BufferBuilder normal(float x, float y, float z) { tessellator.normal(x, y, z); return this; }
    public void nextVertex() { tessellator.vertex(x, y, z); }
}
//?}
