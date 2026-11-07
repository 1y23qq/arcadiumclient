package io.github.arcadiumclient.runtime.client.optimizations;

public final class PreparedPlayerMode073 {
    public static final boolean REQUESTED=Boolean.getBoolean("arcadium.preparedPlayer073.enabled");
    public static final boolean ACTOR_ROOT=Boolean.getBoolean("arcadium.actorRoot073.enabled");
    public static final boolean ACTOR_SCENE=Boolean.getBoolean("arcadium.actorScene073.enabled");
    public static final boolean FUSED_INPUT=Boolean.getBoolean("arcadium.actorScene073.fusedInput");
    public static final boolean ENABLED=enabled(REQUESTED,ACTOR_ROOT,ACTOR_SCENE,FUSED_INPUT);
    private PreparedPlayerMode073(){}
    public static boolean enabled(boolean requested,boolean root,boolean scene,boolean fused){return requested&&!root&&!scene&&!fused;}
}
