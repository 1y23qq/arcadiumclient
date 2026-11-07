// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ActorFusedMode {
    static final boolean OLD_REQUESTED=Boolean.getBoolean("arcadium.actorScene073.enabled");
    static final boolean FUSED_REQUESTED=Boolean.getBoolean("arcadium.actorScene073.fusedInput");
    static final boolean OLD=old(OLD_REQUESTED,FUSED_REQUESTED),FUSED=fused(OLD_REQUESTED,FUSED_REQUESTED);
    static boolean old(boolean old,boolean fused){return old&&!fused;}
    static boolean fused(boolean old,boolean fused){return fused&&!old;}
    private ActorFusedMode(){}
}
