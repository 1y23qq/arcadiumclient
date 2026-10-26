# Minecraft 1.7.10

This integration runs Forge 10.13.4.1614 with Angelica, GTNHLib and LWJGL3ify on Java 25. Added 1.7.10 Forge mods are loaded directly by FML. Minecraft 1.8.9 keeps its existing runtime.

The managed optimization dependencies also include Hodgepodge 2.7.219, CoreTweaks 0.3.4.7-GTNH and ArchaicFix 0.8.2. Missing configuration files are initialized from `src/main/resources/arcadium-defaults` before early mod loading. Existing user configuration files are preserved. Defaults select memory, chunk and startup improvements, use CoreTweaks LITE caching, and leave unrelated interface and gameplay changes disabled.

UniMixins 0.3.2 uses its official composite JAR so Forge can resolve the aggregate `unimixins` dependency required by Hodgepodge and ArchaicFix. The launch profile retains its pinned Mixin and MixinExtras libraries first on the classpath. Its hosted Mixin container is accepted only when the loaded engine JAR's SHA-256 matches the launch profile's pin; other UniMixins integrity checks remain active. Exact known copies of the superseded split modules are preserved outside the active mods folder during migration.

The Arcadium RFB integration filters ArchaicFix's unconditional collision-margin, third-person camera and window-focus mixins. Its lighting and memory fixes remain available. Official dependency JARs are not modified.

CoreTweaks LITE saves use detached snapshots of the outer cache and each transformer's entry map. This prevents background class loading from changing map membership while Kryo writes the cache. The upstream cache format, metadata and cached bytecode remain unchanged; the integration checks the pinned cache implementation and data layout before applying the snapshot.

Arcadium's shared UI and module sources are compiled with the existing mapping toolchain and reobfuscated to Forge SRG names. The private `platform` API connects their loader metadata and lifecycle calls to Forge. It does not load user Fabric mods or run a Fabric loader. The RFB plugin handles legacy ASM references before the current Mixin engine loads them.

Run `python scripts/build_client.py --java-home "$JAVA_HOME"` from the repository root. `scripts/package_client.py` builds and stages the two Forge components and generates the dependency bundle. Dependencies and mappings are verified against `distribution/forge-1.7.10.dependencies.json`; generated game APIs remain inside the ignored build cache.

For isolated Windows checks, prepare a new folder with `scripts/prepare_forge1710.py`, build `qaJar` with the Gradle wrapper and project `client-forge1710`, then run `scripts/run_forge1710_probe.py` with `--menus` or `--world`. These fixtures use a synthetic account and refuse unmarked existing profiles. QA classes are excluded from the shipped components.

Settings and player data use the shared Minecraft directory. Forge discovery and caches stay in the version's runtime directory, whose `mods` link points at its own cluster folder. Retired managed components are preserved outside that folder. Native Forge mods and 1.8.9 mods are kept separate.

Upstream notices are retained in the component JAR and `licenses/forge1710`. Official dependencies remain replaceable, unmodified external downloads.
