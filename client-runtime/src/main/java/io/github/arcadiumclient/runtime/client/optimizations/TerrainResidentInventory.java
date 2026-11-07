// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.*;

public final class TerrainResidentInventory {
    public static final int LIMIT=1024, SECTIONS=256, WORDS=20, REGION_WORDS=SECTIONS*WORDS, ORDER_WORDS=65;
    private static final int[] ZERO=new int[REGION_WORDS];
    public static final class Span {
        public final int firstWord;
        private final int[] words;
        private Span(int firstWord,int[] words){this.firstWord=firstWord;this.words=words;}
        public int length(){return words.length;}
        public int word(int index){return words[index];}
        public void put(IntBuffer target){target.put(words);}
        public int[] copy(){return words.clone();}
    }
    public static final class OrderRow {
        final Object region,nativeRow;
        final byte[] ranks;
        public OrderRow(Object region,Object nativeRow,byte[] ranks,int count){
            if(region==null||nativeRow==null||ranks==null||count<0||count>256||count>ranks.length)throw new IllegalArgumentException("Unknown native order row");
            boolean[] seen=new boolean[256];this.ranks=Arrays.copyOf(ranks,count);
            for(byte rank:this.ranks){int id=rank&255;if(seen[id])throw new IllegalArgumentException("Duplicate native section rank");seen[id]=true;}
            this.region=region;this.nativeRow=nativeRow;
        }
    }
    public record RegionRef(Object region,Object nativeRow,Object storage,long generation,long storageGeneration,long bindings,int gpuSlot){}
    public static final class Frame {
        private final TerrainResidentInventory inventory;
        public final long epoch,baseUpload,throughUpload,orderPublication,newestLoad,layoutVersion;
        public final int pass,highWater,activeRegions,maximumElements;
        public final boolean fullReplay;
        private final Object nativeList;
        private final List<Span> metadata,orders;
        private final List<RegionRef> drawOrder;
        private Frame(TerrainResidentInventory inventory,int pass,List<Span> metadata,List<Span> orders,List<RegionRef> drawOrder){
            this.inventory=inventory;var state=inventory.passes[pass];this.epoch=inventory.epoch;this.baseUpload=state.ack;this.throughUpload=state.revision;this.orderPublication=inventory.orderPublication;
            this.fullReplay=state.fullReplay;this.newestLoad=state.loads.isEmpty()?0:state.loads.lastKey();this.layoutVersion=state.layoutVersion;this.pass=pass;this.highWater=state.highWater;this.activeRegions=state.active;this.maximumElements=state.maximum;this.nativeList=inventory.nativeList;
            this.metadata=List.copyOf(metadata);this.orders=List.copyOf(orders);this.drawOrder=drawOrder;
        }
        public List<Span> metadata(){return metadata;}
        public List<Span> orders(){return orders;}
        public List<RegionRef> drawOrder(){return drawOrder;}
        public int slot(Object storage,Object actualNativeList){if(!valid(actualNativeList))return -1;Slot slot=inventory.storage.get(storage);return slot!=null&&slot.pass==pass?slot.index:-1;}
        public boolean valid(Object currentNativeList){return inventory.valid(this,currentNativeList);}
    }
    public static final class Ticket {
        private final TerrainResidentInventory inventory;
        private final long sequence;
        private Ticket(TerrainResidentInventory inventory,long sequence){this.inventory=inventory;this.sequence=sequence;}
    }
    private record Key(int x,int y,int z) implements Comparable<Key>{
        public int compareTo(Key other){int d=Integer.compare(x,other.x);if(d==0)d=Integer.compare(y,other.y);return d==0?Integer.compare(z,other.z):d;}
    }
    private static final class Region {
        final Object identity;final Key key;final long generation;final Slot[] slots=new Slot[2];long bindings,newestLoad;
        Region(Object identity,Key key,long generation){this.identity=identity;this.key=key;this.generation=generation;}
    }
    private static final class Slot {
        final Object storage;final Region region;final int pass,index;final long generation;
        final int[] words=new int[REGION_WORDS],elements=new int[256];final BitSet dirty=new BitSet(256);
        int populated;byte[] ranks=new byte[0];
        Slot(Object storage,Region region,int pass,int index,long generation){this.storage=storage;this.region=region;this.pass=pass;this.index=index;this.generation=generation;dirty.set(0,256);}
    }
    private static final class Pass {
        final Slot[] slots=new Slot[LIMIT];final BitSet dirty=new BitSet(LIMIT),orderDirty=new BitSet(LIMIT),free=new BitSet(LIMIT);
        final TreeMap<Integer,Integer> elements=new TreeMap<>();final TreeMap<Long,Integer> loads=new TreeMap<>();
        int highWater,active,maximum;long revision,ack,layoutVersion=1;Frame cached;List<RegionRef> drawOrder=List.of();boolean topology=true,fullReplay=true;
        Pass(){free.set(0,LIMIT);}
    }
    private final Thread owner=Thread.currentThread();
    private final Object manager;
    private final Pass[] passes={new Pass(),new Pass()};
    private final IdentityHashMap<Object,Region> regions=new IdentityHashMap<>();
    private final TreeMap<Key,Region> spatial=new TreeMap<>();
    private final IdentityHashMap<Object,Region> retired=new IdentityHashMap<>(),publishedRegions=new IdentityHashMap<>();
    private final IdentityHashMap<Object,OrderRow> publishedRanks=new IdentityHashMap<>();
    private final IdentityHashMap<Object,Slot> storage=new IdentityHashMap<>();
    private final Ticket[] pending=new Ticket[16];
    private int depth;private long sequence,generation,epoch=1,orderPublication;private int nativeFrame=-1;
    private Object nativeList;private List<OrderRow> nativePrefix=List.of();private volatile String failure;
    public long metadataRowsRead,orderRowsCopied,seals,unchangedSeals,topologyVisits;
    public TerrainResidentInventory(Object manager){this.manager=Objects.requireNonNull(manager);}
    public boolean ownerThread(){return Thread.currentThread()==owner;}
    public String failure(){return failure;}
    public void invalidate(String reason){if(failure==null)failure=Objects.requireNonNull(reason);}
    private void check(){if(!ownerThread()){invalidate("Foreign inventory owner thread");throw new IllegalStateException(failure);}if(failure!=null)throw new IllegalStateException(failure);}
    private void mutation(){check();if(depth==0){invalidate("Publication without native operation ticket");throw new IllegalStateException(failure);}}
    public Ticket begin(){check();if(depth==pending.length){invalidate("Native publication nesting bound");throw new IllegalStateException(failure);}if(sequence==Long.MAX_VALUE){invalidate("Operation sequence exhausted");throw new IllegalStateException(failure);}Ticket t=new Ticket(this,++sequence);pending[depth++]=t;return t;}
    public void end(Ticket ticket,boolean success){
        check();if(ticket==null||ticket.inventory!=this||depth==0||pending[depth-1]!=ticket){invalidate("Dropped or out-of-order native completion");throw new IllegalStateException(failure);}
        pending[--depth]=null;if(!success)invalidate("Native operation did not complete successfully");
    }
    private void advanceEpoch(){if(epoch==Long.MAX_VALUE){invalidate("Inventory epoch exhausted");throw new IllegalStateException(failure);}epoch++;}
    private void changed(int pass){Pass p=passes[pass];if(p.revision==Long.MAX_VALUE){invalidate("Upload sequence exhausted");throw new IllegalStateException(failure);}p.revision++;p.cached=null;advanceEpoch();}
    private long generation(){if(generation==Long.MAX_VALUE){invalidate("Generation exhausted");throw new IllegalStateException(failure);}return ++generation;}
    public void addRegion(Object identity,int x,int y,int z){
        mutation();Objects.requireNonNull(identity);if(Math.abs((long)x)>30_001_024||Math.abs((long)y)>30_001_024||Math.abs((long)z)>30_001_024||x%128!=0||y%64!=0||z%128!=0){invalidate("Unproven native region coordinates");return;}Region old=regions.get(identity);Key key=new Key(x,y,z);
        if(old!=null){if(!old.key.equals(key))invalidate("Region identity moved without replacement");return;}
        if(regions.size()==LIMIT||spatial.containsKey(key)){invalidate("Unknown or excessive native region inventory");return;}
        Region r=new Region(identity,key,generation());regions.put(identity,r);spatial.put(key,r);advanceEpoch();
    }
    private static void load(Pass pass,long time,int delta){if(time==0)return;pass.loads.compute(time,(k,n)->{int count=(n==null?0:n)+delta;return count==0?null:count;});}
    public void loadTime(Object identity,long time){mutation();Region r=regions.get(identity);if(r==null){invalidate("Unknown native section load time");return;}if(r.newestLoad==time)return;for(Slot slot:r.slots)if(slot!=null&&slot.populated>0){Pass p=passes[slot.pass];load(p,r.newestLoad,-1);load(p,time,1);}r.newestLoad=time;advanceEpoch();for(Pass p:passes)p.cached=null;}
    public void bindingsChanged(Object identity){mutation();Region r=regions.get(identity);if(r==null){invalidate("Unknown native binding invalidation");return;}r.bindings++;for(Pass p:passes){p.topology=true;p.layoutVersion++;p.cached=null;}advanceEpoch();}
    public void removeRegion(Object identity){
        mutation();Region r=regions.get(identity);if(r==null){invalidate("Unknown native region deletion");return;}
        for(Slot slot:r.slots)if(slot!=null)removeStorage(slot.storage);
        regions.remove(identity);spatial.remove(r.key);if(retired.size()==LIMIT){invalidate("Retired region publication bound");return;}retired.put(identity,r);for(Pass p:passes){p.topology=true;p.layoutVersion++;p.cached=null;}advanceEpoch();
    }
    public void addStorage(Object region,Object identity,int pass,int[] initialWords){
        mutation();if(pass<0||pass>1||identity==null||initialWords.length!=REGION_WORDS){invalidate("Unknown storage layout");return;}
        Region r=regions.get(region);if(r==null){invalidate("Storage arrived before its native region");return;}Slot old=storage.get(identity);
        if(old!=null){if(old.region!=r||old.pass!=pass)invalidate("Storage alias changed owner/pass");return;}
        if(r.slots[pass]!=null){invalidate("Native storage replacement omitted deletion");return;}
        Pass p=passes[pass];int index=p.free.nextSetBit(0);if(index<0){invalidate("Resident storage capacity exceeded");return;}p.free.clear(index);p.highWater=Math.max(p.highWater,index+1);
        Slot slot=new Slot(identity,r,pass,index,generation());r.slots[pass]=slot;p.slots[index]=slot;storage.put(identity,slot);
        for(int section=0;section<256;section++)replaceSection(slot,section,initialWords,section*20);
        OrderRow oldRank=publishedRanks.get(region);if(oldRank!=null)rank(slot,oldRank.ranks);
        p.dirty.set(index);p.orderDirty.set(index);p.topology=true;p.layoutVersion++;changed(pass);
    }
    public void section(Object identity,int section,int[] words){
        mutation();Slot slot=storage.get(identity);if(slot==null||section<0||section>=256||words.length!=20){invalidate("Unknown section publication");return;}
        replaceSection(slot,section,words,0);slot.dirty.set(section);passes[slot.pass].dirty.set(slot.index);metadataRowsRead++;changed(slot.pass);
    }
    public void relocate(Object identity,int[] words){
        mutation();Slot slot=storage.get(identity);if(slot==null||words.length!=REGION_WORDS){invalidate("Unknown arena relocation");return;}
        for(int section=0;section<256;section++)replaceSection(slot,section,words,section*20);
        slot.dirty.set(0,256);passes[slot.pass].dirty.set(slot.index);metadataRowsRead+=256;changed(slot.pass);
    }
    private void replaceSection(Slot slot,int section,int[] words,int source){
        int total=0;for(int face=0;face<7;face++){int first=words[source+4+face],count=words[source+12+face];if(first<0||count<0||count%6!=0){invalidate("Unproven native quad span");throw new IllegalArgumentException(failure);}try{total=Math.addExact(total,count);}catch(ArithmeticException error){invalidate("Native quad element overflow");throw error;}}
        Pass p=passes[slot.pass];int old=slot.elements[section];if(old>0)p.elements.compute(old,(k,n)->n==1?null:n-1);if(total>0)p.elements.merge(total,1,Integer::sum);
        int was=slot.populated;slot.populated+=(total>0?1:0)-(old>0?1:0);if((was==0)!=(slot.populated==0)){p.active+=slot.populated>0?1:-1;load(p,slot.region.newestLoad,slot.populated>0?1:-1);p.topology=true;p.layoutVersion++;}
        if((old>0)!=(total>0)){p.topology=true;p.layoutVersion++;}
        slot.elements[section]=total;System.arraycopy(words,source,slot.words,section*20,20);p.maximum=p.elements.isEmpty()?0:p.elements.lastKey();
    }
    public void removeStorage(Object identity){
        mutation();Slot s=storage.remove(identity);if(s==null){invalidate("Unknown storage deletion");return;}Pass p=passes[s.pass];
        for(int n:s.elements)if(n>0)p.elements.compute(n,(k,count)->count==1?null:count-1);
        p.maximum=p.elements.isEmpty()?0:p.elements.lastKey();if(s.populated>0){p.active--;load(p,s.region.newestLoad,-1);}s.region.slots[s.pass]=null;p.slots[s.index]=null;p.free.set(s.index);p.dirty.set(s.index);p.orderDirty.set(s.index);p.topology=true;p.layoutVersion++;changed(s.pass);
    }
    public void publishOrder(Object actualNativeList,int frame,List<OrderRow> rows){
        mutation();if(actualNativeList==null||frame<nativeFrame||rows.size()>LIMIT){invalidate("Unknown/stale native list publication");return;}
        if(actualNativeList==nativeList){if(frame!=nativeFrame)invalidate("Native list identity was reused with a different publication");return;}
        var seen=new IdentityHashMap<Object,Boolean>();var next=List.copyOf(rows);var resolved=new IdentityHashMap<Object,Region>();
        for(OrderRow row:next){Region r=regions.get(row.region);if(r==null)r=retired.get(row.region);if(r==null||seen.put(row.region,true)!=null){invalidate("Unknown/duplicate native prefix region");return;}resolved.put(row.region,r);}
        for(OrderRow old:nativePrefix)if(!seen.containsKey(old.region)){Region r=publishedRegions.get(old.region);if(r!=null)for(Slot s:r.slots)if(s!=null)rank(s,new byte[0]);}
        for(OrderRow row:next){Region r=resolved.get(row.region);for(Slot s:r.slots)if(s!=null)rank(s,row.ranks);orderRowsCopied++;}
        publishedRegions.clear();publishedRegions.putAll(resolved);publishedRanks.clear();for(OrderRow row:next)publishedRanks.put(row.region,row);retired.keySet().removeIf(key->!seen.containsKey(key));
        nativeList=actualNativeList;nativeFrame=frame;nativePrefix=next;orderPublication++;for(Pass p:passes){p.topology=true;p.layoutVersion++;p.cached=null;}advanceEpoch();
    }
    private void rank(Slot slot,byte[] ranks){if(Arrays.equals(slot.ranks,ranks))return;slot.ranks=ranks.clone();passes[slot.pass].orderDirty.set(slot.index);changed(slot.pass);}
    public Frame seal(int pass,Object actualNativeList){
        check();seals++;if(pass<0||pass>1||depth!=0||nativeList==null||nativeList!=actualNativeList)throw new IllegalStateException("Incomplete/unobserved native publication");Pass p=passes[pass];
        if(p.cached!=null&&p.cached.epoch==epoch){unchangedSeals++;return p.cached;}
        if(p.topology){var refs=new ArrayList<RegionRef>();var seen=new IdentityHashMap<Object,Boolean>();
            for(OrderRow row:nativePrefix){Region r=publishedRegions.get(row.region);if(r==null){invalidate("Published prefix references deleted native region");throw new IllegalStateException(failure);}Slot s=r.slots[pass];refs.add(new RegionRef(r.identity,row.nativeRow,s==null?null:s.storage,r.generation,s==null?0:s.generation,r.bindings,s==null?-1:s.index));seen.put(r.identity,true);topologyVisits++;}
            for(Region r:spatial.values()){Slot s=r.slots[pass];if(s!=null&&s.populated>0&&!seen.containsKey(r.identity)){refs.add(new RegionRef(r.identity,null,s.storage,r.generation,s.generation,r.bindings,s.index));topologyVisits++;}}
            p.drawOrder=List.copyOf(refs);p.topology=false;
        }
        var metadata=new ArrayList<Span>();var orders=new ArrayList<Span>();
        for(int i=p.dirty.nextSetBit(0);i>=0;i=p.dirty.nextSetBit(i+1)){Slot s=p.slots[i];if(s==null){metadata.add(new Span(i*REGION_WORDS,ZERO.clone()));continue;}
            for(int start=s.dirty.nextSetBit(0);start>=0;){int end=s.dirty.nextClearBit(start);metadata.add(new Span(i*REGION_WORDS+start*20,Arrays.copyOfRange(s.words,start*20,end*20)));start=s.dirty.nextSetBit(end);}}
        for(int i=p.orderDirty.nextSetBit(0);i>=0;i=p.orderDirty.nextSetBit(i+1)){int[] words=new int[65];Slot s=p.slots[i];if(s!=null){words[0]=s.ranks.length;for(int at=0;at<s.ranks.length;at++)words[1+(at>>>2)]|=(s.ranks[at]&255)<<((at&3)*8);}orders.add(new Span(i*65,words));}
        return p.cached=new Frame(this,pass,metadata,orders,p.drawOrder);
    }
    public boolean valid(Frame frame,Object actualNativeList){return ownerThread()&&failure==null&&depth==0&&frame!=null&&frame.inventory==this&&frame.epoch==epoch&&frame.nativeList==actualNativeList&&nativeList==actualNativeList;}
    public void acknowledge(Frame frame){
        check();if(!valid(frame,nativeList)){invalidate("Upload acknowledgement outlived its native snapshot");throw new IllegalStateException(failure);}Pass p=passes[frame.pass];if(p.ack==p.revision&&p.dirty.isEmpty()&&p.orderDirty.isEmpty()&&!p.fullReplay)return;
        for(int i=p.dirty.nextSetBit(0);i>=0;i=p.dirty.nextSetBit(i+1))if(p.slots[i]!=null)p.slots[i].dirty.clear();p.dirty.clear();p.orderDirty.clear();p.ack=p.revision;p.fullReplay=false;p.cached=new Frame(this,frame.pass,List.of(),List.of(),p.drawOrder);
    }
    public void requestReplay(int pass){
        check();if(depth!=0||pass<0||pass>1)throw new IllegalStateException("Replay outside an idle owned preparation required");Pass p=passes[pass];for(int i=0;i<p.highWater;i++){p.dirty.set(i);p.orderDirty.set(i);if(p.slots[i]!=null)p.slots[i].dirty.set(0,256);}p.fullReplay=true;changed(pass);
    }
    public void reset(){
        if(!ownerThread())throw new IllegalStateException("Foreign world reset");invalidate("Native manager retired");advanceEpoch();regions.clear();spatial.clear();storage.clear();retired.clear();publishedRegions.clear();publishedRanks.clear();nativePrefix=List.of();nativeList=null;Arrays.fill(pending,null);depth=0;
        for(Pass p:passes){Arrays.fill(p.slots,null);p.dirty.clear();p.orderDirty.clear();p.elements.clear();p.loads.clear();p.drawOrder=List.of();p.cached=null;}
    }
    public Object manager(){check();return manager;}
}
