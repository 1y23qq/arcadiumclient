package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.joml.Options;
import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRootOracle073 {
    public record Result(int status,float[] root){}
    private ActorRootOracle073(){}
    public static double[] relative(State s,Frame f){
        double x=s.previousX()+(s.x()-s.previousX())*(double)f.partial()-f.cameraX();
        double y=s.previousY()+(s.y()-s.previousY())*(double)f.partial()-f.cameraY();
        double z=s.previousZ()+(s.z()-s.previousZ())*(double)f.partial()-f.cameraZ();
        if((s.flags()&Y_BIAS_ONE)!=0)y-=.125d;if((s.flags()&Y_BIAS_TWO)!=0)y-=.125d;return new double[]{x,y,z};
    }
    public static float body(State s,Frame f){float difference=s.bodyYaw()-s.previousBodyYaw();while(difference< -180)difference+=360;while(difference>=180)difference-=360;return s.previousBodyYaw()+f.partial()*difference;}
    public static float[] jomlReference(State s,Frame f){
        double[] p=relative(s,f);float b=body(s,f);Matrix4f root=new Matrix4f().identity().translate((float)p[0],(float)p[1],(float)p[2]).rotateY((float)Math.toRadians(180-b));
        if((s.flags()&UPSIDE_DOWN)!=0)root.translate(0,s.height()+.1f,0).rotateZ((float)Math.PI);
        return root.scale(-1,-1,1).scale(.9375f).translate(0,-1.5078125f,0).get(new float[16]);
    }
    public static Result prototype(State s,Frame f,boolean fp64) {
        if(!fp64)return new Result(1,null);
        if(Options.FASTMATH||Options.USE_MATH_FMA||f.mathFlags()!=0)return new Result(6,null);
        if(!Float.isFinite(f.partial())||f.partial()<0||f.partial()>1||f.partial()!=0&&Math.abs(f.partial())<Float.MIN_NORMAL)return new Result(7,null);
        if((s.flags()&~KNOWN_FLAGS)!=0||!Float.isFinite(s.height())||s.height()<=0||s.height()>4)return new Result(8,null);
        try {
            double[] p=relative(s,f);float x=ActorRootMath073.checkedFloat(p[0],0),y=ActorRootMath073.checkedFloat(p[1],0),z=ActorRootMath073.checkedFloat(p[2],0);
        if(!Float.isFinite(s.previousBodyYaw())||!Float.isFinite(s.bodyYaw())||s.previousBodyYaw()!=0&&Math.abs(s.previousBodyYaw())<Float.MIN_NORMAL||s.bodyYaw()!=0&&Math.abs(s.bodyYaw())<Float.MIN_NORMAL||Math.abs(s.bodyYaw()-s.previousBodyYaw())>1080)return new Result(3,null);
            float b=body(s,f),degrees=180f-b;if(!Float.isFinite(degrees)||Math.abs(degrees)>360)return new Result(3,null);
            float angle=ActorRootMath073.checkedFloat((double)degrees*0.017453292519943295,0);
            float sine=ActorRootMath073.sin(angle),cosine=ActorRootMath073.cosFromSin(sine,angle);
            float[] root={cosine,0,-sine,0,0,1,0,0,sine,0,cosine,0,x,y,z,1};
            if((s.flags()&UPSIDE_DOWN)!=0){translate(root,0,s.height()+.1f,0);float sz=ActorRootMath073.sin((float)Math.PI),cz=ActorRootMath073.cosFromSin(sz,(float)Math.PI);rotateZ(root,sz,cz);}
            scale(root,-1,-1,1);scale(root,.9375f,.9375f,.9375f);translate(root,0,-1.5078125f,0);
            for(float v:root)if(!Float.isFinite(v))return new Result(2,null);return new Result(0,root);
        }catch(ActorRootMath073.Decline decline){return new Result(decline.reason,null);}
    }
    private static void translate(float[] m,float x,float y,float z){for(int r=0;r<4;r++){float a=m[8+r]*z+m[12+r];float b=m[4+r]*y+a;m[12+r]=m[r]*x+b;}}
    private static void scale(float[] m,float x,float y,float z){for(int r=0;r<4;r++){m[r]*=x;m[4+r]*=y;m[8+r]*=z;}}
    private static void rotateZ(float[] m,float s,float c){for(int r=0;r<4;r++){float a=m[r]*c+m[4+r]*s;float b=m[r]*-s+m[4+r]*c;m[r]=a;m[4+r]=b;}}
}
