// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.*;

public final class ParticleOperationFusion implements Opcodes {
    private static final String OP="com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String TRACK="org.polyfrost.overflowparticles.mixin.client.Mixin_TrackRenderState";
    private static final String FADE="org.polyfrost.overflowparticles.mixin.client.Mixin_ParticleFading";
    private static final String CONTROL=Type.getInternalName(RenderHookAllocationControl.class);
    private static final String TRACK_HASH="99398b45b7b8d950c1b08ab72007c49b92629c782d2c4341edb6f394127dc87b";
    private static final String FADE_HASH="001d561a03e4e3c5b02aee308697210291510c60522e37d0efb88bee90957683";
    private static final String BASE_HASH="1ca2b177e93a561a87e7eccf03ff8c9725c81bf0c9c78ffe420f6529e18e9b84";
    private static final String BRIDGE_HASH="62876fad9c3ab2e2fecf3a1af014f7a3948626ba43e6de6dfd662f0e15205874";
    private static final String TYPED_TRACK="arcadium$typedParticleState",TYPED_FADE="arcadium$typedParticleFade";
    public record Site(String method,String descriptor) { }
    private record Context(String owner,Type[] arguments,String particleType,MethodNode track,MethodNode fade,String typedDescriptor) { }
    private record Pack(AbstractInsnNode first,AbstractInsnNode last) { }
    private record Frame(Object[] locals,Object[] stack) { }
    private record Patch(MethodNode method,InvokeDynamicInsnNode first,MethodInsnNode last,Frame frame) { }
    private ParticleOperationFusion() { }

    public static List<Site> apply(ClassNode target){
        if(target.methods.stream().anyMatch(method->method.name.equals(TYPED_TRACK)||method.name.equals(TYPED_FADE)))return List.of();
        ClassNode work=expanded(target);Context context=context(work);
        if(context==null||!fingerprint(context.track,context).equals(TRACK_HASH)||!fingerprint(context.fade,context).equals(FADE_HASH))return List.of();
        List<Patch> patches=new ArrayList<>();MethodInsnNode render=null;
        for(MethodNode method:work.methods){
            List<AbstractInsnNode> code=code(method);
            for(int i=0;i<code.size();i++)if(code.get(i) instanceof InvokeDynamicInsnNode base&&base.desc.equals("()L"+OP+";")){
                Patch patch=site(work,context,method,code,i);
                if(patch!=null){
                    patches.add(patch);
                    MethodNode bridge=implementation(work,base);
                    MethodInsnNode call=code(bridge).stream().filter(node->node instanceof MethodInsnNode m&&m.owner.equals(context.arguments[0].getInternalName())&&m.getOpcode()==INVOKEVIRTUAL).map(node->(MethodInsnNode)node).findFirst().orElseThrow();
                    if(render!=null&&(!render.owner.equals(call.owner)||!render.name.equals(call.name)||!render.desc.equals(call.desc)))return List.of();
                    render=call;
                }
            }
        }
        if(patches.size()!=2||render==null)return List.of();
        MethodNode typedFade=typed(context.fade,TYPED_FADE,context,render,null);
        MethodNode typedTrack=typed(context.track,TYPED_TRACK,context,null,TYPED_FADE);
        if(typedFade==null||typedTrack==null)return List.of();
        for(Patch patch:patches){
            LabelNode slow=new LabelNode(),done=new LabelNode();InsnList fast=new InsnList();
            fast.add(new FieldInsnNode(GETSTATIC,CONTROL,"enabled","Z"));fast.add(new JumpInsnNode(IFEQ,slow));
            fast.add(new MethodInsnNode(INVOKESPECIAL,work.name,TYPED_TRACK,context.typedDescriptor,false));
            fast.add(new JumpInsnNode(GOTO,done));fast.add(slow);fast.add(frame(patch.frame.locals,patch.frame.stack));
            patch.method.instructions.insertBefore(patch.first,fast);
            InsnList finish=new InsnList();finish.add(done);finish.add(frame(patch.frame.locals,new Object[0]));
            patch.method.instructions.insert(patch.last,finish);patch.method.maxStack=Math.max(12,patch.method.maxStack);
        }
        work.methods.add(typedFade);work.methods.add(typedTrack);target.methods=work.methods;
        return patches.stream().map(patch->new Site(patch.method.name,patch.method.desc)).toList();
    }

