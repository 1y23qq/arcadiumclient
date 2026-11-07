package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.file.*;

public final class ActorRootOff073Test {
    public static void main(String[] args)throws Exception{
        if(ActorRoot073.enabled()||ActorRoot073.hasJobs()||ActorRoot073.bind()!=null)throw new IllegalStateException("Private lane must default off without GL");
        Path path=Path.of(args[0]);String ordinary=Files.readString(path.resolve("ordinary_model.comp")),player=Files.readString(path.resolve("player_pose.glsl"));
        if(!ActorRootSource073.compose(ordinary,player,ActorRootSource073.sha(ordinary),ActorRootSource073.sha(player)).equals(ordinary))throw new IllegalStateException("Default shader changed");
        System.out.println("{\"passed\":true,\"defaultOff\":true,\"shaderByteExact\":true,\"noGlCalls\":true}");
    }
}
