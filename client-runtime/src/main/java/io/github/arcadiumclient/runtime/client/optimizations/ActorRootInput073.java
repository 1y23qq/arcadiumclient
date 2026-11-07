package io.github.arcadiumclient.runtime.client.optimizations;
import java.nio.*;
public final class ActorRootInput073 {
    public static final int RECORD_BYTES=80,FRAME_BYTES=48,UPSIDE_DOWN=1,SLIM=2,Y_BIAS_ONE=4,Y_BIAS_TWO=8,KNOWN_FLAGS=15;
    public record State(double previousX,double previousY,double previousZ,double x,double y,double z,
                        float previousBodyYaw,float bodyYaw,float height,int flags) {}
    public record Frame(double cameraX,double cameraY,double cameraZ,float partial,int activeCount,long frameId,int mathFlags) {}
    private static final State ZERO=new State(0,0,0,0,0,0,0,0,0,0);
    public static void encode(ByteBuffer target,int generation,int lifetime,boolean alive,State s) {
        target.order(ByteOrder.LITTLE_ENDIAN).putInt(generation).putInt(lifetime).putInt(alive?1:0).putInt(0);
        if(s==null)s=ZERO;
        target.putDouble(s.previousX).putDouble(s.previousY).putDouble(s.previousZ).putDouble(s.x).putDouble(s.y).putDouble(s.z)
                .putFloat(s.previousBodyYaw).putFloat(s.bodyYaw).putFloat(s.height).putInt(s.flags);
    }
    public static void encodeFrame(ByteBuffer target,Frame f,int capacity){if(capacity<1||capacity>2048)throw new IllegalArgumentException("Frame capacity");target.order(ByteOrder.LITTLE_ENDIAN).putDouble(f.cameraX).putDouble(f.cameraY).putDouble(f.cameraZ).putFloat(f.partial).putInt(f.activeCount).putLong(f.frameId).putInt(f.mathFlags).putInt(capacity);}
    static boolean rawEqual(State a,State b){return a!=null&&Double.doubleToRawLongBits(a.previousX)==Double.doubleToRawLongBits(b.previousX)&&Double.doubleToRawLongBits(a.previousY)==Double.doubleToRawLongBits(b.previousY)&&Double.doubleToRawLongBits(a.previousZ)==Double.doubleToRawLongBits(b.previousZ)&&Double.doubleToRawLongBits(a.x)==Double.doubleToRawLongBits(b.x)&&Double.doubleToRawLongBits(a.y)==Double.doubleToRawLongBits(b.y)&&Double.doubleToRawLongBits(a.z)==Double.doubleToRawLongBits(b.z)&&Float.floatToRawIntBits(a.previousBodyYaw)==Float.floatToRawIntBits(b.previousBodyYaw)&&Float.floatToRawIntBits(a.bodyYaw)==Float.floatToRawIntBits(b.bodyYaw)&&Float.floatToRawIntBits(a.height)==Float.floatToRawIntBits(b.height)&&a.flags==b.flags;}
}
