// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class ParticleOperationFusionProbe implements Opcodes {
    private static ClassNode read(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
    public static void main(String[] args)throws Exception {
        byte[] original=Files.readAllBytes(Path.of(args[0]));ClassNode target=read(original);
        require(ParticleOperationFusion.apply(target).size()==2,"The published wrapper chain was not fused");
        int analyzed=0;
        for(MethodNode method:target.methods){
            if(method.instructions.size()>0){new Analyzer<>(new BasicVerifier()).analyze(target.name,method);analyzed++;}
            if(method.name.startsWith("arcadium$typedParticle"))for(AbstractInsnNode node:method.instructions){
                require(!(node instanceof InvokeDynamicInsnNode),"Typed helper retained a lambda");
                require(!(node instanceof TypeInsnNode type&&type.getOpcode()==ANEWARRAY),"Typed helper retained an argument array");
                require(!(node instanceof MethodInsnNode call&&(call.owner.endsWith("/Operation")||call.owner.equals("java/lang/Float")&&call.name.equals("valueOf"))),"Typed helper retained operation boxing");
            }
        }
        byte[] once=bytes(target);require(ParticleOperationFusion.apply(target).isEmpty()&&Arrays.equals(once,bytes(target)),"Repeated fusion changed the class");
        ClassNode pending=read(original);for(MethodNode method:pending.methods){method.maxLocals=0;method.maxStack=0;}
        require(ParticleOperationFusion.apply(pending).size()==2,"MixinExtras methods with pending stack/local maxima were rejected");
        for(int mode=0;mode<9;mode++){
            ClassNode unknown=read(original);
            MethodNode track=unknown.methods.stream().filter(method->method.name.endsWith("$updateRenderingEntityState")).findFirst().orElseThrow();
            MethodNode fade=unknown.methods.stream().filter(method->method.name.endsWith("$overflowparticle$fade")).findFirst().orElseThrow();
            if(mode==0)track.visibleAnnotations=null;
            if(mode==1)fade.instructions.insert(new InsnNode(NOP));
            if(mode==2)for(AbstractInsnNode node:track.instructions)if(node instanceof IntInsnNode number&&number.operand==9){number.operand=8;break;}
            if(mode==3){MethodNode bridge=unknown.methods.stream().filter(method->method.name.startsWith("mixinextras$bridge$wrapOperation")&&method.name.contains("$fade$")).findFirst().orElseThrow();bridge.instructions.insert(new InsnNode(NOP));}
            if(mode==4){MethodNode entry=unknown.methods.stream().filter(method->method.name.contains("mixinextras$wrapped")&&method.instructions.iterator().hasNext()&&hasParticleOperation(method)).findFirst().orElseThrow();
                InvokeDynamicInsnNode operation=firstParticleOperation(entry);LabelNode foreign=new LabelNode();entry.instructions.insert(operation,foreign);entry.instructions.insertBefore(operation,new JumpInsnNode(GOTO,foreign));}
            if(mode==5){MethodNode entry=unknown.methods.stream().filter(ParticleOperationFusionProbe::hasParticleOperation).findFirst().orElseThrow();unknown.methods.remove(entry);}
            if(mode==6){MethodNode bridge=unknown.methods.stream().filter(method->method.name.startsWith("mixinextras$bridge$m_32469004")).findFirst().orElseThrow();for(AbstractInsnNode node:bridge.instructions)if(node instanceof MethodInsnNode call&&call.getOpcode()==INVOKEVIRTUAL){call.name="unknownCustomRender";break;}}
            if(mode==7){MethodNode entry=unknown.methods.stream().filter(ParticleOperationFusionProbe::hasParticleOperation).findFirst().orElseThrow();InvokeDynamicInsnNode operation=firstParticleOperation(entry);AbstractInsnNode store=operation.getNext();while(store.getOpcode()<0)store=store.getNext();int slot=((VarInsnNode)store).var;for(AbstractInsnNode node=store;node!=null;node=node.getNext())if(node instanceof MethodInsnNode call&&call.name.endsWith("$updateRenderingEntityState")){InsnList extra=new InsnList();extra.add(new VarInsnNode(ALOAD,slot));extra.add(new InsnNode(POP));entry.instructions.insert(call,extra);break;}}
            if(mode==8){MethodNode entry=unknown.methods.stream().filter(ParticleOperationFusionProbe::hasParticleOperation).findFirst().orElseThrow();InvokeDynamicInsnNode operation=firstParticleOperation(entry);AbstractInsnNode preload=operation;for(int i=0;i<4;){preload=preload.getPrevious();if(preload.getOpcode()>=0)i++;}LabelNode foreign=new LabelNode();entry.instructions.insertBefore(preload,foreign);entry.instructions.insertBefore(preload,new JumpInsnNode(GOTO,foreign));}
            byte[] before=bytes(unknown);boolean rejected;
            try{rejected=ParticleOperationFusion.apply(unknown).isEmpty();}catch(IllegalArgumentException unsupported){rejected=true;}
            require(rejected&&Arrays.equals(before,bytes(unknown)),"Unknown chain changed instead of falling back: "+mode);
        }
        System.out.println("Particle operation fusion: 2 published sites, "+analyzed+" verified methods, no typed lambda/array/Float boxing, 9 unchanged fallbacks and idempotence passed");
    }
    private static boolean hasParticleOperation(MethodNode method){return firstParticleOperation(method)!=null;}
    private static InvokeDynamicInsnNode firstParticleOperation(MethodNode method){for(AbstractInsnNode node:method.instructions)if(node instanceof InvokeDynamicInsnNode call&&call.bsmArgs.length>1&&call.bsmArgs[1] instanceof Handle handle&&handle.getName().startsWith("mixinextras$bridge$m_32469004"))return call;return null;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
