package io.github.arcadiumclient.runtime.client.optimizations;

public final class PreparedVoxelLease073 {
    final Object main,capture,parts,texture,fact;final Object[] meshes,geometry;final int mask,sourceTemplate,variantTemplate;final long epoch;private boolean consumed;
    private final boolean nativeProjectionQualified;
    PreparedVoxelLease073(Object main,Object capture,Object parts,Object texture,Object fact,Object[] meshes,Object[] geometry,int mask,int sourceTemplate,int variantTemplate,long epoch,boolean qualified){this.main=main;this.capture=capture;this.parts=parts;this.texture=texture;this.fact=fact;this.meshes=meshes;this.geometry=geometry;this.mask=mask;this.sourceTemplate=sourceTemplate;this.variantTemplate=variantTemplate;this.epoch=epoch;nativeProjectionQualified=qualified;}
    public boolean projectionQualified(){return nativeProjectionQualified;}
    boolean consume(Object main,Object capture,Object parts,Object texture,int sourceTemplate,long epoch){if(consumed)return false;consumed=true;return nativeProjectionQualified&&this.main==main&&this.capture==capture&&this.parts==parts&&this.texture.equals(texture)&&this.sourceTemplate==sourceTemplate&&this.epoch==epoch;}
}
