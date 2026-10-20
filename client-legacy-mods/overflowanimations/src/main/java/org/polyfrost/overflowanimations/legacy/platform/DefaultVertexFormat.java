package org.polyfrost.overflowanimations.legacy.platform;

//? if = 1.7.10 {
public final class DefaultVertexFormat {
    public static final VertexFormat POSITION_COLOR = new VertexFormat();
    public static final VertexFormat POSITION_TEX = new VertexFormat();
    public static final VertexFormatElement POSITION_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    public static final VertexFormatElement UV0_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement COLOR_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.COLOR, 4);
    public static final VertexFormatElement UV1_ELEMENT = new VertexFormatElement(1, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    private DefaultVertexFormat() { }
}
//?}
