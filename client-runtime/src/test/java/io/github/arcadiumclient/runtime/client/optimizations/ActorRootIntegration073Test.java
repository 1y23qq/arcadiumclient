package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRootIntegration073Test {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
    private static final class Backend implements ActorRootCore073.Backend {
        final BitSet dirty=new BitSet(2048);boolean ready=true,foreign;int invalidations,poseWrites;long actorBytes,frameBytes,frame=-1;int rootDeclines;
        public boolean reserve(ActorRootCore073 core){return ready&&!foreign;}
        public void dirty(int slot){dirty.set(slot);}public void roster(){}public void pose(ActorRootCore073.Entry entry){poseWrites++;}public void endBatch(){}
        public void invalidate(){invalidations++;dirty.clear();}
        void execute(ActorRootCore073 core){require(!foreign,"Foreign backend executed");for(int slot=dirty.nextSetBit(0);slot>=0;slot=dirty.nextSetBit(slot+1))actorBytes+=80;dirty.clear();if(frame!=core.frame().frameId()){frame=core.frame().frameId();frameBytes+=48;}
            for(var e:core.entries)if(e!=null&&e.claimBatch==core.batch()){var result=ActorRootOracle073.prototype(e.state,core.frame(),true);if(result.status()!=0)rootDeclines++;require(result.status()==0,"CPU admitted a shader-math decline");require(Arrays.equals(bits(result.root()),bits(ActorRootOracle073.jomlReference(e.state,core.frame()))),"Admitted root differs from actual JOML");}}
    }
    static int[] bits(float[] a){int[] b=new int[a.length];for(int i=0;i<a.length;i++)b[i]=Float.floatToRawIntBits(a[i]);return b;}
    static State state(double x,double y,double z,float yaw,int flags){return new State(x,y,z,x,y,z,yaw,yaw,1.8f,flags);}
    static float[] pose(){float[] p=new float[16];p[2]=100;p[5]=0;p[11]=0;p[12]=0;p[13]=-1;return p;}
    public static void main(String[] args)throws Exception {
        Backend backend=new Backend();ActorRootCore073 core=new ActorRootCore073(backend);Object world=new Object(),a=new Object(),b=new Object();core.world(world);State sa=state(1,4,3,0,0),sb=state(2,4,4,20,1);core.beginTick();core.observe(a,sa);core.observe(b,sb);core.endTick();
        for(int i=0;i<100;i++){float partial=(i%9)/8f;core.beginBatch(partial);require(core.camera(0,0,0),"Current camera refused");int ja=core.reserve(a,sa,1,4,3,pose()),jb=core.reserve(b,sb,2,4,4,pose());require(ja==8192&&jb==8193,"Persistent actor jobs changed across current partial");core.commit(ja);core.commit(jb);require(core.reserve(a,sa,1,4,3,pose())<0,"Duplicate actor suppressed twice");backend.execute(core);require(core.rootMatrixConstructions==0,"Ordinary admission built a Matrix4f");}
        require(backend.actorBytes==160&&backend.frameBytes==4800&&core.poseBytes==12800,"Retained actor80/frame48/pose64 publication contract");long actorBytesFirst100=backend.actorBytes;require(backend.rootDeclines==0,"Suppressed actor required a GPU-status readback");
        core.beginBatch(.5f);require(core.camera(10,0,0),"Current moving camera");backend.ready=false;require(core.reserve(a,sa,-9,4,3,pose())<0,"Busy store did not retain same-frame CPU route");require(!core.hasJobs(),"Failed reservation left suppressed geometry");backend.ready=true;int j=core.reserve(a,sa,-9,4,3,pose());require(j>=0,"Same-frame retry did not use current camera");core.cancel(j);require(!core.hasJobs(),"Cancelled reservation remained visible");require(core.reserve(a,sa,-9,4,3,pose())>=0,"Cancelled append was not reusable");
        core.beginBatch(.25f);require(core.job(j)==null,"Previous-frame job was reused");backend.foreign=true;require(core.camera(0,0,0)&&core.reserve(a,sa,1,4,3,pose())<0,"Foreign context accepted a root");backend.foreign=false;
        core.beginBatch(.25f);require(core.camera(0,0,0),"Camera setup");State moved=new State(1,4,3,9,8,7,0,20,1.8f,0);require(core.reserve(a,moved,3,5,4,pose())>=0,"Current tick endpoints/partial were not interpolated immediately");backend.execute(core);
        core.invalidate();require(core.job(j)==null&&!core.hasJobs(),"Resource lifetime retained an actor job");core.world(new Object());core.beginBatch(0);require(core.camera(0,0,0),"Fresh world camera");int replacement=core.reserve(new Object(),sa,1,4,3,pose());require(replacement>=0,"Fresh world identity not admitted");core.commit(replacement);core.beginTick();core.endTick();core.beginBatch(0);require(core.size()==0,"Retired actor was not removed at a safe batch boundary");
        State dual=new State(Double.NaN,0,0,0,0,0,Float.NaN,0,1.8f,0);Frame frame=new Frame(0,0,0,.5f,1,1,0);require(ActorRootScalar073.admit(dual,frame)==2&&ActorRootOracle073.prototype(dual,frame,true).status()==2,"Normative dual-NaN position priority changed");
        Random random=new Random(730073);int accepted=0,refused=0,shaderEligible=0;
        for(int n=0;n<25000;n++){double base=n%3==0?3e7:n%3==1?1e12:0;float old=(random.nextFloat()-.5f)*720,now=old+(random.nextFloat()-.5f)*300;State s=new State(base+1,base+65,base+3,base+1.125,base+65.0625,base+3.25,old,now,n%2==0?1.8f:1.65f,n%16);Frame f=new Frame(base,base+64,base,random.nextFloat(),1,n+1,0);int reference=ActorRootOracle073.prototype(s,f,true).status();if(reference==0)shaderEligible++;int status=ActorRootScalar073.admit(s,f);if(status==0){accepted++;require(reference==0,"Conservative scalar admission missed a math guard");}else refused++;}
        require(accepted>=shaderEligible*.99,"Conservative scalar admission unexpectedly rejects the qualified math range");
        ByteBuffer record=ByteBuffer.allocate(80),current=ByteBuffer.allocate(48);ActorRootInput073.encode(record,3,4,true,sa);ActorRootInput073.encodeFrame(current,frame,2048);require(record.position()==80&&current.position()==48,"Source input ABI changed");
        float[] templates=new float[12*16];for(int i=0;i<12;i++){int t=i*16;templates[t]=i%2==0?5:-5;templates[t+1]=i<4?0:12;templates[t+2]=.1f;templates[t+8]=-.2f;templates[t+9]=-.3f;templates[t+10]=-.1f;templates[t+12]=.4f;templates[t+13]=.8f;templates[t+14]=.25f;}
        GpuPlayerPackets.beginBatch();int template=GpuPlayerPackets.registerTemplate(templates);int poseWords=0;
        for(int n=0;n<24;n++){State s=state(n-12,4+n*.0625,3,10+n,n%4);Frame f=new Frame(.5,.25,.75,.5f,1,n+1,0);float[] metadata=pose();metadata[0]=n*.1f;metadata[1]=.4f;metadata[2]=100.5f;metadata[3]=n;metadata[4]=n*.25f;metadata[5]=.3f;metadata[6]=n%2;metadata[7]=n%3==0?1:0;metadata[8]=n%2;metadata[9]=n%4;metadata[10]=0;metadata[11]=template;metadata[12]=PlayerPoseOptions.flags();org.joml.Matrix4f baseMatrix=new org.joml.Matrix4f().set(ActorRootOracle073.jomlReference(s,f));int job=GpuPlayerPackets.add(baseMatrix,metadata[0],metadata[1],metadata[2],metadata[3],metadata[4],metadata[5],(int)metadata[8],(int)metadata[9],metadata[6]!=0,false,metadata[7]!=0,template);
            for(int part=0;part<12;part++){float[] expected=GpuPlayerPackets.resolve(job*16+part,new org.joml.Matrix4f()).get(new float[16]),actual=ActorRoot073.resolveData(s,f,metadata,part,new org.joml.Matrix4f(),templates).get(new float[16]);require(Arrays.equals(bits(expected),bits(actual)),"Current-snapshot CPU recovery differs from Main30 model-pose path");poseWords+=16;}}
        Path directory=Path.of(args[0]);String ordinary=Files.readString(directory.resolve("ordinary_model.comp")),player=Files.readString(directory.resolve("player_pose.glsl"));String patched=ActorRootSource073.compose(ordinary,player,ActorRootSource073.sha(ordinary),ActorRootSource073.sha(player));
        require(ActorRoot073.enabled()&&patched.contains("binding=7")&&patched.contains("arcRootRead(p+29u)")&&!patched.contains("pd[p+29u]"),"Actual model-pose consumer was not connected");require(ActorRootSource073.compose(ordinary,player,"changed",ActorRootSource073.sha(player)).equals(ordinary),"Unknown shader source was patched");Files.writeString(directory.resolve("persistent_model.comp"),patched);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"scalarAccepted\":"+accepted+",\"scalarRefused\":"+refused+",\"shaderMathEligible\":"+shaderEligible+",\"cpuRecoveryModelPoseWords\":"+poseWords+",\"actor80BytesAcross100Frames\":"+actorBytesFirst100+",\"zeroGpuStatusReadbacks\":true,\"sameFrameFallbackContracts\":true,\"dirtyStateAndCurrentCameraVerified\":true,\"noGlCalls\":true}");
    }
}