    public static Map<String,String> audit(ClassNode target){
        ClassNode work=expanded(target);Context context=context(work);if(context==null)return Map.of();
        Map<String,String> result=new LinkedHashMap<>();result.put("track",fingerprint(context.track,context));result.put("fade",fingerprint(context.fade,context));
        for(MethodNode method:work.methods)for(AbstractInsnNode node:method.instructions)if(node instanceof InvokeDynamicInsnNode dynamic){
            MethodNode implementation=implementation(work,dynamic);
            if(implementation!=null&&implementation.desc.equals("([Ljava/lang/Object;)Ljava/lang/Void;"))result.put("base:"+implementation.name,fingerprint(implementation,context));
            if(implementation!=null&&implementation.desc.equals("(L"+OP+";[Ljava/lang/Object;)Ljava/lang/Void;"))result.put("bridge:"+implementation.name,fingerprint(implementation,context));
        }
        return result;
    }
    private static Context context(ClassNode owner){
        List<MethodNode> tracks=owner.methods.stream().filter(method->merged(method,TRACK)&&method.name.endsWith("$updateRenderingEntityState")).toList();
        List<MethodNode> fades=owner.methods.stream().filter(method->merged(method,FADE)&&method.name.endsWith("$overflowparticle$fade")).toList();
        if(tracks.size()!=1||fades.size()!=1)return null;MethodNode track=tracks.getFirst(),fade=fades.getFirst();
        Type[] args=Type.getArgumentTypes(track.desc);
        if(track.access!=ACC_PRIVATE||fade.access!=ACC_PRIVATE||!track.desc.equals(fade.desc)||args.length!=10||Type.getReturnType(track.desc)!=Type.VOID_TYPE||!args[9].equals(Type.getObjectType(OP)))return null;
        for(int i=0;i<9;i++)if(i<3?args[i].getSort()!=Type.OBJECT:args[i]!=Type.FLOAT_TYPE)return null;
        if(!track.tryCatchBlocks.isEmpty()||!fade.tryCatchBlocks.isEmpty()||packs(track).size()!=2||packs(fade).size()!=1)return null;
        String particleType=null;
        for(AbstractInsnNode node:track.instructions)if(node instanceof MethodInsnNode call&&call.owner.equals("org/polyfrost/overflowparticles/client/config/ParticleConfig")&&call.name.equals("getParticleType")){
            Type result=Type.getReturnType(call.desc);if(result.getSort()!=Type.OBJECT)return null;
            if(particleType!=null&&!particleType.equals(result.getInternalName()))return null;particleType=result.getInternalName();
        }
        return particleType==null?null:new Context(owner.name,Arrays.copyOf(args,9),particleType,track,fade,Type.getMethodDescriptor(Type.VOID_TYPE,Arrays.copyOf(args,9)));
    }
    private static Patch site(ClassNode owner,Context context,MethodNode method,List<AbstractInsnNode> code,int at){
        InvokeDynamicInsnNode base=(InvokeDynamicInsnNode)code.get(at);MethodNode nativeBridge=implementation(owner,base);
        if(nativeBridge==null||nativeBridge.access!=(ACC_PRIVATE|ACC_STATIC|ACC_SYNTHETIC)||!fingerprint(nativeBridge,context).equals(BASE_HASH)||at<10||at+25>=code.size())return null;
        int[] slots=new int[9];
        if(!variable(code.get(at-10),ALOAD,0))return null;
        for(int i=0;i<9;i++){
            if(!(code.get(at-9+i) instanceof VarInsnNode load)||load.getOpcode()!=context.arguments[i].getOpcode(ILOAD))return null;
            slots[i]=load.var;
        }
        int i=at+1;
        if(!(code.get(i++) instanceof VarInsnNode operation)||operation.getOpcode()!=ASTORE)return null;
        for(int arg=8;arg>=0;arg--)if(!variable(code.get(i++),context.arguments[arg].getOpcode(ISTORE),slots[arg]))return null;
        if(code.get(i++).getOpcode()!=POP||!variable(code.get(i++),ALOAD,0))return null;
        for(int arg=0;arg<9;arg++)if(!variable(code.get(i++),context.arguments[arg].getOpcode(ILOAD),slots[arg]))return null;
        if(!variable(code.get(i++),ALOAD,0))return null;
        AbstractInsnNode operationLoad=code.get(i++);
        if(!variable(operationLoad,ALOAD,operation.var)||!(code.get(i++) instanceof InvokeDynamicInsnNode fade))return null;
        if(!fade.desc.equals("(L"+owner.name+";L"+OP+";)L"+OP+";"))return null;
        MethodNode fadeBridge=implementation(owner,fade);
        if(fadeBridge==null||fadeBridge.access!=(ACC_PRIVATE|ACC_SYNTHETIC)||!fingerprint(fadeBridge,context).equals(BRIDGE_HASH))return null;
        if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=INVOKEVIRTUAL||!call.owner.equals(owner.name)||!call.name.equals(context.track.name)||!call.desc.equals(context.track.desc))return null;
        if(!unbranched(method,code.get(at-10),call))return null;
        for(AbstractInsnNode node:method.instructions){
            if(node instanceof VarInsnNode variable&&variable.var==operation.var&&node!=operation&&node!=operationLoad)return null;
            if(node instanceof IincInsnNode increment&&increment.var==operation.var)return null;
        }
        Frame frame=frameBefore(owner.name,method,base);
        Object[] expected=new Object[]{owner.name,context.arguments[0].getInternalName(),context.arguments[1].getInternalName(),context.arguments[2].getInternalName(),FLOAT,FLOAT,FLOAT,FLOAT,FLOAT,FLOAT};
        return frame!=null&&Arrays.equals(frame.stack,expected)?new Patch(method,base,call,frame):null;
    }
    private static MethodNode typed(MethodNode original,String name,Context context,MethodInsnNode render,String next){
        MethodNode result=new MethodNode(ASM9,original.access,name,context.typedDescriptor,null,original.exceptions==null?null:original.exceptions.toArray(String[]::new));
        original.accept(result);result.name=name;result.desc=context.typedDescriptor;result.signature=null;
        result.access=ACC_PRIVATE|ACC_SYNTHETIC;result.visibleAnnotations=result.invisibleAnnotations=null;
        result.visibleParameterAnnotations=result.invisibleParameterAnnotations=null;result.visibleAnnotableParameterCount=result.invisibleAnnotableParameterCount=0;
        if(result.parameters!=null)result.parameters=new ArrayList<>(result.parameters.subList(0,9));
        if(result.localVariables!=null)result.localVariables.removeIf(variable->variable.index==10);
        List<Pack> calls=packs(result);if(calls.size()!=(next==null?1:2))return null;
        for(Pack pack:calls){
            InsnList direct=new InsnList();if(next!=null)direct.add(new VarInsnNode(ALOAD,0));
            for(int i=0;i<9;i++)direct.add(new VarInsnNode(context.arguments[i].getOpcode(ILOAD),i+1));
            if(next==null)direct.add(new MethodInsnNode(INVOKEVIRTUAL,render.owner,render.name,render.desc,render.itf));
            else direct.add(new MethodInsnNode(INVOKESPECIAL,context.owner,next,context.typedDescriptor,false));
            result.instructions.insertBefore(pack.first,direct);AbstractInsnNode after=pack.last.getNext();
            for(AbstractInsnNode node=pack.first;node!=after;){AbstractInsnNode following=node.getNext();result.instructions.remove(node);node=following;}
        }
        for(AbstractInsnNode node:result.instructions){
            if(node instanceof FrameNode frame&&frame.local!=null&&frame.local.size()>10)frame.local.set(10,TOP);
            if(node instanceof VarInsnNode variable&&variable.var==10)return null;
            if(node instanceof MethodInsnNode call&&(call.owner.equals(OP)||call.owner.equals("java/lang/Float")&&call.name.equals("valueOf")))return null;
        }
        result.maxStack=Math.max(11,result.maxStack);return result;
    }
    private static List<Pack> packs(MethodNode method){
        List<AbstractInsnNode> code=code(method);List<Pack> result=new ArrayList<>();
        for(int at=0;at<code.size();at++)if(variable(code.get(at),ALOAD,10)){
            int i=at+1;
            if(i+2>=code.size()||!integer(code.get(i++),9)||!(code.get(i++) instanceof TypeInsnNode array)||array.getOpcode()!=ANEWARRAY||!array.desc.equals("java/lang/Object"))return List.of();
            for(int arg=0;arg<9;arg++){
                if(i+4>=code.size()||code.get(i++).getOpcode()!=DUP||!integer(code.get(i++),arg)||!variable(code.get(i++),arg<3?ALOAD:FLOAD,arg+1))return List.of();
                if(arg>=3&&!method(code.get(i++),INVOKESTATIC,"java/lang/Float","valueOf","(F)Ljava/lang/Float;"))return List.of();
                if(code.get(i++).getOpcode()!=AASTORE)return List.of();
            }
            if(i+1>=code.size()||!method(code.get(i++),INVOKEINTERFACE,OP,"call","([Ljava/lang/Object;)Ljava/lang/Object;")||code.get(i).getOpcode()!=POP||!unbranched(method,code.get(at),code.get(i)))return List.of();
            result.add(new Pack(code.get(at),code.get(i)));at=i;
        }
        return result;
    }
    private static MethodNode implementation(ClassNode owner,InvokeDynamicInsnNode call){
        if(!call.name.equals("call")||!call.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")||!call.bsm.getName().equals("metafactory")||call.bsmArgs.length!=3||
                !(call.bsmArgs[0] instanceof Type generic)||!generic.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Object;")||
                !(call.bsmArgs[1] instanceof Handle handle)||!handle.getOwner().equals(owner.name)||
                !(call.bsmArgs[2] instanceof Type erased)||!erased.getDescriptor().equals("([Ljava/lang/Object;)Ljava/lang/Void;")||!handle.getName().startsWith("mixinextras$bridge$"))return null;
        MethodNode method=owner.methods.stream().filter(value->value.name.equals(handle.getName())&&value.desc.equals(handle.getDesc())).findFirst().orElse(null);
        if(method==null||!method.tryCatchBlocks.isEmpty()||handle.getTag()!=((method.access&ACC_STATIC)!=0?H_INVOKESTATIC:H_INVOKESPECIAL))return null;
        return method;
    }
    private static boolean unbranched(MethodNode method,AbstractInsnNode first,AbstractInsnNode last){
        int from=method.instructions.indexOf(first),to=method.instructions.indexOf(last);
        for(AbstractInsnNode node:method.instructions){
            if(node instanceof JumpInsnNode jump){int target=method.instructions.indexOf(jump.label);if(target>from&&target<=to)return false;}
            if(node instanceof TableSwitchInsnNode branch){
                if(inside(method,branch.dflt,from,to))return false;for(LabelNode label:branch.labels)if(inside(method,label,from,to))return false;
            }
            if(node instanceof LookupSwitchInsnNode branch){
                if(inside(method,branch.dflt,from,to))return false;for(LabelNode label:branch.labels)if(inside(method,label,from,to))return false;
            }
        }
        for(AbstractInsnNode node=first;node!=last;node=node.getNext())if(node instanceof LabelNode)return false;
        return true;
    }
    private static boolean inside(MethodNode method,LabelNode label,int from,int to){int index=method.instructions.indexOf(label);return index>from&&index<=to;}
    private static Frame frameBefore(String owner,MethodNode method,InvokeDynamicInsnNode wanted){
        AnalyzerAdapter analyzer=new AnalyzerAdapter(owner,method.access,method.name,method.desc,null);Frame[] result={null};int[] ordinal={0};
        int expected=0;for(AbstractInsnNode node:method.instructions){if(node==wanted)break;if(node instanceof InvokeDynamicInsnNode)expected++;}
        final int index=expected;
        method.accept(new MethodVisitor(ASM9,analyzer){
            @Override public void visitInvokeDynamicInsn(String name,String descriptor,Handle bootstrap,Object... arguments){
                if(ordinal[0]++==index&&analyzer.locals!=null&&analyzer.stack!=null)result[0]=new Frame(values(analyzer.locals),values(analyzer.stack));
                super.visitInvokeDynamicInsn(name,descriptor,bootstrap,arguments);
            }
        });
        return result[0];
    }
    private static Object[] values(List<Object> values){
        List<Object> result=new ArrayList<>();
        for(int i=0;i<values.size();i++){
            Object value=values.get(i);if(!(value instanceof String)&&!(value instanceof Integer))throw new IllegalArgumentException("Uninitialized particle operation frame");
            result.add(value);if((value.equals(LONG)||value.equals(DOUBLE))&&i+1<values.size()&&values.get(i+1).equals(TOP))i++;
        }
        while(!result.isEmpty()&&result.getLast().equals(TOP))result.removeLast();return result.toArray();
    }
    private static FrameNode frame(Object[] locals,Object[] stack){return new FrameNode(F_FULL,locals.length,locals,stack.length,stack);}
    private static ClassNode expanded(ClassNode source){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);source.accept(writer);ClassNode result=new ClassNode(ASM9);new ClassReader(writer.toByteArray()).accept(result,ClassReader.EXPAND_FRAMES);return result;}
    private static boolean merged(MethodNode method,String expected){
        if(method.visibleAnnotations==null)return false;
        for(AnnotationNode annotation:method.visibleAnnotations)if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")&&annotation.values!=null){
            Object name=null,priority=null;for(int i=0;i+1<annotation.values.size();i+=2){if(annotation.values.get(i).equals("mixin"))name=annotation.values.get(i+1);if(annotation.values.get(i).equals("priority"))priority=annotation.values.get(i+1);}
            if(expected.equals(name)&&Integer.valueOf(1000).equals(priority))return true;
        }
        return false;
    }
    private static String fingerprint(MethodNode method,Context context){
        StringBuilder code=new StringBuilder(method.access+"|"+normalize(method.desc,context));List<AbstractInsnNode> instructions=code(method);
        for(AbstractInsnNode node:instructions){
            code.append('|').append(node.getOpcode());
            if(node instanceof VarInsnNode variable)code.append(':').append(variable.var);
            else if(node instanceof IntInsnNode integer)code.append(':').append(integer.operand);
            else if(node instanceof TypeInsnNode type)code.append(':').append(normalize(type.desc,context));
            else if(node instanceof MethodInsnNode call){
                String name=call.name;
                if(call.owner.equals(context.owner)&&name.equals(context.fade.name))name="@fade";
                if(call.owner.equals(context.arguments[0].getInternalName())&&(name.equals("render")||name.equals("m_32469004")))name="@render";
                code.append(':').append(normalize(call.owner,context)).append('.').append(name).append(normalize(call.desc,context)).append(':').append(call.itf);
            }else if(node instanceof FieldInsnNode field)code.append(':').append(normalize(field.owner,context)).append('.').append(field.name).append(normalize(field.desc,context));
            else if(node instanceof JumpInsnNode jump){AbstractInsnNode label=jump.label;while(label!=null&&label.getOpcode()<0)label=label.getNext();code.append(':').append(instructions.indexOf(label));}
            else if(node instanceof LdcInsnNode constant)code.append(':').append(normalize(String.valueOf(constant.cst),context));
            else if(!(node instanceof InsnNode))throw new IllegalArgumentException("Unsupported particle operation instruction");
        }
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static String normalize(String value,Context context){
        value=value.replace(context.owner,"@owner").replace(context.particleType,"@type");
        for(int i=0;i<3;i++){String type=context.arguments[i].getInternalName();value=value.replace(type,"@arg"+i).replace(type.replace('/','.'),"@arg"+i);}
        return value;
    }
    private static List<AbstractInsnNode> code(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode node:method.instructions)if(node.getOpcode()>=0)result.add(node);return result;}
    private static boolean variable(AbstractInsnNode node,int opcode,int slot){return node instanceof VarInsnNode variable&&variable.getOpcode()==opcode&&variable.var==slot;}
    private static boolean integer(AbstractInsnNode node,int value){return node.getOpcode()==ICONST_0+value&&value>=-1&&value<=5||node instanceof IntInsnNode integer&&(integer.getOpcode()==BIPUSH||integer.getOpcode()==SIPUSH)&&integer.operand==value;}
    private static boolean method(AbstractInsnNode node,int opcode,String owner,String name,String descriptor){return node instanceof MethodInsnNode call&&call.getOpcode()==opcode&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor);}
}
