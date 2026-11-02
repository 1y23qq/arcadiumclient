// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public final class RetainedTagOptions {
    private static final int BOOLEAN=0,BYTE=1,SHORT=2,CHAR=3,INT=4,LONG=5,FLOAT=6,DOUBLE=7,ENUM=8,COLOR=9;
    private static final int BACKGROUND=1,PADDING=2,FONT=4;
    private static final class Option {
        final MethodHandle getter,argb;
        final int kind,flags;
        long bits;
        Object reference;
        boolean seen;
        Option(Field field)throws ReflectiveOperationException {
            field.setAccessible(true);
            getter=MethodHandles.lookup().unreflectGetter(field);
            Class<?> type=field.getType();
            kind=type==boolean.class?BOOLEAN:type==byte.class?BYTE:type==short.class?SHORT:type==char.class?CHAR:
                    type==int.class?INT:type==long.class?LONG:type==float.class?FLOAT:type==double.class?DOUBLE:type.isEnum()?ENUM:COLOR;
            argb=kind==COLOR?MethodHandles.lookup().unreflect(type.getMethod("getArgb")).asType(MethodType.methodType(int.class,Object.class)):null;
            String name=field.getName();
            flags=name.equals("rounded")?BACKGROUND:name.equals("paddingX")||name.equals("paddingY")?PADDING:
                    name.equals("useInterInGame")||name.equals("useInterForModText")?FONT:0;
        }
        boolean poll()throws Throwable {
            long value;
            switch(kind){
                case BOOLEAN -> value=(boolean)getter.invokeExact()?1:0;
                case BYTE -> value=(byte)getter.invokeExact();
                case SHORT -> value=(short)getter.invokeExact();
                case CHAR -> value=(char)getter.invokeExact();
                case INT -> value=(int)getter.invokeExact();
                case LONG -> value=(long)getter.invokeExact();
                case FLOAT -> value=Float.floatToIntBits((float)getter.invokeExact());
                case DOUBLE -> value=Double.doubleToLongBits((double)getter.invokeExact());
                default -> {
                    Object object=getter.invoke();
                    if(kind==ENUM){boolean changed=!seen||reference!=object;reference=object;seen=true;return changed;}
                    value=object==null?0:(int)argb.invokeExact(object);
                    boolean changed=!seen||bits!=value||(reference==null)!=(object==null);
                    reference=object;bits=value;seen=true;return changed;
                }
            }
            boolean changed=!seen||bits!=value;bits=value;seen=true;return changed;
        }
        boolean positive(){return kind==FLOAT?Float.intBitsToFloat((int)bits)>0:kind==DOUBLE?Double.longBitsToDouble(bits)>0:bits>0;}
    }
    private final Option[] options;
    private boolean customBackground,customFont;

    public RetainedTagOptions(Class<?>... owners)throws ReflectiveOperationException {
        ArrayList<Option> fields=new ArrayList<>();
        for(Class<?> owner:owners)for(Field field:owner.getDeclaredFields()){
            Class<?> type=field.getType();
            if(Modifier.isStatic(field.getModifiers())&&(type.isPrimitive()||type.isEnum()||type.getSimpleName().equals("PolyColor")))fields.add(new Option(field));
        }
        options=fields.toArray(Option[]::new);
    }
    public boolean poll()throws Throwable {
        boolean changed=false;customBackground=customFont=false;
        for(Option option:options){
            changed|=option.poll();
            if((option.flags&BACKGROUND)!=0&&option.bits!=0||(option.flags&PADDING)!=0&&option.positive())customBackground=true;
            if((option.flags&FONT)!=0&&option.bits!=0)customFont=true;
        }
        return changed;
    }
    public boolean customBackground(){return customBackground;}
    public boolean customFont(){return customFont;}
}
