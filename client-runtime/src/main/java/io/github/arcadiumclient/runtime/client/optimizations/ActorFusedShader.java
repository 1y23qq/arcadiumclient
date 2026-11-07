// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ActorFusedShader {
    public static final String MODEL_SHA="557ff0e51a83ece4a586a45a066dd3122851e37f2ef449e00300bd1ca6967e4c",PLAYER_SHA="20c5af9d1d3b4037bd2eb03a5a7e17a8c383d217857a3a15e1e44299c408079c";
    public static final String READER_SHA="fd8931d5fd2cbe2b02392758b4eeca2011c9d35f363549f4ed4ef73d0675a3eb";
    private static final String PLAYERS="layout(std430,binding=3) readonly buffer Players {float pd[];};";
    private static final String STATE="    float walk=pd[p+16u],speed=pd[p+17u],age=pd[p+18u],yaw=pd[p+19u],pitch=pd[p+20u],swing=pd[p+21u];";
    public record Result(boolean accepted,boolean fused,String source,String reason,int computeStorageBlocks,int maximumBinding){}
    private ActorFusedShader(){}
    public static Result compose(String model,String player,String reader,boolean enabled)throws Exception{
        int line=model.indexOf('\n');if(line<0)return new Result(false,false,model,"Unknown compute header",0,0);
        String ordinary=model.substring(0,line+1)+player+model.substring(line+1);
        if(!enabled)return new Result(true,false,ordinary,"Exact legacy composition",6,6);
        if(!sha(model).equals(MODEL_SHA)||!sha(player).equals(PLAYER_SHA)||!sha(reader).equals(READER_SHA)||count(player,PLAYERS)!=1||count(player,STATE)!=1||player.contains("arcActor")||!reader.contains("readonly buffer ArcActorInputBits")||!reader.contains("uniform uint arcActorMetadataBase;"))return new Result(false,false,ordinary,"Unknown producer/consumer source shape",0,0);
        String modified=player.replace(PLAYERS,PLAYERS+"\n"+reader).replace(STATE,STATE+"\n    arcActorPose(p,walk,speed,age,yaw,pitch);");
        return new Result(true,true,model.substring(0,line+1)+modified+model.substring(line+1),"Owned read-only UINT alias on existing binding3",7,6);
    }
    public static boolean capabilities(Result result,boolean gl43,int computeBlocks,int storageBindings,int activeBinding){return result.accepted&&(!result.fused||gl43&&computeBlocks>=7&&storageBindings>6&&activeBinding==3);}
    public static String sha(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
    private static int count(String text,String token){int result=0,offset=0;while((offset=text.indexOf(token,offset))>=0){result++;offset+=token.length();}return result;}
}
