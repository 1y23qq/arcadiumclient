package io.github.arcadiumclient.runtime.client.optimizations;

public final class PreparedPlayerEmission073 {
    public interface Adapter {
        default Object createLease(){return null;}
        Object preflight(Object model,Object entity,float scale,Object reuse);
        boolean emit(Object lease,int block);
        void release(Object lease);
    }
    public static final class Controller {
        private static final class Scope {Object model,entity,lease;float scale;boolean decided,admitted;int blocks;}
        private final Scope[] scopes=new Scope[8];private final Adapter adapter;private int depth,blocked;
        public long actors,bodyBlocks,outerBlocks,refusals,partialFallbacks;
        public Controller(Adapter adapter){this.adapter=adapter;}
        public int begin(Object model,Object entity,float scale){if(depth==scopes.length){blocked++;return -1;}Scope s=scopes[depth];if(s==null){scopes[depth]=s=new Scope();s.lease=adapter.createLease();}s.model=model;s.entity=entity;s.scale=scale;s.decided=s.admitted=false;s.blocks=0;return ++depth;}
        public boolean emit(Object model,Object entity,float scale,int block){
            if(blocked!=0||depth==0||block<0||block>1)return false;Scope s=scopes[depth-1];if(s.model!=model||s.entity!=entity||Float.floatToRawIntBits(s.scale)!=Float.floatToRawIntBits(scale)||(s.blocks&(1<<block))!=0)return false;
            if(!s.decided){s.decided=true;if(block!=0){refusals++;return false;}Object prepared=adapter.preflight(model,entity,scale,s.lease);s.admitted=prepared!=null;if(!s.admitted){refusals++;return false;}s.lease=prepared;actors++;}
            if(!s.admitted)return false;boolean emitted=adapter.emit(s.lease,block);if(!emitted){if(s.blocks==0){s.admitted=false;refusals++;}else partialFallbacks++;return false;}s.blocks|=1<<block;if(block==0)bodyBlocks++;else outerBlocks++;return true;
        }
        public void end(int token){if(token<0){if(blocked==0)throw new IllegalStateException("Prepared model blocked-scope order");blocked--;return;}if(token==0)return;if(token!=depth)throw new IllegalStateException("Prepared model scope order");Scope s=scopes[--depth];try{if(s.lease!=null)adapter.release(s.lease);}finally{s.model=s.entity=null;s.decided=s.admitted=false;s.blocks=0;}}
        public int depth(){return depth;}
    }
    private static volatile boolean certified;
    private static final ThreadLocal<Controller> local=ThreadLocal.withInitial(()->new Controller(NativePlayerPosePipeline.preparedAdapter()));
    private PreparedPlayerEmission073(){}
    static void certify(){certified=true;}
    public static boolean certified(){return certified;}
    public static int begin(Object model,Object entity,float scale){if(!PreparedPlayerMode073.ENABLED||!NativePlayerPosePipeline.preparedSkinAllowed())return 0;if(!certified&&NativePlayerPosePipeline.preparedEvidence(model))certified=true;return certified&&NativePlayerPosePipeline.preparedEnabled()?local.get().begin(model,entity,scale):0;}
    public static boolean emit(Object model,Object entity,float scale,int block){return PreparedPlayerMode073.ENABLED&&certified&&local.get().emit(model,entity,scale,block);}
    public static void end(int token){if(token!=0)local.get().end(token);}
}
