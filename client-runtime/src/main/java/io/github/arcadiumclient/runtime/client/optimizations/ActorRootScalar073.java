package io.github.arcadiumclient.runtime.client.optimizations;

import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRootScalar073 {
    public static long checks,sineCalls,sqrtCalls,refusals;
    private ActorRootScalar073(){}
    public static int admit(State s,Frame f){
        checks++;
        if(f.mathFlags()!=0)return refuse(6);
        if(!Float.isFinite(f.partial())||f.partial()<0||f.partial()>1||f.partial()!=0&&Math.abs(f.partial())<Float.MIN_NORMAL)return refuse(7);
        if((s.flags()&~15)!=0||!Float.isFinite(s.height())||s.height()<=0||s.height()>4)return refuse(8);
        if(!Double.isFinite(s.previousX())||!Double.isFinite(s.previousY())||!Double.isFinite(s.previousZ())||!Double.isFinite(s.x())||!Double.isFinite(s.y())||!Double.isFinite(s.z())||!Double.isFinite(f.cameraX())||!Double.isFinite(f.cameraY())||!Double.isFinite(f.cameraZ()))return refuse(2);
        double x=s.previousX()+(s.x()-s.previousX())*(double)f.partial()-f.cameraX(),y=s.previousY()+(s.y()-s.previousY())*(double)f.partial()-f.cameraY(),z=s.previousZ()+(s.z()-s.previousZ())*(double)f.partial()-f.cameraZ();
        if((s.flags()&4)!=0)y-=.125d;if((s.flags()&8)!=0)y-=.125d;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return refuse(2);
        try{
            float fx=ActorRootMath073.checkedFloat(x,0),fy=ActorRootMath073.checkedFloat(y,0),fz=ActorRootMath073.checkedFloat(z,0);
            if(Math.abs(fx)>Float.MAX_VALUE/16||Math.abs(fy)>Float.MAX_VALUE/16||Math.abs(fz)>Float.MAX_VALUE/16)return refuse(4);
            float old=s.previousBodyYaw(),now=s.bodyYaw(),difference=now-old;
            if(!Float.isFinite(old)||!Float.isFinite(now)||old!=0&&Math.abs(old)<Float.MIN_NORMAL||now!=0&&Math.abs(now)<Float.MIN_NORMAL||Math.abs(difference)>1080)return refuse(3);
            while(difference< -180)difference+=360;while(difference>=180)difference-=360;
            float step=f.partial()*difference,body=old+step,degrees=180f-body;
            if(!Float.isFinite(degrees)||Math.abs(degrees)>360)return refuse(3);
            float angle=ActorRootMath073.checkedFloat((double)degrees*0.017453292519943295,0);
            if(!safeCoefficients(angle))return refuse(4);
            if((s.flags()&1)!=0&&!safeCoefficients((float)Math.PI))return refuse(4);
            return 0;
        }catch(ActorRootMath073.Decline decline){return refuse(decline.reason);}
    }
    private static boolean safeCoefficients(float angle){
        sineCalls++;double sine=Math.sin((double)angle);float s=ActorRootMath073.checkedFloat(sine,Math.abs(sine)*0x1p-36+0x1p-68);
        float square=s*s,radicand=1f-square;if(radicand<0||!Float.isFinite(radicand))return false;
        if(radicand!=0){sqrtCalls++;double c=Math.sqrt((double)radicand);ActorRootMath073.checkedFloat(c,Math.abs(c)*0x1p-36+0x1p-90);}
        return true;
    }
    private static int refuse(int reason){refusals++;return reason;}
    static boolean raw(double a,double b){return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);}
}
