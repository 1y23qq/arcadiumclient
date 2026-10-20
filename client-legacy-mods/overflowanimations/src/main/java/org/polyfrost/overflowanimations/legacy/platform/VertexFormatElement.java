package org.polyfrost.overflowanimations.legacy.platform;

//? if = 1.7.10 {
public record VertexFormatElement(int index, Type type, Usage usage, int count) {
    public enum Type { FLOAT }
    public enum Usage { POSITION, UV, COLOR, NORMAL }
}
//?}
