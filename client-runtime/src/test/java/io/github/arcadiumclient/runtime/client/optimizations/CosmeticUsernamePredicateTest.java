// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticUsernamePredicateTest {
    private static final Pattern USERNAME=Pattern.compile("[A-Za-z0-9_]+");
    @Test void everyUtf16CharacterAndSurrogatePairMatchesTheInstalledExpression(){
        boolean previous=RenderHookAllocationControl.enabled;
        try{
            for(int value=0;value<=Character.MAX_VALUE;value++){
                compare(USERNAME,String.valueOf((char)value));compare(USERNAME,"Player"+(char)value+"_09");
            }
            for(int high=0xD800;high<=0xDBFF;high+=17)for(int low=0xDC00;low<=0xDFFF;low+=31)compare(USERNAME,new String(new char[]{(char)high,(char)low}));
            compare(USERNAME,"");compare(USERNAME,"Ab_019Z".repeat(2048));
        }finally{RenderHookAllocationControl.enabled=previous;}
    }
    @Test void randomizedNamesAndUnknownPatternsStayExact(){
        Random random=new Random(660066);boolean previous=RenderHookAllocationControl.enabled;
        try{
            for(int sample=0;sample<24000;sample++){
                char[] value=new char[random.nextInt(97)];
                for(int i=0;i<value.length;i++)value[i]=sample%3==0?"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_".charAt(random.nextInt(63)):(char)random.nextInt(65536);
                compare(USERNAME,new String(value));
            }
            for(Pattern pattern:List.of(Pattern.compile(".*"),Pattern.compile("[A-Za-z0-9_]+",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),Pattern.compile("[A-Za-z0-9_]+",Pattern.LITERAL),Pattern.compile(""))){
                for(String text:List.of("", "Valid_019", "bad space", "\u212A", "\uD83D\uDE00", "[A-Za-z0-9_]+"))compare(pattern,text);
            }
            for(int i=0;i<64;i++)compare(Pattern.compile("[A-Za-z0-9_]+"),"DifferentPattern_"+i);
        }finally{RenderHookAllocationControl.enabled=previous;}
    }
    @Test void mutableSequencesNullsAndNativeErrorsAreDelegated(){
        boolean previous=RenderHookAllocationControl.enabled;
        try{
            StringBuilder mutable=new StringBuilder("Mutable_09");compare(USERNAME,mutable);mutable.append('!');compare(USERNAME,mutable);mutable.setLength(0);compare(USERNAME,mutable);
            for(Pattern pattern:new Pattern[]{USERNAME,Pattern.compile(".*"),null}){
                compareFailure(pattern,null);if(pattern==null)compareFailure(null,"name");
            }
            IllegalStateException sentinel=new IllegalStateException("native CharSequence failure");
            CharSequence failing=new CharSequence(){public int length(){throw sentinel;}public char charAt(int index){throw sentinel;}public CharSequence subSequence(int start,int end){throw sentinel;}};
            RenderHookAllocationControl.enabled=true;assertSame(sentinel,assertThrows(IllegalStateException.class,()->CosmeticUsernamePredicate.matches(USERNAME,failing)));
        }finally{RenderHookAllocationControl.enabled=previous;}
    }
    @Test void independentThreadsAndPatternIdentitiesRemainExact()throws Exception {
        boolean previous=RenderHookAllocationControl.enabled;RenderHookAllocationControl.enabled=true;
        try(var executor=Executors.newFixedThreadPool(4)){
            List<Future<?>> work=new ArrayList<>();
            for(int thread=0;thread<4;thread++){final int seed=thread;work.add(executor.submit(()->{
                Pattern local=Pattern.compile("[A-Za-z0-9_]+");Random random=new Random(seed);
                for(int i=0;i<10000;i++){String text="User_"+random.nextInt()+((i&1)==0?"":"!");assertEquals(local.matcher(text).matches(),CosmeticUsernamePredicate.matches(local,text));}
            }));}
            for(Future<?> future:work)future.get();
        }finally{RenderHookAllocationControl.enabled=previous;}
    }
    @Test void hotAsciiValidationAllocatesNoMatchersOrInputCache(){
        boolean previous=RenderHookAllocationControl.enabled;RenderHookAllocationControl.enabled=true;
        try{
            String[] names={"ArcadiumTest","Other_019","bad!", "", "Long_01234567890123456789"};
            for(int i=0;i<50000;i++)CosmeticUsernamePredicate.matches(USERNAME,names[i%names.length]);
            var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId(),best=Long.MAX_VALUE;
            for(int pass=0;pass<3;pass++){long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++)CosmeticUsernamePredicate.matches(USERNAME,names[i%names.length]);best=Math.min(best,bean.getThreadAllocatedBytes(thread)-before);}
            assertEquals(0,best);
        }finally{RenderHookAllocationControl.enabled=previous;}
    }
    private static void compare(Pattern pattern,CharSequence text){
        boolean expected=pattern.matcher(text).matches();RenderHookAllocationControl.enabled=false;assertEquals(expected,CosmeticUsernamePredicate.matches(pattern,text));RenderHookAllocationControl.enabled=true;assertEquals(expected,CosmeticUsernamePredicate.matches(pattern,text));
    }
    private static void compareFailure(Pattern pattern,CharSequence text){
        Throwable original=assertThrows(Throwable.class,()->pattern.matcher(text).matches());
        for(boolean enabled:new boolean[]{false,true}){RenderHookAllocationControl.enabled=enabled;Throwable actual=assertThrows(Throwable.class,()->CosmeticUsernamePredicate.matches(pattern,text));assertEquals(original.getClass(),actual.getClass());}
    }
    public static void main(String[] args)throws Exception {
        var test=new CosmeticUsernamePredicateTest();test.everyUtf16CharacterAndSurrogatePairMatchesTheInstalledExpression();test.randomizedNamesAndUnknownPatternsStayExact();test.mutableSequencesNullsAndNativeErrorsAreDelegated();test.independentThreadsAndPatternIdentitiesRemainExact();test.hotAsciiValidationAllocatesNoMatchersOrInputCache();
        System.out.println("Cosmetic username predicate: all UTF16 characters, surrogate pairs, 24k random names, mutable/unknown/null/errors, concurrent identities and0B hot path passed");
    }
}
