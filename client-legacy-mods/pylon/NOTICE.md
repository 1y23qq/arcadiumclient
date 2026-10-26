# Pylon

Based on Polyfrost/Pylon 0.2.2-beta.1, commit
7c7cb32d4780cd4c69841f3d96a23adc03b08044.
Original authors: notdevcody and Pylon contributors. LGPL-3.0-only.
The original license is preserved in LICENSE.md; COPYING contains GNU GPLv3.

Arcadium modifications, 27 October 2026:

- GameTransformer uses a constant-pool filter before the original patch scan.
  Classes that may match, unsupported versions and unrecognized constant-pool
  data still use the original transformer. Patch implementations are unchanged.
- PatchSymbols and its tests are new. All retain LGPL-3.0-only terms.
- The version and metadata identify the Arcadium adaptation. Builds exclude upstream development runs,
  use the repository's game-branding Gradle wrapper, preserve license notices
  in the JAR and create reproducible archives.

Complete modified sources, build logic and dependency versions are in this
directory. From the repository root, build with JDK 25:

    game-branding/gradlew -p client-legacy-mods/pylon build

On Windows use game-branding/gradlew.bat. This invokes the original relocation
and LWJGL compatibility build. Tests compare the optimized transformer with
the original implementation. The produced library is a separate JAR. To change
the launcher-managed copy, rebuild it and run scripts/package_client.py before
building the launcher.

Upstream: https://github.com/Polyfrost/pylon/tree/7c7cb32d4780cd4c69841f3d96a23adc03b08044
Modified source: https://github.com/1y23qq/arcadiumclient/tree/main/client-legacy-mods/pylon
Support: https://discord.gg/QbNfaCHjUz
