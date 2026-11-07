package io.github.arcadiumclient.runtime.qa;

public final class VoxelPhysicalProjection073 {
    public static final int LIMIT=32;
    private VoxelPhysicalProjection073(){}
    public static void compose(float[] projection,float[] physicalEye,float[] target,int offset){
        require(projection.length==16&&physicalEye.length==16&&target!=projection&&target!=physicalEye&&offset>=0&&offset<=target.length-16,"Unknown bounded physical projection storage");finite(projection);finite(physicalEye);
        for(int c=0;c<4;c++)for(int r=0;r<4;r++){float a=projection[r]*physicalEye[c*4],b=projection[4+r]*physicalEye[c*4+1],d=projection[8+r]*physicalEye[c*4+2],e=projection[12+r]*physicalEye[c*4+3];float value=(a+b)+(d+e);require(Float.isFinite(value),"Nonfinite composed projection");target[offset+c*4+r]=value;}
    }
    public static float[] clip(float[] matrix,int offset,float x,float y,float z){require(offset>=0&&offset<=matrix.length-16,"Unknown projection row");float[] result=new float[4];for(int r=0;r<4;r++){float value=0;value=Math.fma(matrix[offset+r],x,value);value=Math.fma(matrix[offset+4+r],y,value);value=Math.fma(matrix[offset+8+r],z,value);value=Math.fma(matrix[offset+12+r],1,value);require(Float.isFinite(value),"Nonfinite projected clip");result[r]=value;}return result;}
    public static final class Table {
        private final float[] words=new float[LIMIT*16];private int size;
        public int add(float[] projection,float[] physicalEye){require(size<LIMIT,"Projection table budget exceeded");compose(projection,physicalEye,words,size*16);return size++;}
        public void reset(){size=0;}
        public int size(){return size;}
        public float[] copy(){return java.util.Arrays.copyOf(words,size*16);}
    }
    private static void finite(float[] values){for(float value:values)require(Float.isFinite(value),"Nonfinite physical matrix");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
