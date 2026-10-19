# Dependency evidence

These are original license/notice texts and public source/release metadata used to corroborate the four artifacts listed in [dependency-corroboration.json](../dependency-corroboration.json). They retain their original licenses. Copies of upstream build files are evidence, not alternative Arcadium build entry points.

Pylon and Sarcio have exact release tags and official asset digests that match the locked binaries. ItemPhysicLite has an exact release tag and matching Modrinth hashes. SmartSearch has no release tags; its pinned source commit is the last commit before publication and declares version 1.2.0. That source selection is an inference, not a reproducible-build claim.

The nested Sarcio/SmartSearch binaries were compared with official Maven artifacts. All 6,613 class/native entries match. Their original license and notice files were recovered from the corresponding release source or embedded Maven notices. Pylon's 16 native binaries match LWJGL 3.4.3, while its six intentional LWJGL class patches are documented in the original build source. The OpenAL binaries identify OpenAL Soft 1.25.2; its original copyright header grants LGPL version 2 or later. Original OpenAL, SDL, LWJGL, ASM, libffi/liburing and Khronos notices are retained separately.

SmartSearch's embedded Snowflake ONNX weights and tokenizer match the pinned model repository's Git/LFS objects. The original model card's License section grants Apache License 2.0; the separate model LICENSE path returned 404, so the original model card and an unchanged copy of the Apache license are retained as evidence.

This corroboration does not by itself complete an installer license audit. Binary distributions must accompany the applicable notices and provide complete corresponding sources for GPL/LGPL components, including native libraries, with library replacement/rebuild paths available. The main publication audit handles those obligations.
