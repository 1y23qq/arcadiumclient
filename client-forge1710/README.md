# Minecraft 1.7.10

This integration runs Forge 10.13.4.1614 with Angelica, GTNHLib and LWJGL3ify on Java 25. Added 1.7.10 Forge mods are loaded directly by FML. Minecraft 1.8.9 keeps its existing runtime.

Arcadium's shared UI and module sources are compiled with the existing mapping toolchain and reobfuscated to Forge SRG names. The private `platform` API connects their loader metadata and lifecycle calls to Forge. It does not load user Fabric mods or run a Fabric loader. The RFB plugin handles legacy ASM references before the current Mixin engine loads them.

Run `python scripts/build_client.py --java-home "$JAVA_HOME"` from the repository root. `scripts/package_client.py` builds and stages the two Forge components and generates the dependency bundle. Dependencies and mappings are verified against `distribution/forge-1.7.10.dependencies.json`; generated game APIs remain inside the ignored build cache.

For isolated Windows checks, prepare a new folder with `scripts/prepare_forge1710.py`, build `qaJar` with the Gradle wrapper and project `client-forge1710`, then run `scripts/run_forge1710_probe.py` with `--menus` or `--world`. These fixtures use a synthetic account and refuse unmarked existing profiles. QA classes are excluded from the shipped components.

Settings and player data use the shared Minecraft directory. Forge discovery and caches stay in the version's runtime directory, whose `mods` link points at its own cluster folder. Retired managed components are preserved outside that folder. Native Forge mods and 1.8.9 mods are kept separate.

Upstream notices are retained in the component JAR and `licenses/forge1710`. Official dependencies remain replaceable, unmodified external downloads.
