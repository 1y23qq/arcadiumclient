// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class CosmeticUsernameFusion implements Opcodes {
    private static final String OWNER="dev/rdh/cera/modules/OptifineCosmetics";
    private static final String PATTERN="java/util/regex/Pattern",MATCHER="java/util/regex/Matcher";
    private static final String CAPE_HASH="2135835610b681562047a83fd7172cebbbf681d4930985156f9e471b84a91a08";
    private static final String COSMETICS_HASH="f0681473c18fdefb1d7c58d78d1319b1c770949422cd8fcd498d23690f2cce28";
    private record Patch(MethodNode method,MethodInsnNode matcher,MethodInsnNode matches) { }
    public record Site(String method,String descriptor) { }
    private CosmeticUsernameFusion() { }
    public static List<Site> apply(ClassNode target){
        if(!target.name.equals(OWNER))return List.of();
        ClassNode work=new ClassNode(ASM9);target.accept(work);
        MethodNode cape=resolver(work,"cape","java/util/Optional",ACC_PUBLIC),cosmetics=resolver(work,"cosmetics","java/util/List",ACC_PRIVATE);
        if(cape==null||cosmetics==null||!Arrays.equals(Type.getArgumentTypes(cape.desc),Type.getArgumentTypes(cosmetics.desc)))return List.of();
        String player=Type.getArgumentTypes(cape.desc)[0].getInternalName();
        if(!player.equals("net/minecraft/client/entity/living/player/ClientPlayerEntity")&&!player.equals("net/minecraft/unmapped/C_23527099"))return List.of();
        if(!fingerprint(cape,player).equals(CAPE_HASH)||!fingerprint(cosmetics,player).equals(COSMETICS_HASH))return List.of();
        Patch first=site(cape),second=site(cosmetics);if(first==null||second==null)return List.of();
        for(Patch patch:List.of(first,second)){
            patch.method.instructions.set(patch.matcher,new MethodInsnNode(INVOKESTATIC,Type.getInternalName(CosmeticUsernamePredicate.class),"matches","(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Z",false));
            patch.method.instructions.remove(patch.matches);
        }
        target.methods=work.methods;return List.of(new Site(cape.name,cape.desc),new Site(cosmetics.name,cosmetics.desc));
    }
    public static Map<String,String> audit(ClassNode owner){
        MethodNode cape=resolver(owner,"cape","java/util/Optional",ACC_PUBLIC),cosmetics=resolver(owner,"cosmetics","java/util/List",ACC_PRIVATE);
        if(cape==null||cosmetics==null)return Map.of();String player=Type.getArgumentTypes(cape.desc)[0].getInternalName();
        return Map.of("cape",fingerprint(cape,player),"cosmetics",fingerprint(cosmetics,player));
    }
    private static MethodNode resolver(ClassNode owner,String name,String result,int access){
        List<MethodNode> candidates=owner.methods.stream().filter(method->method.name.equals(name)&&method.access==access&&Type.getReturnType(method.desc).equals(Type.getObjectType(result))&&Type.getArgumentTypes(method.desc).length==1&&Type.getArgumentTypes(method.desc)[0].getSort()==Type.OBJECT).toList();
        return candidates.size()==1?candidates.getFirst():null;
    }
    private static Patch site(MethodNode method){
        List<AbstractInsnNode> code=code(method);List<Patch> patches=new ArrayList<>();
        for(int i=2;i+1<code.size();i++)if(method(code.get(i),INVOKEVIRTUAL,PATTERN,"matcher","(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;")){
            if(!(code.get(i-2) instanceof FieldInsnNode field)||field.getOpcode()!=GETSTATIC||!field.owner.equals(OWNER)||!field.name.equals("USERNAME")||!field.desc.equals("Ljava/util/regex/Pattern;")||
                    !(code.get(i-1) instanceof VarInsnNode load)||load.getOpcode()!=ALOAD||!method(code.get(i+1),INVOKEVIRTUAL,MATCHER,"matches","()Z"))return null;
            for(AbstractInsnNode node=code.get(i-2);node!=code.get(i+1);node=node.getNext())if(node instanceof LabelNode)return null;
            patches.add(new Patch(method,(MethodInsnNode)code.get(i),(MethodInsnNode)code.get(i+1)));
        }
        return patches.size()==1?patches.getFirst():null;
    }
    private static String fingerprint(MethodNode method,String player){
        List<AbstractInsnNode> code=code(method);StringBuilder result=new StringBuilder(method.access+"|"+normalize(method.desc,player));
        for(AbstractInsnNode node:code){
            result.append('|').append(node.getOpcode());
            if(node instanceof VarInsnNode variable)result.append(':').append(variable.var);
            else if(node instanceof IntInsnNode integer)result.append(':').append(integer.operand);
            else if(node instanceof MethodInsnNode call){String name=call.name;if(call.owner.equals(player)&&(name.equals("getGameProfile")||name.equals("m_37763343")))name="@profile";result.append(':').append(normalize(call.owner,player)).append('.').append(name).append(normalize(call.desc,player)).append(':').append(call.itf);}
            else if(node instanceof FieldInsnNode field)result.append(':').append(normalize(field.owner,player)).append('.').append(field.name).append(normalize(field.desc,player));
            else if(node instanceof TypeInsnNode type)result.append(':').append(normalize(type.desc,player));
            else if(node instanceof JumpInsnNode jump)result.append(':').append(position(code,jump.label));
            else if(node instanceof LdcInsnNode constant)result.append(':').append(constant(constant.cst,player));
            else if(node instanceof InvokeDynamicInsnNode dynamic){result.append(':').append(dynamic.name).append(normalize(dynamic.desc,player)).append(':').append(constant(dynamic.bsm,player));for(Object argument:dynamic.bsmArgs)result.append(':').append(constant(argument,player));}
            else if(!(node instanceof InsnNode))throw new IllegalArgumentException("Unsupported cosmetic resolver instruction");
        }
        for(TryCatchBlockNode block:method.tryCatchBlocks)result.append("|catch:").append(position(code,block.start)).append(':').append(position(code,block.end)).append(':').append(position(code,block.handler)).append(':').append(block.type);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static String constant(Object value,String player){
        if(value instanceof Handle handle)return handle.getTag()+":"+normalize(handle.getOwner(),player)+"."+handle.getName()+normalize(handle.getDesc(),player)+":"+handle.isInterface();
        if(value instanceof Type type)return normalize(type.getDescriptor(),player);
        if(value instanceof String||value instanceof Number)return normalize(String.valueOf(value),player);
        throw new IllegalArgumentException("Unsupported cosmetic resolver constant");
    }
    private static String normalize(String value,String player){return value.replace(OWNER,"@owner").replace(player,"@player").replace(player.replace('/','.'),"@player").replace("net/minecraft/unmapped/C_22168528","@identifier").replace("net/minecraft/resource/Identifier","@identifier");}
    private static List<AbstractInsnNode> code(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode node:method.instructions)if(node.getOpcode()>=0)result.add(node);return result;}
    private static int position(List<AbstractInsnNode> code,AbstractInsnNode node){while(node!=null&&node.getOpcode()<0)node=node.getNext();return node==null?code.size():code.indexOf(node);}
    private static boolean method(AbstractInsnNode node,int opcode,String owner,String name,String descriptor){return node instanceof MethodInsnNode call&&call.getOpcode()==opcode&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor);}
}
