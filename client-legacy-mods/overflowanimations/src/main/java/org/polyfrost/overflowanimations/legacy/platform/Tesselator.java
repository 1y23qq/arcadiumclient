package org.polyfrost.overflowanimations.legacy.platform;

//? if = 1.7.10 {
public final class Tesselator {
    private static final Tesselator INSTANCE = new Tesselator();
    private final BufferBuilder buffer = new BufferBuilder(net.minecraft.client.render.vertex.LegacyTesselatorBackend.INSTANCE);
    private Tesselator() { }
    public static Tesselator getInstance() { return INSTANCE; }
    public BufferBuilder getBuffer() { return buffer; }
    public void end() { net.minecraft.client.render.vertex.LegacyTesselatorBackend.INSTANCE.end(); }
}
//?}
