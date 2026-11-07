// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.*;

public final class LowerNamePreparationFusion implements Opcodes {
    private static final String OP="com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String CONTROL=Type.getInternalName(LowerNamePreparation.class);
    private static final String SUFFIX="$argentum$batchNameTag";
    private static final String MIXIN="dev.rdh.argentum.mixin.features.nametag.EntityRendererMixin";
    private static final String HASH="bf7d8db67cd4ff5cfedd95ee6944c5b0f20060f006658a274ce7000a6c0c298f";
    private static final String TYPED="arcadium$typedLowerHologramName";
    private record Context(String owner,String living,MethodNode wrapper,String descriptor) { }
    private record Pack(AbstractInsnNode first,AbstractInsnNode last) { }
    private record Frame(Object[] locals,Object[] stack) { }
    private record Patch(Context context,MethodNode caller,AbstractInsnNode first,MethodInsnNode last,MethodInsnNode target,Frame frame) { }
    private LowerNamePreparationFusion(){ }

    public static int apply(ClassNode target){
        if(!target.name.equals("net/minecraft/client/render/entity/EntityRenderer")&&!target.name.equals("net/minecraft/unmapped/C_53593123"))return 0;
        if(target.methods.stream().anyMatch(method->method.name.equals(TYPED)))return 0;
        try{
            ClassNode work=expanded(target);Context context=context(work,true);if(context==null)return 0;
            Patch patch=patch(work,context);if(patch==null)return 0;MethodNode clone=typed(patch);if(clone==null)return 0;
            LabelNode slow=new LabelNode(),done=new LabelNode();InsnList fast=new InsnList();
            fast.add(new VarInsnNode(ALOAD,1));fast.add(new MethodInsnNode(INVOKESTATIC,CONTROL,"active","(Ljava/lang/Object;)Z",false));fast.add(new JumpInsnNode(IFEQ,slow));
            fast.add(new MethodInsnNode(INVOKESPECIAL,work.name,TYPED,context.descriptor,false));fast.add(new JumpInsnNode(GOTO,done));
            fast.add(slow);fast.add(frame(patch.frame.locals,patch.frame.stack));patch.caller.instructions.insertBefore(patch.first,fast);
            InsnList finish=new InsnList();finish.add(done);finish.add(frame(patch.frame.locals,new Object[0]));patch.caller.instructions.insert(patch.last,finish);
            patch.caller.maxStack=Math.max(13,patch.caller.maxStack);work.methods.add(clone);target.methods=work.methods;return 1;
        }catch(RuntimeException malformed){return 0;}
    }
    public static String audit(ClassNode target){Context context=context(target,false);return context==null?"unsupported":fingerprint(context.wrapper,context);}
    private static Context context(ClassNode owner,boolean pinned){
        List<MethodNode> wrappers=owner.methods.stream().filter(method->method.name.endsWith(SUFFIX)&&merged(method,MIXIN)).toList();
        if(wrappers.size()!=1)return null;MethodNode wrapper=wrappers.getFirst();Type[] args=Type.getArgumentTypes(wrapper.desc);
        if(wrapper.access!=ACC_PRIVATE||Type.getReturnType(wrapper.desc)!=Type.VOID_TYPE||args.length!=7||args[0].getSort()!=Type.OBJECT
                ||!args[1].equals(Type.getType(String.class))||args[2]!=Type.DOUBLE_TYPE||args[3]!=Type.DOUBLE_TYPE||args[4]!=Type.DOUBLE_TYPE
                ||args[5]!=Type.INT_TYPE||!args[6].equals(Type.getObjectType(OP)))return null;
        Context context=new Context(owner.name,args[0].getInternalName(),wrapper,Type.getMethodDescriptor(Type.VOID_TYPE,Arrays.copyOf(args,6)));
        return (!pinned||fingerprint(wrapper,context).equals(HASH))&&packs(wrapper).size()==2?context:null;
    }
    private static Patch patch(ClassNode owner,Context context){
        Patch result=null;
        for(MethodNode method:owner.methods){List<AbstractInsnNode> code=code(method);
            for(int at=0;at<code.size();at++)if(code.get(at) instanceof MethodInsnNode call&&call.owner.equals(owner.name)&&call.name.equals(context.wrapper.name)&&call.desc.equals(context.wrapper.desc)){
                if(result!=null||!method.desc.equals(context.descriptor)||(method.access&ACC_STATIC)!=0||at<9||call.getOpcode()!=INVOKESPECIAL)return null;
                if(!variable(code.get(at-9),ALOAD,0)||!variable(code.get(at-8),ALOAD,1)||!variable(code.get(at-7),ALOAD,2)
                        ||!variable(code.get(at-6),DLOAD,3)||!variable(code.get(at-5),DLOAD,5)||!variable(code.get(at-4),DLOAD,7)||!variable(code.get(at-3),ILOAD,9)
                        ||!variable(code.get(at-2),ALOAD,0)||!(code.get(at-1) instanceof InvokeDynamicInsnNode dynamic)||!unbranched(method,code.get(at-9),call))return null;
                MethodInsnNode next=implementation(owner,context,dynamic);if(next==null)return null;AbstractInsnNode first=code.get(at-2);Frame state=frameBefore(owner.name,method,first);
                if(state==null||!Arrays.equals(state.stack,new Object[]{owner.name,context.living,"java/lang/String",DOUBLE,DOUBLE,DOUBLE,INTEGER}))return null;
                result=new Patch(context,method,first,call,next,state);
            }
        }return result;
    }
    private static MethodInsnNode implementation(ClassNode owner,Context context,InvokeDynamicInsnNode dynamic){
        if(!dynamic.name.equals("call")||!dynamic.desc.equals("(L"+owner.name+";)L"+OP+";")
                ||!dynamic.bsm.equals(new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false))
                ||dynamic.bsmArgs.length!=3||!Type.getType("([Ljava/lang/Object;)Ljava/lang/Object;").equals(dynamic.bsmArgs[0])
                ||!(dynamic.bsmArgs[1] instanceof Handle handle)||handle.getTag()!=H_INVOKESPECIAL||!handle.getOwner().equals(owner.name)||handle.isInterface()
                ||!handle.getName().startsWith("mixinextras$bridge$")||!handle.getDesc().equals("([Ljava/lang/Object;)Ljava/lang/Void;")
                ||!Type.getType("([Ljava/lang/Object;)Ljava/lang/Void;").equals(dynamic.bsmArgs[2]))return null;
        MethodNode bridge=owner.methods.stream().filter(method->method.name.equals(handle.getName())&&method.desc.equals(handle.getDesc())).findFirst().orElse(null);
        if(bridge==null||bridge.access!=(ACC_PRIVATE|ACC_SYNTHETIC)||!bridge.tryCatchBlocks.isEmpty())return null;
        List<MethodInsnNode> targets=code(bridge).stream().filter(node->node instanceof MethodInsnNode call&&call.getOpcode()==INVOKESPECIAL&&call.owner.equals(owner.name)&&call.desc.equals(context.descriptor)).map(node->(MethodInsnNode)node).toList();
        if(targets.size()!=1)return null;MethodInsnNode target=targets.getFirst();
        if(owner.methods.stream().noneMatch(method->method.name.equals(target.name)&&method.desc.equals(target.desc)&&method.access==ACC_PRIVATE))return null;
        return fingerprint(bridge,context).equals(fingerprint(bridge(context,target),context))?target:null;
    }
    private static MethodNode bridge(Context context,MethodInsnNode target){
        MethodNode expected=new MethodNode(ASM9,ACC_PRIVATE|ACC_SYNTHETIC,"expected","([Ljava/lang/Object;)Ljava/lang/Void;",null,null);InsnList code=expected.instructions;
        code.add(new VarInsnNode(ALOAD,1));code.add(new IntInsnNode(BIPUSH,6));code.add(new LdcInsnNode("["+context.living.replace('/','.')+", java.lang.String, double, double, double, int]"));
        code.add(new MethodInsnNode(INVOKESTATIC,"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime","checkArgumentCount","([Ljava/lang/Object;ILjava/lang/String;)V",false));code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,1));
        for(int arg=0;arg<6;arg++){
            code.add(new InsnNode(DUP));code.add(new IntInsnNode(BIPUSH,arg));code.add(new InsnNode(AALOAD));
            String type=arg==0?context.living:arg==1?"java/lang/String":arg==5?"java/lang/Integer":"java/lang/Double";code.add(new TypeInsnNode(CHECKCAST,type));
            if(arg>=2)code.add(new MethodInsnNode(INVOKEVIRTUAL,type,arg==5?"intValue":"doubleValue",arg==5?"()I":"()D",false));
            if(arg>=2&&arg<=4){code.add(new InsnNode(DUP2_X1));code.add(new InsnNode(POP2));}else code.add(new InsnNode(SWAP));
        }
        code.add(new InsnNode(POP));code.add(new MethodInsnNode(INVOKESPECIAL,target.owner,target.name,target.desc,target.itf));code.add(new InsnNode(ACONST_NULL));code.add(new TypeInsnNode(CHECKCAST,"java/lang/Void"));code.add(new InsnNode(ARETURN));return expected;
    }
    private static MethodNode typed(Patch patch){
        Context context=patch.context;MethodNode result=new MethodNode(ASM9,context.wrapper.access,context.wrapper.name,context.wrapper.desc,context.wrapper.signature,context.wrapper.exceptions.toArray(String[]::new));context.wrapper.accept(result);
        result.name=TYPED;result.desc=context.descriptor;result.signature=null;result.access=ACC_PRIVATE|ACC_SYNTHETIC;result.visibleAnnotations=result.invisibleAnnotations=null;result.visibleParameterAnnotations=result.invisibleParameterAnnotations=null;result.visibleAnnotableParameterCount=result.invisibleAnnotableParameterCount=0;
        if(result.parameters!=null){if(result.parameters.size()!=7)return null;result.parameters=new ArrayList<>(result.parameters.subList(0,6));}
        if(result.localVariables!=null)result.localVariables.removeIf(variable->variable.index==10);
        for(Pack pack:packs(result)){
            InsnList direct=arguments();direct.add(new MethodInsnNode(INVOKESPECIAL,patch.target.owner,patch.target.name,patch.target.desc,patch.target.itf));
            result.instructions.insertBefore(pack.first,direct);AbstractInsnNode after=pack.last.getNext();for(AbstractInsnNode node=pack.first;node!=after;){AbstractInsnNode next=node.getNext();result.instructions.remove(node);node=next;}
        }
        for(AbstractInsnNode node:result.instructions){
            if(node instanceof FrameNode state&&state.local!=null){int slot=0;for(int i=0;i<state.local.size();i++){Object value=state.local.get(i);if(slot==10)state.local.set(i,TOP);slot+=value.equals(DOUBLE)||value.equals(LONG)?2:1;}}
            if(node instanceof VarInsnNode variable&&variable.var==10||node instanceof IincInsnNode increment&&increment.var==10)return null;
        }
        result.instructions.insert(new MethodInsnNode(INVOKESTATIC,CONTROL,"noteTyped","()V",false));result.maxStack=Math.max(12,result.maxStack);return result;
    }
    private static InsnList arguments(){InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ALOAD,1));code.add(new VarInsnNode(ALOAD,2));code.add(new VarInsnNode(DLOAD,3));code.add(new VarInsnNode(DLOAD,5));code.add(new VarInsnNode(DLOAD,7));code.add(new VarInsnNode(ILOAD,9));return code;}
    private static List<Pack> packs(MethodNode method){
        List<AbstractInsnNode> code=code(method);List<Pack> result=new ArrayList<>();
        for(int at=0;at<code.size();at++)if(variable(code.get(at),ALOAD,10)){
            int i=at+1;if(i+2>=code.size()||!integer(code.get(i++),6)||!(code.get(i++) instanceof TypeInsnNode array)||array.getOpcode()!=ANEWARRAY||!array.desc.equals("java/lang/Object"))return List.of();
            for(int arg=0;arg<6;arg++){
                if(i+4>=code.size()||code.get(i++).getOpcode()!=DUP||!integer(code.get(i++),arg))return List.of();
                int opcode=arg<2?ALOAD:arg==5?ILOAD:DLOAD,slot=arg<2?arg+1:arg==5?9:arg*2-1;if(!variable(code.get(i++),opcode,slot))return List.of();
                if(arg>=2&&!method(code.get(i++),INVOKESTATIC,arg==5?"java/lang/Integer":"java/lang/Double","valueOf",arg==5?"(I)Ljava/lang/Integer;":"(D)Ljava/lang/Double;"))return List.of();
                if(code.get(i++).getOpcode()!=AASTORE)return List.of();
            }
            if(i+1>=code.size()||!method(code.get(i++),INVOKEINTERFACE,OP,"call","([Ljava/lang/Object;)Ljava/lang/Object;")||code.get(i).getOpcode()!=POP||!unbranched(method,code.get(at),code.get(i)))return List.of();
            result.add(new Pack(code.get(at),code.get(i)));at=i;
        }return result;
    }
    private static boolean merged(MethodNode method, String expected) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null) {
            Object name = null, priority = null;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) { if (annotation.values.get(i).equals("mixin")) name = annotation.values.get(i + 1); if (annotation.values.get(i).equals("priority")) priority = annotation.values.get(i + 1); }
            if (expected.equals(name) && Integer.valueOf(1000).equals(priority)) return true;
        }
        return false;
    }

    private static Frame frameBefore(String owner, MethodNode method, AbstractInsnNode wanted) {
        AnalyzerAdapter analyzer = new AnalyzerAdapter(owner, method.access, method.name, method.desc, null); Frame[] result = {null}; int[] ordinal = {0};
        int expected = 0; for (AbstractInsnNode node : method.instructions) { if (node == wanted) break; if (node instanceof VarInsnNode) expected++; } final int at = expected;
        method.accept(new MethodVisitor(ASM9, analyzer) {
            @Override public void visitVarInsn(int opcode, int variable) {
                if (ordinal[0]++ == at && analyzer.locals != null && analyzer.stack != null) result[0] = new Frame(values(analyzer.locals), values(analyzer.stack));
                super.visitVarInsn(opcode, variable);
            }
        }); return result[0];
    }
    private static Object[] values(List<Object> values) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) { Object value = values.get(i); if (!(value instanceof String) && !(value instanceof Integer)) throw new IllegalArgumentException("Uninitialized lower name frame"); result.add(value); if ((value.equals(LONG) || value.equals(DOUBLE)) && i + 1 < values.size() && values.get(i + 1).equals(TOP)) i++; }
        while (!result.isEmpty() && result.getLast().equals(TOP)) result.removeLast(); return result.toArray();
    }
    private static FrameNode frame(Object[] locals, Object[] stack) { return new FrameNode(F_FULL, locals.length, locals, stack.length, stack); }
    private static ClassNode expanded(ClassNode source) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); source.accept(writer); ClassNode result = new ClassNode(ASM9); new ClassReader(writer.toByteArray()).accept(result, ClassReader.EXPAND_FRAMES); return result; }
    private static boolean unbranched(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) {
        int from = method.instructions.indexOf(first), to = method.instructions.indexOf(last);
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof JumpInsnNode jump && inside(method, jump.label, from, to)) return false;
            if (node instanceof TableSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
            if (node instanceof LookupSwitchInsnNode branch) { if (inside(method, branch.dflt, from, to)) return false; for (LabelNode label : branch.labels) if (inside(method, label, from, to)) return false; }
        }
        for (AbstractInsnNode node = first; node != last; node = node.getNext()) if (node instanceof LabelNode) return false;
        for (TryCatchBlockNode block : method.tryCatchBlocks) if (inside(method, block.start, from, to) || inside(method, block.end, from, to) || inside(method, block.handler, from, to)) return false;
        return true;
    }
    private static String fingerprint(MethodNode method, Context context) {
        List<AbstractInsnNode> instructions = code(method); StringBuilder text = new StringBuilder(method.access + "|" + normalize(method.desc, context));
        for (AbstractInsnNode node : instructions) {
            text.append('|').append(node.getOpcode());
            if (node instanceof VarInsnNode variable) text.append(':').append(variable.var);
            else if (node instanceof IntInsnNode integer) text.append(':').append(integer.operand);
            else if (node instanceof MethodInsnNode call) text.append(':').append(normalize(call.owner + "." + call.name + call.desc, context)).append(':').append(call.itf);
            else if (node instanceof FieldInsnNode field) text.append(':').append(normalize(field.owner + "." + field.name + field.desc, context));
            else if (node instanceof JumpInsnNode jump) text.append(':').append(position(instructions, jump.label));
            else if (node instanceof TypeInsnNode type) text.append(':').append(normalize(type.desc, context));
            else if (node instanceof LdcInsnNode constant) text.append(':').append(constant(constant.cst, context));
            else if (node instanceof InvokeDynamicInsnNode dynamic) { text.append(':').append(normalize(dynamic.name + dynamic.desc, context)).append(':').append(constant(dynamic.bsm, context)); for (Object argument : dynamic.bsmArgs) text.append(':').append(constant(argument, context)); }
            else if (!(node instanceof InsnNode)) throw new IllegalArgumentException("Unsupported lower name instruction");
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) text.append("|catch:").append(position(instructions, block.start)).append(':').append(position(instructions, block.end)).append(':').append(position(instructions, block.handler)).append(':').append(block.type);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String constant(Object value, Context context) {
        if (value instanceof Type type) return normalize(type.getDescriptor(), context);
        if (value instanceof Handle handle) return handle.getTag() + ":" + normalize(handle.getOwner() + "." + handle.getName() + handle.getDesc(), context) + ":" + handle.isInterface();
        return normalize(String.valueOf(value), context);
    }
    private static String normalize(String value, Context context) {
        return value.replace(context.owner, "@renderer").replace(context.living, "@living").replace(context.living.replace('/', '.'), "@living")
            .replace("net/minecraft/entity/Entity", "@entity").replace("net/minecraft/unmapped/C_42232651", "@entity")
            .replace("net/minecraft/text/Text", "@text").replace("net/minecraft/unmapped/C_95231345", "@text")
            .replace("m_40164672", "@sneaking").replace("isSneaking", "@sneaking").replace("m_33641726", "@displayName").replace("getDisplayName", "@displayName")
            .replace("m_42269114", "@formatted").replace("getFormattedString", "@formatted");
    }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) result.add(node); return result; }
    private static int position(List<AbstractInsnNode> code, AbstractInsnNode node) { while (node != null && node.getOpcode() < 0) node = node.getNext(); return node == null ? code.size() : code.indexOf(node); }
    private static boolean inside(MethodNode method, LabelNode label, int from, int to) { int position = method.instructions.indexOf(label); return position > from && position <= to; }
    private static boolean variable(AbstractInsnNode node, int opcode, int slot) { return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean integer(AbstractInsnNode node, int value) { return node.getOpcode() == ICONST_0 + value && value >= -1 && value <= 5 || node instanceof IntInsnNode integer && (integer.getOpcode() == BIPUSH || integer.getOpcode() == SIPUSH) && integer.operand == value; }
    private static boolean method(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) { return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor); }
}
