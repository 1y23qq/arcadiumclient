// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ActorSceneContextTest {
 @Test void capabilityIsCachedForTheActualOwningContext(){var state=new ActorSceneContext();Object owner=new Object();assertTrue(state.accept(owner,true));long epoch=state.epoch();assertTrue(state.accept(owner,false));assertEquals(epoch,state.epoch());}
 @Test void foreignContextCannotMigrateOrReleaseOwnerEvenIfNamesCouldAlias(){var state=new ActorSceneContext();Object owner=new Object(),foreign=new Object();state.accept(owner,true);long epoch=state.epoch();assertFalse(state.accept(foreign,true));assertFalse(state.owns(foreign));assertThrows(IllegalStateException.class,()->state.release(foreign));assertTrue(state.attached());assertEquals(epoch,state.epoch());assertTrue(state.accept(owner,true));}
 @Test void ownerTeardownAdvancesEpochAndAllowsANewCapabilityProbe(){var state=new ActorSceneContext();Object owner=new Object(),next=new Object();assertFalse(state.accept(owner,false));assertFalse(state.accept(next,true));long epoch=state.epoch();state.release(owner);assertFalse(state.attached());assertTrue(state.epoch()>epoch);assertTrue(state.accept(next,true));}
 @Test void missingContextDoesNotCreateAnOwnerOrResetCachedState(){var state=new ActorSceneContext();assertFalse(state.accept(null,true));assertFalse(state.attached());Object owner=new Object();state.accept(owner,true);long epoch=state.epoch();assertFalse(state.accept(null,true));assertEquals(epoch,state.epoch());assertTrue(state.owns(owner));}
}
