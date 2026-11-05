// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ResolvedUploadPolicyTest {
    @Test void unfamiliarTopLevelModsRetainTheirNormalUploadPath() {
        assertTrue(ResolvedUploadPolicy.knownMod("argentum"));
        assertTrue(ResolvedUploadPolicy.knownMod("minecraft"));
        assertTrue(ResolvedUploadPolicy.knownMod("arcadium_release_qa"));
        assertTrue(ResolvedUploadPolicy.knownMod("arcadiumclient"));
        assertFalse(ResolvedUploadPolicy.knownMod("arcadiumclient_unrelated"));
        assertFalse(ResolvedUploadPolicy.knownMod("custom_gpu_upload_mod"));
    }

    @Test void foreignMergedHooksAreNotCoveredByTheNativeContract() {
        assertTrue(ResolvedUploadPolicy.knownMixin("io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelUpload"));
        assertTrue(ResolvedUploadPolicy.knownMixin("dev.rdh.argentum.mixin.features.model.ModelPartMixin"));
        assertFalse(ResolvedUploadPolicy.knownMixin("other.client.mixin.UploadDataHook"));
        assertFalse(ResolvedUploadPolicy.service(new Object()));
    }

    @Test void onlyTheAuditedCeraArchiveClassVersionAndMixinNameAreAccepted() {
        String name = "dev.rdh.cera.mixin.ModelInstancerMixin", fingerprint = "207834988fa6f39e5de5e2f2768f98938525604f4cd69055565a241b810b888a";
        String jar = "b2dc1edd4232dbe60aac1644b0dd94be12860ab5a4a4cdc0545181309b74806f";
        assertTrue(ResolvedUploadPolicy.auditedCera(name, fingerprint, "1.0.1", jar));
        assertFalse(ResolvedUploadPolicy.auditedCera(name, "changed", "1.0.1", jar));
        assertFalse(ResolvedUploadPolicy.auditedCera(name, fingerprint, "1.0.2", jar));
        assertFalse(ResolvedUploadPolicy.auditedCera(name, fingerprint, "1.0.1", "changed"));
        assertFalse(ResolvedUploadPolicy.auditedCera("dev.rdh.cera.mixin.OtherMixin", fingerprint, "1.0.1", jar));
        assertFalse(ResolvedUploadPolicy.knownMixin(name));
    }
}
