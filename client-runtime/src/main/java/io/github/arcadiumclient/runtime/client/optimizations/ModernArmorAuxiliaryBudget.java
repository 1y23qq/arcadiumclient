// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Collection;
import java.util.IdentityHashMap;

final class ModernArmorAuxiliaryBudget {
    static final int OWNERS=2, PROFILES=128, GEOMETRIES=896, INSTANCE_UNITS=8192;
    static final long VERTEX_BYTES=4L*1024*1024;
    private final IdentityHashMap<Object,Object> owners=new IdentityHashMap<>(), profiles=new IdentityHashMap<>();
    private final IdentityHashMap<Object,Geometry> geometries=new IdentityHashMap<>();
    private final IdentityHashMap<Object,Instance> instances=new IdentityHashMap<>();
    private long vertexBytes;
    private int units;

    private static final class Geometry {
        final Object owner,profile;
        final long bytes;
        int gpuCapacity=1;
        Geometry(Object owner,Object profile,long bytes){this.owner=owner;this.profile=profile;this.bytes=bytes;}
    }
    private static final class Instance {
        final Object geometry;
        int data=1,upload=1,used;
        Instance(Object geometry){this.geometry=geometry;}
    }
    boolean owner(Object owner){if(owner==null)return false;if(owners.containsKey(owner))return true;if(owners.size()==OWNERS)return false;owners.put(owner,owner);return true;}
    boolean profile(Object owner,Object profile){if(!owners.containsKey(owner)||profile==null)return false;Object known=profiles.get(profile);if(known!=null)return known==owner;if(profiles.size()==PROFILES)return false;profiles.put(profile,owner);return true;}
    boolean geometry(Object owner,Object profile,Object geometry,long bytes){
        if(!owners.containsKey(owner)||profiles.get(profile)!=owner||geometry==null||bytes<=0||bytes>VERTEX_BYTES)return false;
        Geometry known=geometries.get(geometry);if(known!=null)return known.owner==owner&&known.profile==profile&&known.bytes==bytes;
        if(geometries.size()==GEOMETRIES||bytes>VERTEX_BYTES-vertexBytes||units==INSTANCE_UNITS)return false;
        geometries.put(geometry,new Geometry(owner,profile,bytes));vertexBytes+=bytes;units++;return true;
    }
    boolean canInstance(){return INSTANCE_UNITS-units>=2;}
    int gpuCapacity(Object geometry){Geometry known=geometries.get(geometry);return known==null?0:known.gpuCapacity;}
    boolean instance(Object geometry,Object instance){
        if(!geometries.containsKey(geometry)||instance==null)return false;Instance known=instances.get(instance);if(known!=null)return known.geometry==geometry;
        if(INSTANCE_UNITS-units<2)return false;instances.put(instance,new Instance(geometry));units+=2;return true;
    }
    boolean grow(Object instance,int data,int upload,int gpu){
        Instance known=instances.get(instance);if(known==null||data<known.data||upload<known.upload||gpu<1||data<known.used||upload<known.used)return false;
        Geometry geometry=geometries.get(known.geometry);int nextGpu=Math.max(geometry.gpuCapacity,gpu);
        long extra=(long)data-known.data+(long)upload-known.upload+(long)nextGpu-geometry.gpuCapacity;
        if(extra>INSTANCE_UNITS-units)return false;
        units+=(int)extra;known.data=data;known.upload=upload;geometry.gpuCapacity=nextGpu;return true;
    }
    boolean used(Object instance,int count){Instance known=instances.get(instance);if(known==null||count<0||count>known.data||count>known.upload||count>geometries.get(known.geometry).gpuCapacity)return false;known.used=count;return true;}
    boolean layer(Object owner,Collection<?> parts){
        IdentityHashMap<Object,Boolean> selected=new IdentityHashMap<>();
        for(Object part:parts){Geometry geometry=geometries.get(part);if(geometry==null||geometry.owner!=owner)return false;selected.put(part,Boolean.TRUE);}
        if(selected.isEmpty())return false;
        long projected=units+(long)selected.size()*3*2;
        IdentityHashMap<Object,Integer> gpu=new IdentityHashMap<>();
        for(var entry:instances.entrySet()){
            Instance instance=entry.getValue();if(!selected.containsKey(instance.geometry))continue;
            int required=instance.used+1,data=capacity(instance.data,required),upload=Math.max(instance.upload,data);
            projected+=(long)data-instance.data+(long)upload-instance.upload;
            gpu.merge(instance.geometry,data,Math::max);
        }
        for(var entry:gpu.entrySet())projected+=Math.max(0,entry.getValue()-geometries.get(entry.getKey()).gpuCapacity);
        return projected<=INSTANCE_UNITS;
    }
    static int capacity(int old,int required){if(old<1||required<1||required>INSTANCE_UNITS)return Integer.MAX_VALUE;int next=old;while(next<required){if(next>INSTANCE_UNITS/2)return Integer.MAX_VALUE;next*=2;}return next;}
    void begin(Object owner){for(Instance instance:instances.values())if(geometries.get(instance.geometry).owner==owner)instance.used=0;}
    void release(Object owner){
        instances.entrySet().removeIf(entry->{Instance value=entry.getValue();Geometry geometry=geometries.get(value.geometry);if(geometry.owner!=owner)return false;units-=value.data+value.upload;return true;});
        geometries.entrySet().removeIf(entry->{Geometry value=entry.getValue();if(value.owner!=owner)return false;vertexBytes-=value.bytes;units-=value.gpuCapacity;return true;});
        profiles.values().removeIf(value->value==owner);owners.remove(owner);
    }
    int units(){return units;}long vertexBytes(){return vertexBytes;}int geometries(){return geometries.size();}int profiles(){return profiles.size();}int owners(){return owners.size();}int instances(){return instances.size();}
}
