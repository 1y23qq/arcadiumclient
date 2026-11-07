package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Objects;

public final class PreparedBlockState073 {
    public interface Counters {int size(int i);int count(int i);int batch();int device();boolean recorded();void size(int i,int value);void count(int i,int value);void batch(int value);void device(int value);void recorded(boolean value);}
    public static final class Counts {
        private final int[] sizes=new int[2],counts=new int[2];private int batch,device;private boolean recorded;
        public void save(Counters access){for(int i=0;i<2;i++){sizes[i]=access.size(i);counts[i]=access.count(i);}batch=access.batch();device=access.device();recorded=access.recorded();}
        public int size(int i){return sizes[i];}public int count(int i){return counts[i];}public int batch(){return batch;}public int device(){return device;}
        public void restore(Counters access){for(int i=0;i<2;i++){access.size(i,sizes[i]);access.count(i,counts[i]);}access.batch(batch);access.device(device);access.recorded(recorded);}
    }
    public interface Parts {
        Object part(Object model,int index);boolean standard(Object part);boolean finite(Object part);boolean visible(Object part);Object box(Object part);Object mesh(Object part);
    }
    public static int liveMask(Parts access,Object model,Object[] parts,Object[] boxes,int plannedVisible,int block){
        int mask=0,from=block==0?0:7,to=block==0?7:12;
        for(int i=from;i<to;i++){Object current=access.part(model,i);if(current==null||current!=parts[i]||!access.standard(current)||!access.finite(current)||access.box(current)!=boxes[i]||access.mesh(current)!=null)return -1;boolean visible=access.visible(current);if(visible!=((plannedVisible&1<<i)!=0))return -1;if(visible)mask|=1<<i;}
        return mask;
    }
    public static final class Material {Object actor,capture,backend,pass,texture,batch,model;}
    public interface Selection {
        Object current()throws Throwable;boolean active(Object actor)throws Throwable;Object capture(Object actor)throws Throwable;Object backend(Object actor)throws Throwable;
        Object pass(Object capture)throws Throwable;Object texture(Object capture)throws Throwable;boolean material(Object capture,Object model,Object texture,Object pass)throws Throwable;
        Object select(Object backend,Object texture,Object pass)throws Throwable;int layer(Object backend)throws Throwable;
    }
    public static int selectOwned(Selection access,Material expected)throws Throwable{
        if(!same(access,expected)||!access.material(expected.capture,expected.model,expected.texture,expected.pass))return Integer.MIN_VALUE;
        Object batch=access.select(expected.backend,expected.texture,expected.pass);int layer=access.layer(expected.backend);
        if(batch!=expected.batch||layer< -1||layer>255||!same(access,expected)||!access.material(expected.capture,expected.model,expected.texture,expected.pass)||access.layer(expected.backend)!=layer)return Integer.MIN_VALUE;return layer;
    }
    private static boolean same(Selection a,Material e)throws Throwable{return a.current()==e.actor&&a.active(e.actor)&&a.capture(e.actor)==e.capture&&a.backend(e.actor)==e.backend&&a.pass(e.capture)==e.pass&&Objects.equals(a.texture(e.capture),e.texture);}
}
