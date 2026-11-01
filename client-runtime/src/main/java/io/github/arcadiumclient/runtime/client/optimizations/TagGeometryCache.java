// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;

public final class TagGeometryCache {
    public record Mesh(int firstVertex, int vertexCount, int[] quads) { }
    private record Variant(int stride, int color, boolean flat, boolean overrideAlpha, int[] source, Mesh mesh) { }
    private static final class Key implements Comparable<Key> {
        final int[] data;
        final int hash;
        Key(int[] data) {
            this.data=data;
            long mixed=0xcbf29ce484222325L;
            for(int value:data)mixed=(mixed^Integer.toUnsignedLong(value))*0x100000001b3L;
            mixed^=mixed>>>33;mixed*=0xff51afd7ed558ccdL;mixed^=mixed>>>33;
            this.hash=(int)(mixed^(mixed>>>32));
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Key key && Arrays.equals(data,key.data);}
        @Override public int compareTo(Key other){return Arrays.compare(data,other.data);}
    }
    private final IdentityHashMap<int[],ArrayDeque<Variant>> hints=new IdentityHashMap<>();
    private final Map<Key,Mesh> meshes=new HashMap<>();
    private int[] arena=new int[65536];
    private int words;
    public long hits,misses;
    public static final int MAX_WORDS=16*1024*1024;
    private static final int[] TRIANGLE={0,1,2,0,2,3};

    public Mesh mesh(int[] source,int length,int stride,int color,boolean flat,boolean overrideAlpha){
        if(length<0||length>source.length||length%stride!=0||length/stride%4!=0)throw new IllegalArgumentException("Invalid tag quad data");
        ArrayDeque<Variant> variants=hints.get(source);
        if(variants!=null)for(var variant:variants)
            if(variant.stride==stride&&variant.color==color&&variant.flat==flat&&variant.overrideAlpha==overrideAlpha
                    &&variant.source.length==length&&Arrays.equals(variant.source,0,length,source,0,length)){hits++;return variant.mesh;}
        int count=length/stride;int[] quads=new int[count*6];
        for(int v=0;v<count;v++){
            int from=v*stride,to=v*6;
            quads[to]=source[from];quads[to+1]=source[from+1];quads[to+2]=flat?0:source[from+2];
            quads[to+3]=stride>=6?source[from+3]:0;quads[to+4]=stride>=6?source[from+4]:0;
            quads[to+5]=source[from+color]|(overrideAlpha?0xff000000:0);
        }
        Key key=new Key(quads);Mesh mesh=meshes.get(key);
        if(mesh==null){
            int required=count/4*6*6;
            if(words+required>MAX_WORDS)throw new IllegalStateException("Name tag geometry budget reached");
            if(words+required>arena.length)arena=Arrays.copyOf(arena,Math.min(MAX_WORDS,Math.max(words+required,arena.length*2)));
            int first=words/6;
            for(int q=0;q<count;q+=4)for(int index:TRIANGLE){System.arraycopy(quads,(q+index)*6,arena,words,6);words+=6;}
            mesh=new Mesh(first,count/4*6,quads);meshes.put(key,mesh);misses++;
        }else hits++;
        if(variants==null){if(hints.size()>=4096)hints.clear();variants=new ArrayDeque<>();hints.put(source,variants);}
        if(variants.size()==4)variants.removeLast();
        variants.addFirst(new Variant(stride,color,flat,overrideAlpha,Arrays.copyOf(source,length),mesh));
        return mesh;
    }
    public int[] words(){return arena;}
    public int size(){return words;}
    public void clear(){hints.clear();meshes.clear();words=0;}
}
