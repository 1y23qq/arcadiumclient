package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.*;

public final class ActorRootSource073 {
    private ActorRootSource073(){}
    public static String compose(String ordinary,String player,String expectedModel,String expectedPlayer){
        if(!ActorRoot073.enabled())return ordinary;
        if(!sha(ordinary).equals(expectedModel)||!sha(player).equals(expectedPlayer))return ordinary;
        String declaration="layout(std430,binding=3) readonly buffer Players {float pd[];};";
        if(!player.contains(declaration)||player.contains("arcPersistentRoot"))return ordinary;
        String reader="""
layout(std430,binding=7) readonly buffer ArcPersistentRootData {uint arcPersistentRoot[];};
float arcRootRead(uint address){
    if(address<262144u)return pd[address];
    uint local=address-262144u,slot=local/32u,word=local%32u;
    if(slot>=2048u||uint(arcPersistentRoot.length())<98308u)return 0.0;
    uint root=4u+slot*32u,pose=65540u+slot*16u;
    if(arcPersistentRoot[root+12u]!=0u||arcPersistentRoot[root+13u]!=arcPersistentRoot[pose+14u]||arcPersistentRoot[root+14u]!=arcPersistentRoot[pose+15u]||arcPersistentRoot[root+15u]!=arcPersistentRoot[0])return 0.0;
    return uintBitsToFloat(word<16u?arcPersistentRoot[root+16u+word]:arcPersistentRoot[pose+word-16u]);
}
""";
        Matcher matcher=Pattern.compile("\\bpd\\[([^]\\r\\n]+)]").matcher(player);String modified=matcher.replaceAll("arcRootRead($1)");
        if(!modified.contains("arcRootRead(p+29u)"))return ordinary;
        modified=modified.replace(declaration,declaration+"\n"+reader);
        int first=ordinary.indexOf('\n');String prefix=ordinary.substring(0,first+1);int offset=prefix.length();
        if(!ordinary.startsWith(player,offset))return ordinary;
        return prefix+modified+ordinary.substring(offset+player.length());
    }
    public static String sha(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
}
