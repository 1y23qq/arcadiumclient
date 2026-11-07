// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class TerrainResidentResources implements AutoCloseable {
    interface Owner {void requireCurrent();}
    interface Delete {void run();}
    static final int LIMIT=6;
    private static final byte PENDING=0,ATTEMPTING=1,AMBIGUOUS=2,DELETED=3;
    private final Owner owner;
    private final Delete[] deletions=new Delete[LIMIT];
    private final byte[] outcomes=new byte[LIMIT];
    private int count;
    private boolean closing;
    TerrainResidentResources(Owner owner){if(owner==null)throw new IllegalArgumentException("Native resource owner required");this.owner=owner;}
    int add(Delete deletion){owner.requireCurrent();if(deletion==null||count==LIMIT)throw new IllegalStateException("Resident native allocation bound exceeded");deletions[count]=deletion;return count++;}
    void delete(int slot){
        owner.requireCurrent();if(slot<0||slot>=count)throw new IllegalArgumentException("Unknown resident resource");
        if(outcomes[slot]==DELETED)return;
        if(outcomes[slot]!=PENDING)throw new IllegalStateException("Ambiguous resident deletion quarantined until authoritative context retirement");
        Delete deletion=deletions[slot];outcomes[slot]=ATTEMPTING;
        try{deletion.run();outcomes[slot]=DELETED;}
        catch(Throwable failure){outcomes[slot]=AMBIGUOUS;throw failure;}
        finally{deletions[slot]=null;}
    }
    boolean empty(){return retained()==0;}
    int retained(){int n=0;for(int i=0;i<count;i++)if(outcomes[i]!=DELETED)n++;return n;}
    int ambiguous(){int n=0;for(int i=0;i<count;i++)if(outcomes[i]==AMBIGUOUS||outcomes[i]==ATTEMPTING)n++;return n;}
    int unattempted(){int n=0;for(int i=0;i<count;i++)if(outcomes[i]==PENDING)n++;return n;}
    void requireCurrent(){owner.requireCurrent();}
    @Override public void close(){
        owner.requireCurrent();if(closing)throw new IllegalStateException("Resident resource cleanup reentered");closing=true;Throwable failure=null;
        try{for(int i=count-1;i>=0;i--)try{delete(i);}catch(Throwable error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}}finally{closing=false;}
        if(failure instanceof Error error)throw error;
        if(failure instanceof RuntimeException error)throw error;
        if(failure!=null)throw new IllegalStateException("Resident resource cleanup failed",failure);
    }
    static final class AllocationFailure extends IllegalStateException {
        final TerrainResidentResources pending;
        AllocationFailure(Throwable failure,TerrainResidentResources pending){super("Resident allocation retained cleanup ownership",failure);this.pending=pending;}
    }
}
