package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.file.*;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PreparedPlayerContractsTest {
    @Test void nativeShellAndExactBlockContracts()throws Exception{
        Path mc=Path.of(HumanoidModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        PreparedPlayerEmission073Test.main(new String[]{mc.toString(),Files.createTempDirectory("arcadium-prepared-asm-").toString()});
    }
    @Test void liveMaterialAndAllocationRollbackRegressions()throws Throwable{
        try{PreparedPlayerR2RegressionTest.main(new String[0]);}finally{GpuPlayerPackets.beginBatch();}
    }
    @Test void privateModeNeverAdoptsConflictingActorModes(){
        for(int mask=0;mask<16;mask++)assertEquals(mask==1,PreparedPlayerMode073.enabled((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0));
    }
    @Test void bootstrapContainsNoUnreviewedFinalApproval(){
        assertEquals(0,PreparedPlayerBootstrap073.approvalCount());
    }
}
