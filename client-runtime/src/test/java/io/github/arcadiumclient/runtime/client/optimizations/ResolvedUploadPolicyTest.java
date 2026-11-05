// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ResolvedUploadPolicyTest {
    @Test void unfamiliarTopLevelModsRetainTheirNormalUploadPath() {
        assertTrue(ResolvedUploadPolicy.knownMod("argentum"));
        assertTrue(ResolvedUploadPolicy.knownMod("minecraft"));
        assertTrue(ResolvedUploadPolicy.knownMod("arcadium_release_qa"));
        assertFalse(ResolvedUploadPolicy.knownMod("custom_gpu_upload_mod"));
    }

    @Test void foreignMergedHooksAreNotCoveredByTheNativeContract() {
        assertTrue(ResolvedUploadPolicy.knownMixin("io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelUpload"));
        assertTrue(ResolvedUploadPolicy.knownMixin("dev.rdh.argentum.mixin.features.model.ModelPartMixin"));
        assertFalse(ResolvedUploadPolicy.knownMixin("other.client.mixin.UploadDataHook"));
        assertFalse(ResolvedUploadPolicy.service(new Object()));
    }
}
