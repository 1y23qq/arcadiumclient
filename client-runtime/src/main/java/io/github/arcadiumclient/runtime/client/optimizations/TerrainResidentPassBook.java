// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Objects;

final class TerrainResidentPassBook<S extends AutoCloseable> implements AutoCloseable {
    static final int LIMIT=2;
    final Object owner;
    private final IdentityHashMap<Object,S> scenes=new IdentityHashMap<>();
    private boolean closed,closing;

    TerrainResidentPassBook(Object owner){this.owner=Objects.requireNonNull(owner);}
    boolean live(Object manager){return !closed&&owner==manager;}
    S get(Object pass){return closed?null:scenes.get(pass);}
    boolean put(Object pass,S scene){
        Objects.requireNonNull(pass);Objects.requireNonNull(scene);
        if(closed||scenes.containsKey(pass)||scenes.size()==LIMIT)return false;
        scenes.put(pass,scene);return true;
    }
    Collection<S> values(){return scenes.values();}
    int size(){return scenes.size();}
    @Override public void close(){
        if(closing)throw new IllegalStateException("Resident pass cleanup reentered");closed=true;closing=true;
        Throwable failure=null;
        try{for(var iterator=scenes.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();try{entry.getValue().close();iterator.remove();}catch(Throwable error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}}}finally{closing=false;}
        if(failure instanceof Error error)throw error;
        if(failure instanceof RuntimeException error)throw error;
        if(failure!=null)throw new IllegalStateException("Resident terrain cleanup failed",failure);
    }
}
