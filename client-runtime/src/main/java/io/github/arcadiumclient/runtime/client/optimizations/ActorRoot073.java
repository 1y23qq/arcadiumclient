package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.model.PlayerModelPart;
import io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelActorRootOrigin073;
import org.joml.Matrix4f;
import org.joml.Options;
import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRoot073 {
    private static final boolean REQUESTED=Boolean.getBoolean("arcadium.actorRoot073.enabled");
    private static final boolean CONFLICT=Boolean.getBoolean("arcadium.actorScene073.enabled")||Boolean.getBoolean("arcadium.actorScene073.fusedInput");
    public static final boolean QUALIFIED=false;
    public static final String KERNEL_SHA="37b4339a5548f4da6619ddff805c1afbf77719e8a72b1e98c5ea47e246d02534";
    private static float delta;private static final float[] pose=new float[16],partPose=new float[6];
    private static final class Live {static final ActorRootGl073 gpu=new ActorRootGl073();static final ActorRootCore073 core=new ActorRootCore073(gpu);}
    public static long layerFallbacks,sourceFallbacks,cpuRecoveries,rootJobs,ordinaryPoseInterpolationRows;
    public record Metrics(boolean requested,boolean qualified,int actors,int nativeBuffers,long retainedBytes,long reservations,long commits,long actorBytes,long frameBytes,long poseBytes,long membershipBytes,long rootDispatches,long busyFallbacks,long foreignFallbacks,long sourceRefusals,long scalarChecks,long scalarSineCalls,long scalarSqrtCalls,long scalarRefusals,long cpuRootRecoveries,long layerFallbacks){}
    private ActorRoot073(){}
    public static boolean enabled(){return REQUESTED&&!CONFLICT;}
    public static Metrics metrics(){if(!enabled())return new Metrics(false,false,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);var c=Live.core;var g=Live.gpu;return new Metrics(true,false,c.size(),g.nativeBuffers(),g.retainedBytes(),c.reservations,c.commits,g.actorBytes,g.frameBytes,g.poseBytes,g.membershipBytes,g.dispatches,g.busyFallbacks,g.foreignFallbacks,g.sourceRefusals,ActorRootScalar073.checks,ActorRootScalar073.sineCalls,ActorRootScalar073.sqrtCalls,ActorRootScalar073.refusals,cpuRecoveries,layerFallbacks);}
    public static void frame(float partial){if(!enabled())return;delta=partial;Live.core.world(Minecraft.getInstance().world);}
    public static void beginBatch(){if(enabled())Live.core.beginBatch(delta);}
    public static void tick(){
        if(!enabled())return;Minecraft mc=Minecraft.getInstance();var core=Live.core;core.world(mc.world);if(mc.world==null)return;core.beginTick();
        for(Object value:mc.world.players)if(value instanceof ClientPlayerEntity p){State state=snapshot(p,core.previous(p),-1);core.observe(p,state);}core.endTick();
    }
    private static State snapshot(ClientPlayerEntity p,State previous,int supplied){
        int flags=(supplied>=0?supplied:(PlayerPoseOptions.upsideDown(p.getName(),p.isModelPartVisible(PlayerModelPart.CAPE))?1:0)|("slim".equals(p.getModelType())?2:0))|(previous==null?0:previous.flags()&12);
        double oldX=p.ticks==0?p.x:p.prevX,oldY=p.ticks==0?p.y:p.prevY,oldZ=p.ticks==0?p.z:p.prevZ;
        if(previous!=null&&ActorRootScalar073.raw(previous.previousX(),oldX)&&ActorRootScalar073.raw(previous.previousY(),oldY)&&ActorRootScalar073.raw(previous.previousZ(),oldZ)&&ActorRootScalar073.raw(previous.x(),p.x)&&ActorRootScalar073.raw(previous.y(),p.y)&&ActorRootScalar073.raw(previous.z(),p.z)&&Float.floatToRawIntBits(previous.previousBodyYaw())==Float.floatToRawIntBits(p.lastBodyYaw)&&Float.floatToRawIntBits(previous.bodyYaw())==Float.floatToRawIntBits(p.bodyYaw)&&Float.floatToRawIntBits(previous.height())==Float.floatToRawIntBits(p.height)&&previous.flags()==flags)return previous;
        return new State(oldX,oldY,oldZ,p.x,p.y,p.z,p.lastBodyYaw,p.bodyYaw,p.height,flags);
    }
    public static int reserve(ClientPlayerEntity p,double x,double y,double z,boolean slim,boolean inverted,float partial,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template,int options){
        if(!enabled()||Options.FASTMATH||Options.USE_MATH_FMA||Float.floatToRawIntBits(partial)!=Float.floatToRawIntBits(delta))return -1;if(GpuResidentBodyShaders.active()){sourceFallbacks++;return -1;}Minecraft mc=Minecraft.getInstance();var core=Live.core;core.world(mc.world);
        if(!(mc.getEntityRenderDispatcher() instanceof MixinGpuModelActorRootOrigin073 origin)){sourceFallbacks++;return -1;}
        if(!core.camera(origin.arcadium$rootOffsetX(),origin.arcadium$rootOffsetY(),origin.arcadium$rootOffsetZ())){sourceFallbacks++;return -1;}
        State state=snapshot(p,core.previous(p),(inverted?1:0)|(slim?2:0));
        pose[0]=walk;pose[1]=speed;pose[2]=age;pose[3]=yaw;pose[4]=pitch;pose[5]=swing;pose[6]=sneak?1:0;pose[7]=bow?1:0;pose[8]=left;pose[9]=right;pose[10]=riding?1:0;pose[11]=template;pose[12]=options;pose[13]=-1;pose[14]=pose[15]=0;
        int job=core.reserve(p,state,x,y,z,pose);if(job>=0){rootJobs++;ordinaryPoseInterpolationRows++;}return job;
    }
    public static boolean rootJob(int job){return enabled()&&ActorRootCore073.rootJob(job);}
    public static boolean hasJobs(){return enabled()&&Live.core.hasJobs();}
    public static void commit(int job){if(rootJob(job))Live.core.commit(job);}
    public static void cancel(int job){if(rootJob(job))Live.core.cancel(job);}
    public static boolean linked(int program,String source){return enabled()&&Live.gpu.linked(program,source);}
    public static void requireOwner(){if(enabled())Live.gpu.requireOwner();}
    public static boolean ownsOwner(){return !enabled()||Live.gpu.ownsOwner();}
    public static ActorRootProtocol073.Caller captureCaller(){return enabled()?Live.gpu.captureCaller():null;}
    public static boolean finishCaller(ActorRootProtocol073.Caller caller){return caller==null||Live.gpu.finishCaller(caller);}
    public static void deferShader(int shader){if(enabled())Live.gpu.deferShader(shader);}
    public static void deferBuffer(int buffer){if(enabled())Live.gpu.deferBuffer(buffer);}
    public static boolean foreign(Throwable failure){for(int i=0;i<16&&failure!=null;i++,failure=failure.getCause())if(failure instanceof ActorRootProtocol073.Foreign)return true;return false;}
    public static ActorRootGl073.Scope bind(){try{return enabled()?Live.gpu.bind(Live.core):null;}catch(ActorRootProtocol073.Foreign foreign){throw foreign;}catch(RuntimeException failure){throw new Failure(failure);}}
    public static final class Failure extends RuntimeException {public Failure(Throwable failure){super(failure);}}
    public static boolean failure(Throwable failure){return failure instanceof Failure;}
    public static void disablePrivate(){if(enabled())Live.gpu.disablePrivate();}
    public static void invalidate(){if(enabled())Live.core.invalidate();}
    public static void close(){if(enabled()){Live.gpu.close();Live.core.invalidate();}}
    public static Matrix4f resolve(int encoded,Matrix4f destination,float[] templates){
        int job=encoded/16,part=encoded%16;var e=Live.core.job(job);if(e==null||part>=12)throw new IllegalArgumentException("Stale or unknown root recovery job");cpuRecoveries++;Live.core.rootMatrixConstructions++;
        return resolveData(e.state,Live.core.frame(),e.pose,part,destination,templates);
    }
    static Matrix4f resolveData(State s,Frame f,float[] p,int part,Matrix4f destination,float[] templates){
        float body=angle(s.previousBodyYaw(),s.bodyYaw(),f.partial());double x=s.previousX()+(s.x()-s.previousX())*(double)f.partial()-f.cameraX(),y=s.previousY()+(s.y()-s.previousY())*(double)f.partial()-f.cameraY(),z=s.previousZ()+(s.z()-s.previousZ())*(double)f.partial()-f.cameraZ();if((s.flags()&4)!=0)y-=.125;if((s.flags()&8)!=0)y-=.125;
        destination.identity().translate((float)x,(float)y,(float)z).rotateY((float)Math.toRadians(180-body));if((s.flags()&1)!=0)destination.translate(0,s.height()+.1f,0).rotateZ((float)Math.PI);destination.scale(-1,-1,1).scale(.9375f).translate(0,-1.5078125f,0);
        int t=(int)p[11]+part*16;if(t<0||t+15>=templates.length)throw new IllegalArgumentException("Root template changed before recovery");PlayerPoseMath.part(p[0],p[1],p[2],p[3],p[4],p[5],(int)p[8],(int)p[9],p[6]!=0,p[10]!=0,p[7]!=0,PlayerPoseMath.BONES[part],templates[t],templates[t+1],templates[t+2],(int)p[12],partPose);
        if(p[6]!=0&&((int)p[12]&2)==0)destination.translate(0,.2f,0);
        return destination.translate(partPose[0]*.0625f+templates[t+4],partPose[1]*.0625f+templates[t+5],partPose[2]*.0625f+templates[t+6]).rotateZ(partPose[5]).rotateY(partPose[4]).rotateX(partPose[3]).translate(templates[t+8],templates[t+9],templates[t+10]).scale(templates[t+12],templates[t+13],templates[t+14]);
    }
    private static float angle(float old,float now,float partial){float difference=now-old;while(difference< -180)difference+=360;while(difference>=180)difference-=360;return old+partial*difference;}
}
