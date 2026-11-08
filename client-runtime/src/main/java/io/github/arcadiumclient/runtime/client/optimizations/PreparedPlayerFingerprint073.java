package io.github.arcadiumclient.runtime.client.optimizations;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PreparedPlayerFingerprint073 {
    static final String MERGED="Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private PreparedPlayerFingerprint073(){}
    public static String classFingerprint(ClassNode source){return hash(classBytes(source));}
    public static byte[] classBytes(ClassNode source){
        ClassWriter writer=new ClassWriter(0);
        source.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return filtered(super.visitMethod(access,name,descriptor,signature,exceptions),false);
            }
            @Override public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value){
                return new FieldVisitor(Opcodes.ASM9,super.visitField(access,name,descriptor,signature,value)){
                    @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){return merged(super.visitAnnotation(descriptor,visible),descriptor);}
                };
            }
        });
        return writer.toByteArray();
    }
    public static String methodFingerprint(MethodNode source){return hash(methodBytes(source));}
    static byte[] methodBytes(MethodNode source){
        ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"PreparedFingerprint",null,"java/lang/Object",null);
        source.accept(filtered(writer.visitMethod(source.access,source.name,source.desc,source.signature,source.exceptions.toArray(String[]::new)),true));
        writer.visitEnd();return writer.toByteArray();
    }
    private static MethodVisitor filtered(MethodVisitor delegate,boolean stripDebug){
        return new MethodVisitor(Opcodes.ASM9,delegate){
            @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){
                return merged(super.visitAnnotation(descriptor,visible),descriptor);
            }
            @Override public void visitLineNumber(int line,Label start){if(!stripDebug)super.visitLineNumber(line,start);}
            @Override public void visitFrame(int type,int locals,Object[] local,int stack,Object[] values){if(!stripDebug)super.visitFrame(type,locals,local,stack,values);}
            @Override public void visitLocalVariable(String name,String descriptor,String signature,Label start,Label end,int index){if(!stripDebug)super.visitLocalVariable(name,descriptor,signature,start,end,index);}
            @Override public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,TypePath typePath,Label[] start,Label[] end,int[] index,String descriptor,boolean visible){return stripDebug?null:super.visitLocalVariableAnnotation(typeRef,typePath,start,end,index,descriptor,visible);}
        };
    }
    private static AnnotationVisitor merged(AnnotationVisitor delegate,String descriptor){
        if(!MERGED.equals(descriptor))return delegate;
        return new AnnotationVisitor(Opcodes.ASM9,delegate){
            @Override public void visit(String name,Object value){super.visit(name,"sessionId".equals(name)&&value instanceof String?"":value);}
        };
    }
    private static String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
