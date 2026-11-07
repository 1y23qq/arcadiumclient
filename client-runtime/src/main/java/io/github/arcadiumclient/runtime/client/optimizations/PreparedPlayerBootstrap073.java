package io.github.arcadiumclient.runtime.client.optimizations;

import com.llamalad7.mixinextras.utils.MixinInternals;
import java.nio.file.*;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.*;

public final class PreparedPlayerBootstrap073 implements IExtension {
    private record Approval(String method,String finalPartClass,String partStage){}
    // Populated only after reviewing an actual final-defined runtime export.
    private static final Map<String,Approval> APPROVED=Map.of();
    private static final PreparedPlayerBootstrap073 INSTANCE=new PreparedPlayerBootstrap073();
    private static final Map<ClassNode,Integer> pending=new IdentityHashMap<>();
    private static boolean registered;private static String observedPartStage;
    public static volatile int inspected,patched;public static volatile String state="Private prepared player route is off";
    private PreparedPlayerBootstrap073(){}
    public static int approvalCount(){return APPROVED.size();}
    public static void arm(ClassNode node,int block){
        if(!PreparedPlayerMode073.ENABLED)return;
        try{if(!registered){boolean injections=false,wrappers=false;for(IExtension extension:MixinInternals.getExtensions().getActiveExtensions()){String name=extension.getClass().getName();injections|=name.equals("com.llamalad7.mixinextras.injector.LateInjectionApplicatorExtension");wrappers|=name.equals("com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodApplicatorExtension");}if(!injections||!wrappers)throw new IllegalStateException("Known late Mixin stages unavailable");MixinInternals.registerExtension(INSTANCE);registered=true;}pending.put(node,block);state="Awaiting final transformed fingerprint approval";}catch(RuntimeException|LinkageError unsupported){state="Late model export unavailable; native parts retained";LogManager.getLogger("Arcadium/Prepared Player").warn(state,unsupported);}
    }
    @Override public boolean checkActive(MixinEnvironment environment){return PreparedPlayerMode073.ENABLED;}
    @Override public void preApply(ITargetClassContext context){}
    @Override public void postApply(ITargetClassContext context){
        ClassNode node=context.getClassNode();Integer block=pending.remove(node);
        if(block==null){if(node.name.equals("net/minecraft/client/render/model/ModelPart")||node.name.equals("net/minecraft/unmapped/C_94760802")){observedPartStage=stageHash(node);dump(node,"part",observedPartStage,"Post-Mixin candidate, NOT final-defined proof");}return;}
        inspected++;var inspection=PreparedPlayerEmissionPatch073.inspect(node,block);String method=inspection==null?"unknown":inspection.methodFingerprint();dump(node,block==0?"humanoid":"player",method,"Post-Mixin candidate, NOT final-defined proof");Approval approval=APPROVED.get(node.name);
        if(approval==null||!approval.partStage.equals(observedPartStage)){state="Unapproved actual transformed fingerprint; native parts retained";return;}
        ClassNode backup=new ClassNode();node.accept(backup);
        try{var result=PreparedPlayerEmissionPatch073.applyOne(node,block,approval.method,approval.finalPartClass);if(!result.accepted()){state=result.reason();return;}recompute(node);patched++;state="Private exact prepared block installed; native proof pending";}
        catch(RuntimeException|LinkageError unsupported){node.methods=backup.methods;node.fields=backup.fields;state="Frame/shape refusal; native parts retained";}
    }
    private static void recompute(ClassNode node){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){ClassInfo common=ClassInfo.getCommonSuperClassOrInterface(a,b);if(common==null)throw new IllegalStateException("Unknown bytecode hierarchy");return common.getName();}};node.accept(writer);ClassNode ready=new ClassNode();new ClassReader(writer.toByteArray()).accept(ready,ClassReader.EXPAND_FRAMES);node.methods=ready.methods;node.fields=ready.fields;}
    private static String stageHash(ClassNode node){ClassWriter writer=new ClassWriter(0);node.accept(writer);return PreparedPlayerEmissionPatch073.sha(writer.toByteArray());}
    private static void dump(ClassNode node,String kind,String method,String note){String directory=System.getProperty("arcadium.preparedPlayer073.exportDirectory","");if(directory.isBlank())return;try{Path root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);String base=node.name.replace('/','_')+"-postMixin";ClassWriter writer=new ClassWriter(0);node.accept(writer);byte[] bytes=writer.toByteArray();Files.write(root.resolve(base+".class"),bytes,StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve(base+".properties"),"kind="+kind+"\nclass="+node.name+"\nsha256="+PreparedPlayerEmissionPatch073.sha(bytes)+"\nmethod="+method+"\nfinalDefined=false\napproved="+APPROVED.containsKey(node.name)+"\nnote="+note+"\n",StandardOpenOption.CREATE_NEW);}catch(Exception unavailable){state="Diagnostic export unavailable; native admission unchanged";}}
    @Override public void export(MixinEnvironment environment,String name,boolean force,ClassNode target){}
}
