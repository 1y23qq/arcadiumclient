// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class LowerNamePreparationFusionTest implements Opcodes {
    private static final String OWNER="net/minecraft/client/render/entity/EntityRenderer";
    private static final String SELF=Type.getInternalName(LowerNamePreparationFusionTest.class);
    private static final String ENTITY=SELF+"$EntityStub",BATCH="dev/rdh/argentum/impl/render/entity/NameTagBatch";
    private static final String OP="com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String DESC="(L"+ENTITY+";Ljava/lang/String;DDDI)V";
    private static final String WRAPPER="fixture$argentum$batchNameTag",NEXT="unchangedLayoutStage",BRIDGE="mixinextras$bridge$lowerName";
    private static final String TYPED="arcadium$typedLowerHologramName",BOOT="arcadium.namePreparation073.lowerEntity";
    private static final String PINNED="bf7d8db67cd4ff5cfedd95ee6944c5b0f20060f006658a274ce7000a6c0c298f";
    private static final String MERGED="Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String MIXIN="dev.rdh.argentum.mixin.features.nametag.EntityRendererMixin";
    private static final Handle META=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);
    private static final ThreadLocal<State> RUN=new ThreadLocal<>();
    private static final String MUTATED=new String("mutated §bname");
    private static int differentialCases,negativeCases;

    public static final class EntityStub {
        int revision;
        @Override public boolean equals(Object other){return other instanceof EntityStub;}
        @Override public int hashCode(){return 1;}
    }
    private record Input(EntityStub entity,String text,double x,double y,double z,int distance) { }
    private record Call(EntityStub entity,String text,long x,long y,long z,int distance,int revision) { }
    private record Result(List<String> events,List<Call> calls,Throwable failure,int depth,boolean restored,long typed) { }
    private enum Mode { NORMAL,ACTIVE_THROW,ENTER_THROW,NEXT_THROW,EXIT_THROW,NEXT_AND_EXIT_THROW,MUTATE,REENTER_WRONG,REENTER_OWNED,REENTER_THROW,NULL_SCOPE }
    private record Spec(boolean batch,boolean enabled,int sites,int scope,int revision,Mode mode,EntityStub child,Throwable nextFailure,Throwable exitFailure) { }

    @Test void syntheticWrapperMatchesTheCapturedFingerprintAndOnlyDispatchChanges() throws Throwable {
        ClassNode original=fixture(),changed=copy(original);assertEquals(PINNED,LowerNamePreparationFusion.audit(original));
        Map<String,String> untouched=new HashMap<>();for(MethodNode m:original.methods)if(!m.name.equals("entry"))untouched.put(m.name+m.desc,trace(m));
        assertEquals(1,LowerNamePreparationFusion.apply(changed));assertEquals(PINNED,LowerNamePreparationFusion.audit(changed));
        for(MethodNode m:changed.methods)if(untouched.containsKey(m.name+m.desc))assertEquals(untouched.get(m.name+m.desc),trace(m),m.name);
        MethodNode typed=find(changed,TYPED);assertEquals(2,typed.tryCatchBlocks.size());assertTrue(typed.tryCatchBlocks.stream().allMatch(b->b.type==null));
        assertEquals(2,code(typed).stream().filter(n->n instanceof MethodInsnNode c&&c.owner.equals(OWNER)&&c.name.equals(NEXT)).count());
        assertTrue(code(typed).stream().noneMatch(n->n instanceof TypeInsnNode t&&t.getOpcode()==ANEWARRAY||n instanceof MethodInsnNode c&&(c.owner.equals(OP)||c.name.equals("valueOf"))));
        assertNull(typed.visibleAnnotations);assertNull(typed.invisibleAnnotations);assertEquals(6,typed.parameters.size());
        for(MethodNode m:changed.methods)new Analyzer<>(new BasicVerifier()).analyze(changed.name,m);
        byte[] once=bytes(changed);assertEquals(0,LowerNamePreparationFusion.apply(changed));assertArrayEquals(once,bytes(changed));
        ClassNode zero=fixture();zero.methods.forEach(m->{m.maxLocals=0;m.maxStack=0;});assertEquals(1,LowerNamePreparationFusion.apply(zero));
    }

    @Test void bothWrapperBranchesPreserveRawBitsReferencesAndNullableArguments() throws Throwable {
        Fixture original=new Fixture(false,true),typed=new Fixture(true,true);EntityStub owner=new EntityStub();
        double[][] values={{Double.longBitsToDouble(0x7ff8000000000421L),-0.0,Double.NEGATIVE_INFINITY},{Double.longBitsToDouble(0x7ff0000000000001L),Double.longBitsToDouble(0xfff8000000001234L),Double.MIN_VALUE},{Math.nextUp(1234.5),0.0,Double.MAX_VALUE}};
        for(boolean batch:new boolean[]{false,true})for(double[] v:values)for(int distance:new int[]{Integer.MIN_VALUE,-129,0,Integer.MAX_VALUE})for(String text:new String[]{null,new String(""),new String("§bNPC ✫ → :name:")}){
            Input input=new Input(owner,text,v[0],v[1],v[2],distance);Spec spec=spec(batch,true,1,1,Mode.NORMAL);
            Result expected=run(original,input,spec),actual=run(typed,input,spec);same(expected,actual);assertEquals(1,actual.typed);assertRaw(input,actual.calls.getFirst());
        }
        Input absent=new Input(null,null,-0.0,Double.NaN,1.25,-1);Result a=run(original,absent,spec(true,true,1,1,Mode.NORMAL)),b=run(typed,absent,spec(true,true,1,1,Mode.NORMAL));same(a,b);assertEquals(0,b.typed);assertRaw(absent,b.calls.getFirst());
    }

    @Test void callbackMutationReentryAndEveryFinallyOutcomeMatchOriginal() throws Throwable {
        Fixture original=new Fixture(false,true),typed=new Fixture(true,true);EntityStub owner=new EntityStub();Input input=new Input(owner,new String("original"),-0.0,Double.longBitsToDouble(0x7ff8000000000007L),9.5,831);
        for(boolean batch:new boolean[]{false,true})for(Mode mode:Mode.values()){
            Spec spec=spec(batch,true,1,1,mode);Result expected=run(original,input,spec),actual=run(typed,input,spec);same(expected,actual);
            assertTrue(actual.typed>=1);if(mode==Mode.REENTER_WRONG)assertEquals(1,actual.typed);if(mode==Mode.REENTER_OWNED||mode==Mode.REENTER_THROW)assertEquals(2,actual.typed);
            if(mode==Mode.MUTATE){assertSame(spec.child,actual.calls.getFirst().entity);assertSame(MUTATED,actual.calls.getFirst().text);assertEquals(Integer.MIN_VALUE,actual.calls.getFirst().distance);}
            if(mode==Mode.NEXT_THROW)assertSame(spec.nextFailure,actual.failure);
            if(batch&&(mode==Mode.EXIT_THROW||mode==Mode.NEXT_AND_EXIT_THROW))assertSame(spec.exitFailure,actual.failure);
            assertEquals(0,actual.depth);assertTrue(actual.restored);
        }
    }

    @Test void bootFlagsSiteCountsOwnerIdentityAndLiveRevisionAreCheckedPerCall() throws Throwable {
        EntityStub owner=new EntityStub();Input input=new Input(owner,new String("live"),1,2,3,4);
        for(boolean boot:new boolean[]{false,true}){
            Fixture original=new Fixture(false,boot),typed=new Fixture(true,boot);
            for(boolean enabled:new boolean[]{false,true})for(int sites:new int[]{0,1,2})for(int scope:new int[]{0,1,2})for(int revision:new int[]{7,8}){
                Spec spec=new Spec(true,enabled,sites,scope,revision,Mode.NORMAL,new EntityStub(),new IllegalStateException("next"),new IllegalArgumentException("exit"));
                Result expected=run(original,input,spec),actual=run(typed,input,spec);same(expected,actual);
                assertEquals(boot&&enabled&&sites==1&&scope==1?1:0,actual.typed);assertEquals(revision,actual.calls.getFirst().revision);
            }
        }
    }

    @Test void anotherThreadCannotBorrowTheActiveOwner() throws Throwable {
        Fixture original=new Fixture(false,true),typed=new Fixture(true,true);Input input=new Input(new EntityStub(),"foreign",1,2,3,4);
        Object a=original.enter(input.entity),b=typed.enter(input.entity);
        try(ExecutorService executor=Executors.newSingleThreadExecutor()){
            Result expected=executor.submit(()->unchecked(original,input)).get(5,TimeUnit.SECONDS),actual=executor.submit(()->unchecked(typed,input)).get(5,TimeUnit.SECONDS);
            same(expected,actual);assertEquals(0,actual.typed);assertTrue(original.active(input.entity));assertTrue(typed.active(input.entity));
        }finally{original.exit(a);typed.exit(b);}
    }

    @Test void unknownBodiesBridgesMetadataAndPartialSitesRemainUnchanged() {
        reject("unknown target",n->n.name="custom/Renderer");
        reject("unknown merged owner",n->annotation(find(n,WRAPPER)).values.set(1,"custom.NameMixin"));
        reject("different priority",n->annotation(find(n,WRAPPER)).values.set(3,1500));
        reject("missing annotation",n->find(n,WRAPPER).visibleAnnotations=null);
        reject("extra wrapper instruction",n->find(n,WRAPPER).instructions.insert(new InsnNode(NOP)));
        reject("one pack changed",n->{List<AbstractInsnNode> c=code(find(n,WRAPPER));for(int i=c.size()-1;i>=0;i--)if(c.get(i) instanceof VarInsnNode v&&v.var==10){find(n,WRAPPER).instructions.insert(c.get(i),new InsnNode(NOP));break;}});
        reject("escaped operation",n->{MethodNode m=find(n,WRAPPER);m.instructions.insertBefore(m.instructions.getLast(),new VarInsnNode(ALOAD,10));m.instructions.insertBefore(m.instructions.getLast(),new InsnNode(POP));});
        reject("changed catch type",n->find(n,WRAPPER).tryCatchBlocks.getFirst().type="java/lang/RuntimeException");
        reject("changed catch range",n->{MethodNode m=find(n,WRAPPER);m.tryCatchBlocks.getFirst().end=m.tryCatchBlocks.getFirst().handler;});
        reject("changed bridge",n->find(n,BRIDGE).instructions.insert(new InsnNode(NOP)));
        reject("bridge catch",n->{MethodNode m=find(n,BRIDGE);LabelNode a=new LabelNode(),b=new LabelNode();m.instructions.insert(a);m.instructions.add(b);m.tryCatchBlocks.add(new TryCatchBlockNode(a,b,b,null));});
        reject("changed bridge unboxing",n->{for(AbstractInsnNode i:find(n,BRIDGE).instructions)if(i instanceof MethodInsnNode c&&c.name.equals("doubleValue")){c.name="unknownDouble";break;}});
        reject("nonprivate next stage",n->find(n,NEXT).access=ACC_PUBLIC);
        reject("unknown lambda factory",n->dynamic(n).bsm=new Handle(H_INVOKESTATIC,"custom/Factory","metafactory",META.getDesc(),false));
        reject("wrong lambda handle kind",n->{Handle h=(Handle)dynamic(n).bsmArgs[1];dynamic(n).bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,h.getOwner(),h.getName(),h.getDesc(),false);});
        reject("changed preload",n->{List<AbstractInsnNode> c=code(find(n,"entry"));((VarInsnNode)c.get(2)).var=1;});
        reject("interior debug boundary",n->{MethodNode m=find(n,"entry");LabelNode label=new LabelNode();m.instructions.insertBefore(code(m).get(4),label);m.instructions.insert(label,new LineNumberNode(73073,label));});
        reject("interior branch",n->{MethodNode m=find(n,"entry");LabelNode label=new LabelNode();m.instructions.insertBefore(code(m).get(4),label);m.instructions.insertBefore(m.instructions.getLast(),new JumpInsnNode(GOTO,label));});
        reject("interior catch boundary",n->{MethodNode m=find(n,"entry");LabelNode a=new LabelNode(),b=new LabelNode(),h=new LabelNode();m.instructions.insertBefore(code(m).get(4),a);m.instructions.insertBefore(m.instructions.getLast(),b);m.instructions.add(h);m.tryCatchBlocks.add(new TryCatchBlockNode(a,b,h,null));});
        reject("duplicate caller",n->{MethodNode m=copy(find(n,"entry"));m.name="secondEntry";n.methods.add(m);});
        reject("duplicate wrapper",n->{MethodNode m=copy(find(n,WRAPPER));m.name="another$argentum$batchNameTag";n.methods.add(m);});
        reject("malformed parameter metadata",n->find(n,WRAPPER).parameters.removeLast());
        reject("typed collision",n->{MethodNode m=new MethodNode(ACC_PRIVATE,TYPED,"()V",null,null);m.instructions.add(new InsnNode(RETURN));n.methods.add(m);});
    }

    private static Spec spec(boolean batch,boolean enabled,int sites,int scope,Mode mode){return new Spec(batch,enabled,sites,scope,43,mode,new EntityStub(),new IllegalStateException("next failure"),new IllegalArgumentException("exit failure"));}
    private static Result unchecked(Fixture fixture,Input input){try{return run(fixture,input,spec(true,true,1,1,Mode.NORMAL));}catch(Throwable failure){throw new AssertionError(failure);}}
    private static Result run(Fixture fixture,Input input,Spec spec)throws Throwable {
        if(input.entity!=null)input.entity.revision=spec.revision;fixture.reset(spec.enabled,spec.sites);State state=new State(fixture,input,spec);RUN.set(state);
        Object owner=spec.scope==1?input.entity:spec.scope==2?new EntityStub():null,previous=fixture.enter(owner);Throwable thrown=null;boolean restored;
        try{fixture.invoke(input);}catch(Throwable error){thrown=error;}finally{fixture.exit(previous);restored=!fixture.active(input.entity);RUN.remove();}
        return new Result(List.copyOf(state.events),List.copyOf(state.calls),thrown,state.depth,restored,fixture.typed());
    }
    private static void same(Result expected,Result actual){assertEquals(expected.events,actual.events);assertEquals(expected.calls,actual.calls);assertSame(expected.failure,actual.failure);assertEquals(expected.depth,actual.depth);assertEquals(expected.restored,actual.restored);differentialCases++;}
    private static void assertRaw(Input input,Call call){assertSame(input.entity,call.entity);assertSame(input.text,call.text);assertEquals(Double.doubleToRawLongBits(input.x),call.x);assertEquals(Double.doubleToRawLongBits(input.y),call.y);assertEquals(Double.doubleToRawLongBits(input.z),call.z);assertEquals(input.distance,call.distance);}

    public static final class BatchStub {
        private static final BatchStub INSTANCE=new BatchStub();
        public static BatchStub active(){State s=RUN.get();s.events.add("active:"+s.spec.revision);if(s.spec.mode==Mode.ACTIVE_THROW)throwUnchecked(s.spec.nextFailure);if(s.spec.mode==Mode.MUTATE&&s.input.entity!=null)s.input.entity.revision++;return s.spec.batch?INSTANCE:null;}
        public void enter(){State s=RUN.get();s.events.add("enter");if(s.spec.mode==Mode.ENTER_THROW)throwUnchecked(s.spec.nextFailure);s.depth++;}
        public void exit(){State s=RUN.get();s.events.add("exit");s.depth--;if(s.spec.mode==Mode.EXIT_THROW||s.spec.mode==Mode.NEXT_AND_EXIT_THROW)throwUnchecked(s.spec.exitFailure);}
    }
    public static void nextStage(Object renderer,EntityStub entity,String text,double x,double y,double z,int distance)throws Throwable {
        State s=RUN.get();s.events.add("next");
        if(s.spec.mode==Mode.NEXT_THROW||s.spec.mode==Mode.NEXT_AND_EXIT_THROW)throw s.spec.nextFailure;
        if(s.spec.mode==Mode.MUTATE){s.fixture.enabled.setBoolean(null,false);entity=s.spec.child;text=MUTATED;x=Double.longBitsToDouble(0x7ff8000000000730L);y=-0.0;z=Double.POSITIVE_INFINITY;distance=Integer.MIN_VALUE;}
        s.calls.add(new Call(entity,text,Double.doubleToRawLongBits(x),Double.doubleToRawLongBits(y),Double.doubleToRawLongBits(z),distance,entity==null?-1:entity.revision));
        if(s.inside&&s.spec.mode==Mode.REENTER_THROW)throw s.spec.nextFailure;
        if(!s.inside&&(s.spec.mode==Mode.REENTER_WRONG||s.spec.mode==Mode.REENTER_OWNED||s.spec.mode==Mode.REENTER_THROW||s.spec.mode==Mode.NULL_SCOPE)){
            s.inside=true;Object previous=null;boolean scope=s.spec.mode!=Mode.REENTER_WRONG;
            if(scope)previous=s.fixture.enter(s.spec.mode==Mode.NULL_SCOPE?null:s.spec.child);
            try{if(s.spec.mode==Mode.NULL_SCOPE)assertTrue(s.fixture.active(s.input.entity));else s.fixture.invoke(new Input(s.spec.child,MUTATED,-0.0,3.5,4.5,-772));}
            finally{if(scope)s.fixture.exit(previous);s.inside=false;assertTrue(s.fixture.active(s.input.entity));}
        }
    }
    private static void throwUnchecked(Throwable failure){LowerNamePreparationFusionTest.<RuntimeException>raise(failure);}
    @SuppressWarnings("unchecked") private static <T extends Throwable>void raise(Throwable failure)throws T{throw(T)failure;}
    private static final class State {
        final Fixture fixture;final Input input;final Spec spec;final List<String> events=new ArrayList<>();final List<Call> calls=new ArrayList<>();int depth;boolean inside;
        State(Fixture fixture,Input input,Spec spec){this.fixture=fixture;this.input=input;this.spec=spec;}
    }

    private static final class Fixture {
        final Object renderer;final MethodHandle call,enter,exit,active;final Field enabled,sites,counter;
        Fixture(boolean transformed,boolean boot)throws Throwable {
            ClassNode node=fixture();if(transformed)assertEquals(1,LowerNamePreparationFusion.apply(node));
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new SimpleRemapper(ASM9,Map.of(BATCH,SELF+"$BatchStub"))));
            Map<String,byte[]> types=new HashMap<>();types.put(OWNER.replace('/','.'),writer.toByteArray());
            for(String name:List.of("LowerNamePreparation","HologramNamePreparation","HologramNamePreparation$Scope","RenderHookAllocationControl")){
                String binary="io.github.arcadiumclient.runtime.client.optimizations."+name;try(var stream=LowerNamePreparationFusionTest.class.getClassLoader().getResourceAsStream(binary.replace('.','/')+".class")){assertNotNull(stream,binary);types.put(binary,stream.readAllBytes());}
            }
            ClassLoader loader=new ClassLoader(LowerNamePreparationFusionTest.class.getClassLoader()){
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null&&types.containsKey(name)){byte[] b=types.get(name);c=defineClass(name,b,0,b.length);}if(c==null)c=super.loadClass(name,false);if(resolve)resolveClass(c);return c;}}
            };
            Class<?> control;
            synchronized(LowerNamePreparationFusionTest.class){String previous=System.getProperty(BOOT);try{System.setProperty(BOOT,Boolean.toString(boot));control=Class.forName(LowerNamePreparation.class.getName(),true,loader);}finally{if(previous==null)System.clearProperty(BOOT);else System.setProperty(BOOT,previous);}}
            Class<?> scope=Class.forName(HologramNamePreparation.class.getName(),true,loader),flags=Class.forName(RenderHookAllocationControl.class.getName(),true,loader),type=Class.forName(OWNER.replace('/','.'),true,loader);
            enabled=flags.getField("enabled");sites=control.getField("preparedSites");counter=control.getField("typedCalls");MethodHandles.Lookup lookup=MethodHandles.publicLookup();
            enter=lookup.findStatic(scope,"enter",MethodType.methodType(Object.class,Object.class));exit=lookup.findStatic(scope,"exit",MethodType.methodType(void.class,Object.class));active=lookup.findStatic(scope,"active",MethodType.methodType(boolean.class,Object.class));renderer=type.getConstructor().newInstance();
            call=lookup.findVirtual(type,"entry",MethodType.methodType(void.class,EntityStub.class,String.class,double.class,double.class,double.class,int.class)).asType(MethodType.methodType(void.class,Object.class,EntityStub.class,String.class,double.class,double.class,double.class,int.class));
        }
        void reset(boolean value,int count)throws Exception{enabled.setBoolean(null,value);sites.setInt(null,count);counter.setLong(null,0);}
        void invoke(Input i)throws Throwable{call.invokeExact(renderer,i.entity,i.text,i.x,i.y,i.z,i.distance);}
        Object enter(Object owner)throws Throwable{return(Object)enter.invokeExact(owner);}
        void exit(Object previous)throws Throwable{exit.invokeExact(previous);}
        boolean active(Object owner)throws Throwable{return(boolean)active.invokeExact(owner);}
        long typed()throws Exception{return counter.getLong(null);}
    }

    private static ClassNode fixture(){
        ClassNode node=new ClassNode(ASM9);node.version=V17;node.access=ACC_PUBLIC;node.name=OWNER;node.superName="java/lang/Object";
        MethodNode constructor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);constructor.instructions.add(new VarInsnNode(ALOAD,0));constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));constructor.instructions.add(new InsnNode(RETURN));node.methods.add(constructor);
        MethodNode wrapper=new MethodNode(ACC_PRIVATE,WRAPPER,DESC.substring(0,DESC.length()-2)+"L"+OP+";)V",null,null);wrapper.parameters=new ArrayList<>();for(String p:List.of("entity","text","x","y","z","maxDistance","original"))wrapper.parameters.add(new ParameterNode(p,0));
        AnnotationNode annotation=new AnnotationNode(MERGED);annotation.values=new ArrayList<>(List.of("mixin",MIXIN,"priority",1000));wrapper.visibleAnnotations=new ArrayList<>(List.of(annotation));
        InsnList code=wrapper.instructions;LabelNode batched=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),handlerEnd=new LabelNode(),done=new LabelNode();
        code.add(new MethodInsnNode(INVOKESTATIC,BATCH,"active","()L"+BATCH+";",false));code.add(new VarInsnNode(ASTORE,11));code.add(new VarInsnNode(ALOAD,11));code.add(new JumpInsnNode(IFNONNULL,batched));pack(code);code.add(new InsnNode(RETURN));
        code.add(batched);code.add(new VarInsnNode(ALOAD,11));code.add(new MethodInsnNode(INVOKEVIRTUAL,BATCH,"enter","()V",false));code.add(start);pack(code);code.add(end);code.add(new VarInsnNode(ALOAD,11));code.add(new MethodInsnNode(INVOKEVIRTUAL,BATCH,"exit","()V",false));code.add(new JumpInsnNode(GOTO,done));
        code.add(handler);code.add(new VarInsnNode(ASTORE,12));code.add(handlerEnd);code.add(new VarInsnNode(ALOAD,11));code.add(new MethodInsnNode(INVOKEVIRTUAL,BATCH,"exit","()V",false));code.add(new VarInsnNode(ALOAD,12));code.add(new InsnNode(ATHROW));code.add(done);code.add(new InsnNode(RETURN));
        wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));wrapper.tryCatchBlocks.add(new TryCatchBlockNode(handler,handlerEnd,handler,null));node.methods.add(wrapper);
        MethodNode bridge=new MethodNode(ACC_PRIVATE|ACC_SYNTHETIC,BRIDGE,"([Ljava/lang/Object;)Ljava/lang/Void;",null,null);code=bridge.instructions;code.add(new VarInsnNode(ALOAD,1));code.add(new IntInsnNode(BIPUSH,6));code.add(new LdcInsnNode("["+ENTITY.replace('/','.')+", java.lang.String, double, double, double, int]"));code.add(new MethodInsnNode(INVOKESTATIC,"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime","checkArgumentCount","([Ljava/lang/Object;ILjava/lang/String;)V",false));code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,1));
        for(int i=0;i<6;i++){code.add(new InsnNode(DUP));code.add(new IntInsnNode(BIPUSH,i));code.add(new InsnNode(AALOAD));String type=i==0?ENTITY:i==1?"java/lang/String":i==5?"java/lang/Integer":"java/lang/Double";code.add(new TypeInsnNode(CHECKCAST,type));if(i>=2)code.add(new MethodInsnNode(INVOKEVIRTUAL,type,i==5?"intValue":"doubleValue",i==5?"()I":"()D",false));if(i>=2&&i<=4){code.add(new InsnNode(DUP2_X1));code.add(new InsnNode(POP2));}else code.add(new InsnNode(SWAP));}
        code.add(new InsnNode(POP));code.add(new MethodInsnNode(INVOKESPECIAL,OWNER,NEXT,DESC,false));code.add(new InsnNode(ACONST_NULL));code.add(new TypeInsnNode(CHECKCAST,"java/lang/Void"));code.add(new InsnNode(ARETURN));node.methods.add(bridge);
        MethodNode next=new MethodNode(ACC_PRIVATE,NEXT,DESC,null,null);loads(next.instructions);next.instructions.add(new MethodInsnNode(INVOKESTATIC,SELF,"nextStage","(Ljava/lang/Object;"+DESC.substring(1),false));next.instructions.add(new InsnNode(RETURN));node.methods.add(next);
        MethodNode entry=new MethodNode(ACC_PUBLIC,"entry",DESC,null,null);loads(entry.instructions);entry.instructions.add(new VarInsnNode(ALOAD,0));entry.instructions.add(new InvokeDynamicInsnNode("call","(L"+OWNER+";)L"+OP+";",META,Type.getType("([Ljava/lang/Object;)Ljava/lang/Object;"),new Handle(H_INVOKESPECIAL,OWNER,BRIDGE,bridge.desc,false),Type.getType(bridge.desc)));entry.instructions.add(new MethodInsnNode(INVOKESPECIAL,OWNER,WRAPPER,wrapper.desc,false));entry.instructions.add(new InsnNode(RETURN));node.methods.add(entry);
        return read(bytesFrames(node));
    }
    private static void loads(InsnList c){c.add(new VarInsnNode(ALOAD,0));c.add(new VarInsnNode(ALOAD,1));c.add(new VarInsnNode(ALOAD,2));c.add(new VarInsnNode(DLOAD,3));c.add(new VarInsnNode(DLOAD,5));c.add(new VarInsnNode(DLOAD,7));c.add(new VarInsnNode(ILOAD,9));}
    private static void pack(InsnList c){c.add(new VarInsnNode(ALOAD,10));c.add(new IntInsnNode(BIPUSH,6));c.add(new TypeInsnNode(ANEWARRAY,"java/lang/Object"));for(int i=0;i<6;i++){c.add(new InsnNode(DUP));c.add(new InsnNode(ICONST_0+i));c.add(new VarInsnNode(i<2?ALOAD:i==5?ILOAD:DLOAD,i<2?i+1:i==5?9:i*2-1));if(i>=2)c.add(new MethodInsnNode(INVOKESTATIC,i==5?"java/lang/Integer":"java/lang/Double","valueOf",i==5?"(I)Ljava/lang/Integer;":"(D)Ljava/lang/Double;",false));c.add(new InsnNode(AASTORE));}c.add(new MethodInsnNode(INVOKEINTERFACE,OP,"call","([Ljava/lang/Object;)Ljava/lang/Object;",true));c.add(new InsnNode(POP));}
    private static void reject(String name,Consumer<ClassNode> mutation){ClassNode node=fixture();mutation.accept(node);byte[] before=bytes(node);assertEquals(0,LowerNamePreparationFusion.apply(node),name);assertArrayEquals(before,bytes(node),name);negativeCases++;}
    private static AnnotationNode annotation(MethodNode m){return m.visibleAnnotations.stream().filter(a->a.desc.equals(MERGED)).findFirst().orElseThrow();}
    private static InvokeDynamicInsnNode dynamic(ClassNode n){return code(find(n,"entry")).stream().filter(i->i instanceof InvokeDynamicInsnNode).map(i->(InvokeDynamicInsnNode)i).findFirst().orElseThrow();}
    private static MethodNode find(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> code(MethodNode m){List<AbstractInsnNode> c=new ArrayList<>();for(AbstractInsnNode i:m.instructions)if(i.getOpcode()>=0)c.add(i);return c;}
    private static MethodNode copy(MethodNode m){MethodNode n=new MethodNode(ASM9,m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(n);return n;}
    private static ClassNode copy(ClassNode n){return read(bytes(n));}
    private static String trace(MethodNode m){MethodNode n=copy(m);for(AbstractInsnNode i:n.instructions.toArray())if(i instanceof FrameNode||i instanceof LineNumberNode)n.instructions.remove(i);Textifier p=new Textifier();n.accept(new TraceMethodVisitor(p));return p.getText().toString();}
    private static byte[] bytesFrames(ClassNode n){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){return"java/lang/Object";}};n.accept(w);return w.toByteArray();}
    private static byte[] bytes(ClassNode n){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
    private static ClassNode read(byte[] b){ClassNode n=new ClassNode(ASM9);new ClassReader(b).accept(n,ClassReader.EXPAND_FRAMES);return n;}

    public static void main(String[] args)throws Throwable {
        LowerNamePreparationFusionTest test=new LowerNamePreparationFusionTest();test.syntheticWrapperMatchesTheCapturedFingerprintAndOnlyDispatchChanges();test.bothWrapperBranchesPreserveRawBitsReferencesAndNullableArguments();test.callbackMutationReentryAndEveryFinallyOutcomeMatchOriginal();test.bootFlagsSiteCountsOwnerIdentityAndLiveRevisionAreCheckedPerCall();test.anotherThreadCannotBorrowTheActiveOwner();test.unknownBodiesBridgesMetadataAndPartialSitesRemainUnchanged();
        String actual="not supplied",sha="";if(args.length>0){byte[] captured=Files.readAllBytes(Path.of(args[0]));actual=LowerNamePreparationFusion.audit(read(captured));assertEquals(PINNED,actual);sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(captured));}
        String report="{\"passed\":true,\"tests\":6,\"differentialCases\":"+differentialCases+",\"unknownPatternsRejectedAtomically\":"+negativeCases+",\"syntheticFingerprint\":\""+PINNED+"\",\"capturedFingerprint\":\""+actual+"\",\"capturedClassSha256\":\""+sha+"\",\"bootIsolatedByClassloader\":true,\"actualGameOrGlRun\":false,\"runtimeIntegrationQualified\":false,\"performanceClaim\":false}";
        if(args.length>1)Files.writeString(Path.of(args[1]),report+"\n");System.out.println(report);
    }
}
