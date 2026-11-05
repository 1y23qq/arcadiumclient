// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuModelAuxiliariesTest {
    @Test void onlyIndependentOwnedBackendsAreRecognized() {
        Object main=new Object(),auxiliary=new Object(),other=new Object();
        try {
            assertFalse(GpuModelAuxiliaries.isAuxiliaryBackend(main));
            assertTrue(GpuModelAuxiliaries.register(main,auxiliary));
            assertTrue(GpuModelAuxiliaries.register(main,auxiliary));
            assertTrue(GpuModelAuxiliaries.isAuxiliaryBackend(auxiliary));
            assertFalse(GpuModelAuxiliaries.register(other,auxiliary));
            assertFalse(GpuModelAuxiliaries.register(auxiliary,other));
            assertFalse(GpuModelAuxiliaries.register(other,main));
            assertFalse(GpuModelAuxiliaries.register(main,main));
        } finally {GpuModelAuxiliaries.unregister(auxiliary);GpuModelAuxiliaries.unregister(other);GpuModelAuxiliaries.unregister(main);}
    }
    @Test void boundedCapacityIsReusableOnlyAfterExplicitCloseUnregistration() {
        Object main=new Object();Object[] auxiliary=new Object[GpuModelAuxiliaries.LIMIT+1];
        for(int i=0;i<auxiliary.length;i++)auxiliary[i]=new Object();
        int before=GpuModelAuxiliaries.size();assertEquals(0,before);
        try {
            for(int i=0;i<GpuModelAuxiliaries.LIMIT;i++)assertTrue(GpuModelAuxiliaries.register(main,auxiliary[i]));
            assertEquals(GpuModelAuxiliaries.LIMIT,GpuModelAuxiliaries.size());
            assertFalse(GpuModelAuxiliaries.register(main,auxiliary[GpuModelAuxiliaries.LIMIT]));
            GpuModelAuxiliaries.unregister(auxiliary[1]);assertFalse(GpuModelAuxiliaries.isAuxiliaryBackend(auxiliary[1]));
            assertTrue(GpuModelAuxiliaries.register(main,auxiliary[GpuModelAuxiliaries.LIMIT]));
        } finally {for(Object backend:auxiliary)GpuModelAuxiliaries.unregister(backend);}
        assertEquals(before,GpuModelAuxiliaries.size());
    }
    @Test void equalValuesCannotAliasNativeOwnerIdentities() {
        Object main=new String("same"),auxiliary=new String("same");
        try {
            assertTrue(GpuModelAuxiliaries.register(main,auxiliary));
            assertFalse(GpuModelAuxiliaries.isAuxiliaryBackend(main));
            assertTrue(GpuModelAuxiliaries.isAuxiliaryBackend(auxiliary));
        } finally {GpuModelAuxiliaries.unregister(auxiliary);}
        assertThrows(NullPointerException.class,()->GpuModelAuxiliaries.register(null,new Object()));
    }
}
