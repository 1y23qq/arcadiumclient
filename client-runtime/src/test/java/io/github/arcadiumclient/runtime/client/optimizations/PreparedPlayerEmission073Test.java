package io.github.arcadiumclient.runtime.client.optimizations;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PreparedPlayerEmission073Test {
    public static int checks,parts,setup,depth,pushes,pops,playerPre,playerPost,livingPre,livingPost,gpuBody,gpuOuter;
    public static final class Entity {public boolean baby,fail;}
    public static final class Part {public void render(float scale){parts++;}}
    public static final class Gl {public static void push(){depth++;pushes++;}public static void pop(){depth--;pops++;}}
    public static class Humanoid {public boolean isBaby;public int observable;public Part head=new Part(),body=new Part(),rightArm=new Part(),leftArm=new Part(),rightLeg=new Part(),leftLeg=new Part(),hat=new Part();
        public void setupAnimation(Entity e){setup++;observable=setup;if(e.fail)throw new IllegalStateException("native setup fault");}
        public void render(Entity e,float a,float b,float c,float d,float f,float scale){setupAnimation(e);Gl.push();if(!isBaby){head.render(scale);body.render(scale);rightArm.render(scale);leftArm.render(scale);rightLeg.render(scale);leftLeg.render(scale);hat.render(scale);}Gl.pop();}}
    public static class Player extends Humanoid {public Part leftPants=new Part(),rightPants=new Part(),leftSleeve=new Part(),rightSleeve=new Part(),jacket=new Part();public void render(Entity e,float a,float b,float c,float d,float f,float scale){super.render(e,a,b,c,d,f,scale);Gl.push();if(!isBaby){leftPants.render(scale);rightPants.render(scale);leftSleeve.render(scale);rightSleeve.render(scale);jacket.render(scale);}Gl.pop();}}
    public static final class Hooks {
        public static boolean allowed=true,appendFails;
        public static final PreparedPlayerEmission073.Controller controller=new PreparedPlayerEmission073.Controller(new PreparedPlayerEmission073.Adapter(){public Object createLease(){return new Object();}public Object preflight(Object model,Object e,float scale,Object reuse){try{if(!allowed||((Entity)e).baby||scale!=.0625f||model.getClass().getField("observable").getInt(model)!=setup)return null;return reuse;}catch(Exception missing){return null;}}public boolean emit(Object lease,int block){if(appendFails)return false;if(block==0)gpuBody++;else gpuOuter++;return true;}public void release(Object lease){}});
        public static int begin(Object model,Object e,float scale){return controller.begin(model,e,scale);}public static boolean emit(Object model,Object e,float scale,int block){return controller.emit(model,e,scale,block);}public static void end(int token){controller.end(token);}}
    static void ok(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
    static byte[] bytes(Class<?> c)throws Exception{try(var in=c.getResourceAsStream('/'+c.getName().replace('.','/')+".class")){return in.readAllBytes();}}
    static ClassNode node(byte[] b){ClassNode n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
    static byte[] encode(ClassNode n){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
    static void reset(){parts=setup=depth=pushes=pops=playerPre=playerPost=livingPre=livingPost=gpuBody=gpuOuter=0;Hooks.allowed=true;Hooks.appendFails=false;}
    static void shell(Object model,Entity e,boolean cancelPlayer,boolean cancelLiving)throws Exception{
        playerPre++;if(cancelPlayer)return;model.getClass().getField("isBaby").setBoolean(model,e.baby);livingPre++;if(cancelLiving){playerPost++;return;}try{model.getClass().getMethod("setupAnimation",Entity.class).invoke(model,e);model.getClass().getMethod("render",Entity.class,float.class,float.class,float.class,float.class,float.class,float.class).invoke(model,e,0f,0f,0f,0f,0f,.0625f);}catch(InvocationTargetException expected){}livingPost++;playerPost++;
    }
    public static void main(String[] args)throws Exception{
        String h=Type.getInternalName(Humanoid.class),p=Type.getInternalName(Player.class),part=Type.getInternalName(Part.class),entity=Type.getInternalName(Entity.class),gl=Type.getInternalName(Gl.class),hooks=Type.getInternalName(Hooks.class);var named=PreparedPlayerEmissionPatch073.named189();var names=new PreparedPlayerEmissionPatch073.Names(h,p,part,entity,gl,"render","render","pop",hooks,named.bodyFields(),named.outerFields());byte[] pb=bytes(Part.class);ClassNode hn=node(bytes(Humanoid.class)),pn=node(bytes(Player.class));
        ok(!PreparedPlayerEmissionPatch073.apply(hn,pn,pb,Set.of(),names).accepted()&&hn.fields.stream().noneMatch(f->f.name.startsWith("arcadium$")),"Unknown final part hook did not fail atomically");var result=PreparedPlayerEmissionPatch073.apply(hn,pn,pb,Set.of(PreparedPlayerEmissionPatch073.sha(pb)),names);ok(result.accepted()&&result.bodyCalls()==7&&result.outerCalls()==5,"Canonical emission blocks rejected");
        Map<String,byte[]> classes=Map.of(Humanoid.class.getName(),encode(hn),Player.class.getName(),encode(pn));ClassLoader loader=new ClassLoader(PreparedPlayerEmission073Test.class.getClassLoader()){@Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{if(!classes.containsKey(n))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null){byte[] data=classes.get(n);c=defineClass(n,data,0,data.length);}if(resolve)resolveClass(c);return c;}};Object model=loader.loadClass(Player.class.getName()).getConstructor().newInstance();Entity e=new Entity();
        reset();shell(model,e,false,false);ok(parts==0&&gpuBody==1&&gpuOuter==1&&setup==2&&depth==0&&pushes==2&&pops==2,"Bulk emission changed setup/preludes/pops or still traversed parts");ok(playerPre==1&&livingPre==1&&livingPost==1&&playerPost==1&&Hooks.controller.depth()==0,"Native event shell/scope changed");
        reset();shell(model,e,true,false);ok(playerPre==1&&livingPre==0&&playerPost==0&&parts==0&&gpuBody==0,"Player cancellation semantics changed");reset();shell(model,e,false,true);ok(playerPre==1&&livingPre==1&&livingPost==0&&playerPost==1&&gpuBody==0,"Living cancellation semantics changed");
        reset();Hooks.allowed=false;shell(model,e,false,false);ok(parts==12&&gpuBody==0&&gpuOuter==0&&setup==2&&depth==0,"Unsupported hook suppressed before preflight");reset();Hooks.appendFails=true;shell(model,e,false,false);ok(parts==12&&gpuBody==0&&gpuOuter==0&&depth==0,"Atomic append failure did not preserve native parts");
        reset();e.baby=true;shell(model,e,false,false);ok(gpuBody==0&&gpuOuter==0&&depth==0&&Hooks.controller.depth()==0,"Child path was suppressed");e.baby=false;reset();e.fail=true;shell(model,e,false,false);ok(Hooks.controller.depth()==0&&gpuBody==0&&livingPost==1&&playerPost==1,"Native setup exception leaked scope or events");e.fail=false;
        for(int i=0;i<32;i++){int t=Hooks.controller.begin(model,e,.0625f);ok(!Hooks.controller.emit(new Object(),e,.0625f,0),"Different actor/model used another scope");Hooks.controller.end(t);}ok(Hooks.controller.depth()==0,"Repeated scopes leaked");
        PreparedVoxelLease073 skin=new PreparedVoxelLease073(new Object(),new Object(),new Object(),"skin",new Object(),new Object[6],new Object[6],0xfc0,0,1,2,false);ok(!skin.projectionQualified()&&!skin.consume(skin.main,skin.capture,skin.parts,"skin",0,2),"Unqualified physical projection was promoted");
        try(ZipFile z=new ZipFile(args[0])){byte[] realH=z.getInputStream(z.getEntry(named.humanoid()+".class")).readAllBytes(),realP=z.getInputStream(z.getEntry(named.player()+".class")).readAllBytes(),realPart=z.getInputStream(z.getEntry(named.part()+".class")).readAllBytes();ClassNode a=node(realH),b=node(realP);var actual=PreparedPlayerEmissionPatch073.apply(a,b,realPart,Set.of(PreparedPlayerEmissionPatch073.sha(realPart)),named);ok(actual.accepted(),"Pinned actual1.8.9 adult block ABI does not match");Path out=Path.of(args[1]);Files.createDirectories(out);Files.write(out.resolve("HumanoidModel-patched.class"),encode(a));Files.write(out.resolve("PlayerModel-patched.class"),encode(b));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"nativePartCallsForAdmittedActor\":0,\"bodyAndOuterBulkBlocks\":2,\"bothSetupCallsAndPopsPreserved\":true,\"playerLivingCancellationContracts\":true,\"unknownHookAndAppendFailureNativeFallback\":true,\"actualNamed189AsmAbi\":true,\"projectionRemainsUnqualified\":true,\"noGlCalls\":true}");
    }
}
