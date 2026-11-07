package io.github.arcadiumclient.runtime.qa;

import java.util.*;

public final class NativeMatrixTrace073 {
    public static final int MODE=1,IDENTITY=2,PUSH=3,POP=4,TRANSLATE_F=5,SCALE_F=6,ROTATE_F=7,
            MULT_F=8,LOAD_F=9,TRANSLATE_D=10,SCALE_D=11,ROTATE_D=12,MULT_D=13,LOAD_D=14,ORTHO=15,FRUSTUM=16,UNKNOWN=99;
    public record Pending(Object owner,long view,int model,int opcode,String site,int[] raw32,long[] raw64,
                          int mode,int depth,int part) {}
    private final List<Map<String,Object>> models=new ArrayList<>(),submissions=new ArrayList<>(),uploads=new ArrayList<>();
    private final IdentityHashMap<Object,Integer> partIds=new IdentityHashMap<>();
    private final Object[] partStack=new Object[16];
    private final int[] partScales=new int[16];
    private final Map<Integer,Integer> meshParts=new HashMap<>();
    private Map<String,Object> active;
    private Object owner;
    private long view;
    private int mode,depth,partDepth;
    private String variant="off",failure;

    public void begin(long token,String variant) {
        if(owner!=null||partDepth!=0)throw new IllegalStateException("Previous matrix trace scope survived");
        view=token;this.variant=variant;failure=null;models.clear();submissions.clear();uploads.clear();partIds.clear();meshParts.clear();
    }
    public boolean active(){return owner!=null;}
    public Object owner(){return owner;}
    public void model(Object capture,int[] root,int[] projection,int actualMode,int absoluteDepth) {
        if(owner!=null) {fail("Nested model root");return;}
        if(models.size()==16||capture==null||actualMode!=5888||absoluteDepth<1||absoluteDepth>64||!finite(root)||!finite(projection)){fail("Invalid bounded physical model seed");return;}
        owner=capture;mode=actualMode;depth=partDepth=0;active=new LinkedHashMap<>();
        active.put("modelId",models.size()+1);active.put("viewToken",view);active.put("rootPhysicalRawBits",root.clone());
        active.put("projectionRawBits",projection.clone());active.put("rootMatrixMode",actualMode);active.put("absoluteRootStackDepth",absoluteDepth);
        active.put("operations",new ArrayList<Map<String,Object>>());active.put("leaves",new ArrayList<Map<String,Object>>());
        models.add(active);
    }
    public void part(Object part,int scaleBits,boolean enter) {
        if(owner==null)return;
        if(enter) {
            if(part==null||partDepth==partStack.length||!Float.isFinite(Float.intBitsToFloat(scaleBits))){fail("Invalid native part scope");return;}
            if(!partIds.containsKey(part)){if(partIds.size()==64){fail("Part identity budget");return;}partIds.put(part,partIds.size()+1);}
            partStack[partDepth]=part;partScales[partDepth++]=scaleBits;
        } else {
            if(partDepth==0||partStack[partDepth-1]!=part){fail("Native part scope association changed");return;}
            partStack[--partDepth]=null;
        }
    }
    public Pending prepare(int opcode,String site,int[] raw32,long[] raw64) {
        if(owner==null)return null;
        if(ops().size()==512||site==null||site.length()>512||raw32.length>16||raw64.length>16){fail("Matrix operation budget");return null;}
        return new Pending(owner,view,(int)active.get("modelId"),opcode,site,raw32.clone(),raw64.clone(),mode,depth,
                partDepth==0?0:partIds.get(partStack[partDepth-1]));
    }
    public void completed(Pending pending) {
        if(pending==null)return;
        if(owner!=pending.owner||view!=pending.view||active==null||(int)active.get("modelId")!=pending.model||mode!=pending.mode||depth!=pending.depth){fail("Native call reentered or crossed model scope");return;}
        if(pending.opcode==MODE) {
            if(pending.raw32.length!=1||!Set.of(5888,5889,5890).contains(pending.raw32[0])){fail("Unknown actual matrix mode");return;}
            mode=pending.raw32[0];
        } else if(mode==5888) {
            if(pending.opcode==PUSH){if(++depth==32)fail("Actual matrix stack budget");}
            if(pending.opcode==POP){if(--depth<0)fail("Actual matrix stack underflow");}
        }
        Map<String,Object> value=new LinkedHashMap<>();value.put("sequence",ops().size());value.put("opcode",pending.opcode);
        value.put("site",pending.site);value.put("raw32",pending.raw32);value.put("raw64",pending.raw64);
        value.put("modeBefore",pending.mode);value.put("modeAfter",mode);value.put("relativeDepthBefore",pending.depth);
        value.put("relativeDepthAfter",depth);value.put("partId",pending.part);value.put("actualOriginalReturned",true);
        ops().add(value);
        if(pending.opcode==UNKNOWN)fail("Unsupported native matrix call was observed");
    }
    public Map<String,Object> leaf(Object capture,int meshId,int scaleBits,int[] physical,int[] projection) {
        if(owner==null||owner!=capture||partDepth==0||mode!=5888||meshId<1||!finite(physical)||!finite(projection)){fail("Actual leaf lacks its live native model/part scope");return Map.of("associationPassed",false);}
        int part=partIds.get(partStack[partDepth-1]);Integer prior=meshParts.putIfAbsent(meshId,part);
        if(prior!=null&&prior!=part)fail("One mesh identity changed native part owner");
        if(leaves().size()==32){fail("Native leaf budget");return Map.of("associationPassed",false);}
        Map<String,Object> value=new LinkedHashMap<>();value.put("modelId",active.get("modelId"));value.put("viewToken",view);
        value.put("meshId",meshId);value.put("partId",part);value.put("partScaleRawBits",partScales[partDepth-1]);value.put("meshScaleRawBits",scaleBits);
        value.put("operationCount",ops().size());value.put("relativeDepth",depth);value.put("trackedMatrixMode",mode);
        value.put("physicalRawBits",physical.clone());value.put("projectionRawBits",projection.clone());
        value.put("frameProjectionRawMatch",Arrays.equals((int[])active.get("projectionRawBits"),projection));
        value.put("associationPassed",prior==null||prior==part);value.put("snapshotSource","Existing actual GlStateManager GL11.glCallList pre-call snapshot; no new per-leaf query");
        if(!Boolean.TRUE.equals(value.get("frameProjectionRawMatch")))fail("Projection changed inside the owned view");
        leaves().add(value);return value;
    }
    public void submission(int meshId,int part,int[] physical,int[] projection) {
        if(owner==null||mode!=5888||partDepth==0||submissions.size()==32||!finite(physical)||!finite(projection)){fail("GPU submission lacks a bounded native operation scope");return;}
        Map<String,Object> value=new LinkedHashMap<>();value.put("modelId",active.get("modelId"));value.put("operationCount",ops().size());
        value.put("meshId",meshId);value.put("observedMeshPart",part);value.put("partId",partIds.get(partStack[partDepth-1]));
        value.put("physicalRawBits",physical.clone());value.put("projectionRawBits",projection.clone());submissions.add(value);
    }
    public void upload(int count,int[] words) {
        if(count<1||count>32||words.length!=count*19||uploads.size()==8){fail("Nineteen-word upload range changed");return;}
        uploads.add(Map.of("count",count,"instanceWords",words.clone(),"wordsPerInstance",19,"originalInputUnchanged",true));
    }
    public void endModel(Object capture,boolean closing) {
        if(owner==null)return;
        if(owner!=capture||closing||depth!=0||partDepth!=0)fail("Model/native stack did not close normally");
        active.put("balanced",owner==capture&&!closing&&depth==0&&partDepth==0);active.put("endingMatrixMode",mode);
        owner=null;active=null;depth=partDepth=0;Arrays.fill(partStack,null);
    }
    public Map<String,Object> end() {
        if(owner!=null||partDepth!=0)fail("Native operation scope leaked past view");
        int leafCount=models.stream().mapToInt(m->((List<?>)m.get("leaves")).size()).sum();
        int operationCount=models.stream().mapToInt(m->((List<?>)m.get("operations")).size()).sum();
        if(models.isEmpty()||operationCount==0)fail("No actual model roots or completed native operations");
        if(Set.of("native","native-repeat","body-native3d").contains(variant)&&leafCount!=12)fail("Expected twelve actual native leaf snapshots");
        if(Set.of("full","voxel-affine","optimized").contains(variant)&&submissions.size()!=12)fail("Expected twelve actual native-capture voxel submissions");
        Map<String,Object> result=new LinkedHashMap<>();result.put("viewToken",view);result.put("variant",variant);result.put("models",new ArrayList<>(models));
        result.put("submissions",new ArrayList<>(submissions));result.put("cpuUploads",new ArrayList<>(uploads));result.put("modelCount",models.size());
        result.put("actualCompletedOperationCount",operationCount);result.put("actualNativeLeafCount",leafCount);result.put("failure",failure);
        result.put("recordingPassed",failure==null);result.put("productionQualified",false);result.put("perLeafQueriesAdded",0);
        owner=null;active=null;partDepth=depth=0;variant="off";Arrays.fill(partStack,null);return result;
    }
    public void fail(String value){if(failure==null)failure=value;}
    private List<Map<String,Object>> ops(){return (List<Map<String,Object>>)active.get("operations");}
    private List<Map<String,Object>> leaves(){return (List<Map<String,Object>>)active.get("leaves");}
    private static boolean finite(int[] bits){if(bits==null||bits.length!=16)return false;for(int value:bits)if(!Float.isFinite(Float.intBitsToFloat(value)))return false;return true;}
}
