// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.llamalad7.mixinextras.utils.MixinInternals;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.*;

public final class RenderHookLateExtension implements IExtension {
    private record Task(String kind,ToIntFunction<ClassNode> processor,IntConsumer counter) { }
    private static final RenderHookLateExtension INSTANCE=new RenderHookLateExtension();
    private static final Map<ClassNode,Task> pending=new IdentityHashMap<>();
    private static boolean registered;
    private RenderHookLateExtension() { }
    public static void arm(ClassNode target,String kind,ToIntFunction<ClassNode> processor,IntConsumer counter){
        boolean known=switch(kind){
            case "particle" -> target.name.equals("net/minecraft/client/ParticleManager")||target.name.equals("net/minecraft/unmapped/C_16190149");
            case "layer" -> target.name.equals("net/minecraft/client/render/entity/LivingEntityRenderer")||target.name.equals("net/minecraft/unmapped/C_71252617");
            default -> false;
        };
        if(!known)return;
        try{
            if(!registered){
                List<IExtension> active=MixinInternals.getExtensions().getActiveExtensions();
                boolean injections=false,wrappers=false;
                for(IExtension extension:active){
                    String name=extension.getClass().getName();
                    injections|=name.equals("com.llamalad7.mixinextras.injector.LateInjectionApplicatorExtension");
                    wrappers|=name.equals("com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodApplicatorExtension");
                }
                if(!injections||!wrappers)throw new IllegalStateException("The known MixinExtras late stages are unavailable");
                MixinInternals.registerExtension(INSTANCE);registered=true;
            }
            pending.putIfAbsent(target,new Task(kind,Objects.requireNonNull(processor),Objects.requireNonNull(counter)));
        }catch(RuntimeException|LinkageError unsupported){LogManager.getLogger("Arcadium/Render Hooks").warn("Keeping original {} operations; late stage unavailable",kind,unsupported);}
    }
    @Override public boolean checkActive(MixinEnvironment environment){return true;}
    @Override public void preApply(ITargetClassContext context) { }
    @Override public void postApply(ITargetClassContext context){
        ClassNode target=context.getClassNode();Task task=pending.remove(target);if(task==null)return;
        ClassNode original=new ClassNode(Opcodes.ASM9);target.accept(original);
        try{
            int sites=task.processor.applyAsInt(target);
            int names=0,holograms=0,cera=0;
            if(task.kind.equals("layer")){
                names=SarcioNameResetFusion.apply(target);
                holograms=HologramNamePreparationFusion.apply(target).size();
                cera=CeraLivingOperationFusion.apply(target).size();
            }
            if(task.kind.equals("layer")){
                RenderHookAllocationControl.nameSites+=names+holograms;
                HologramNamePreparation.preparedSites=holograms;
                sites+=cera;
            }
            task.counter.accept(sites);
            LogManager.getLogger("Arcadium/Render Hooks").info("Prepared {} typed {} gates after MixinExtras",sites,task.kind);
        }catch(RuntimeException unsupported){target.methods=original.methods;LogManager.getLogger("Arcadium/Render Hooks").warn("Keeping original {} operations",task.kind,unsupported);}
    }
    @Override public void export(MixinEnvironment environment,String name,boolean force,ClassNode target) { }
}
