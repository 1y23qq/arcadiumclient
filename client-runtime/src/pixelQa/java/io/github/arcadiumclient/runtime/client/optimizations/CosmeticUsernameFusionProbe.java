// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class CosmeticUsernameFusionProbe implements Opcodes {
    private static ClassNode read(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
    public static void main(String[] args)throws Exception {
        byte[] original;try(var zip=new ZipFile(args[0])){original=zip.getInputStream(zip.getEntry("dev/rdh/cera/modules/OptifineCosmetics.class")).readAllBytes();}
        ClassNode target=read(original);require(CosmeticUsernameFusion.apply(target).size()==2,"Installed Cera did not receive exactly two predicate sites");
        int verified=0,calls=0;for(MethodNode method:target.methods){if(method.instructions.size()>0){new Analyzer<>(new BasicVerifier()).analyze(target.name,method);verified++;}for(AbstractInsnNode node:method.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals(Type.getInternalName(CosmeticUsernamePredicate.class)))calls++;}
        require(calls==2,"Unexpected cosmetic predicate call count");byte[] once=bytes(target);require(CosmeticUsernameFusion.apply(target).isEmpty()&&Arrays.equals(once,bytes(target)),"Repeated cosmetic fusion changed the class");
        for(int mode=0;mode<9;mode++){
            ClassNode unknown=read(original);MethodNode cape=unknown.methods.stream().filter(method->method.name.equals("cape")).findFirst().orElseThrow();MethodNode cosmetics=unknown.methods.stream().filter(method->method.name.equals("cosmetics")).findFirst().orElseThrow();
            if(mode==0)unknown.name="another/OptifineCosmetics";
            if(mode==1)cape.instructions.insert(new InsnNode(NOP));
            if(mode==2)unknown.methods.remove(cosmetics);
            if(mode==3)for(AbstractInsnNode node:cape.instructions)if(node instanceof FieldInsnNode field&&field.name.equals("USERNAME")){field.name="otherPattern";break;}
            if(mode==4)for(AbstractInsnNode node:cosmetics.instructions)if(node instanceof InvokeDynamicInsnNode dynamic){dynamic.name="unknownLoader";break;}
            if(mode==5)for(AbstractInsnNode node:cape.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals("java/util/regex/Matcher")){cape.instructions.insertBefore(call,new LabelNode());break;}
            if(mode==6)for(AbstractInsnNode node:cape.instructions)if(node instanceof FieldInsnNode field&&field.name.equals("optifineCosmetics")){field.name="changedConfig";break;}
            if(mode==7){LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();cape.instructions.insert(start);cape.instructions.add(end);cape.instructions.add(handler);cape.instructions.add(new InsnNode(ATHROW));cape.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));}
            if(mode==8){MethodNode duplicate=new MethodNode(ASM9,cosmetics.access,cosmetics.name,cosmetics.desc,null,null);cosmetics.accept(duplicate);unknown.methods.add(duplicate);}
            byte[] before=bytes(unknown);boolean rejected;try{rejected=CosmeticUsernameFusion.apply(unknown).isEmpty();}catch(IllegalArgumentException unsupported){rejected=true;}
            require(rejected&&Arrays.equals(before,bytes(unknown)),"Unknown cosmetic body changed instead of falling back: "+mode);
        }
        System.out.println("Cosmetic username fusion:2 installed sites, "+verified+" verified methods,9 unchanged body/CFG fallbacks and idempotence passed");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
