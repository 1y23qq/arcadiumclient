// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentPlayerCapabilityTest {
    @Test void disabledOrPreviouslyRejectedDevicesNeedNoCurrentGlContextOrStorage() throws Exception {
        Field tested=field("tested"),supported=field("supported"),failed=field("failed");
        boolean oldEnabled=GpuResidentPlayerState.enabled,oldTested=tested.getBoolean(null),oldSupported=supported.getBoolean(null),oldFailed=failed.getBoolean(null);
        long bytes=GpuResidentPlayerState.residentBytes(),binds=GpuResidentPlayerState.binds;
        try {
            GpuResidentPlayerState.enabled=false;tested.setBoolean(null,false);failed.setBoolean(null,false);
            assertFalse(GpuResidentPlayerState.canRetainActors());
            assertFalse(tested.getBoolean(null));
            GpuResidentPlayerState.enabled=true;tested.setBoolean(null,true);supported.setBoolean(null,false);
            for(int frame=0;frame<1000;frame++)assertFalse(GpuResidentPlayerState.canRetainActors());
            tested.setBoolean(null,false);failed.setBoolean(null,true);
            assertFalse(GpuResidentPlayerState.canRetainActors());
            assertFalse(tested.getBoolean(null));
            assertEquals(bytes,GpuResidentPlayerState.residentBytes());assertEquals(binds,GpuResidentPlayerState.binds);
        } finally {
            GpuResidentPlayerState.enabled=oldEnabled;tested.setBoolean(null,oldTested);supported.setBoolean(null,oldSupported);failed.setBoolean(null,oldFailed);
        }
    }
    private static Field field(String name)throws Exception { Field field=GpuResidentPlayerState.class.getDeclaredField(name);field.setAccessible(true);return field; }
}
