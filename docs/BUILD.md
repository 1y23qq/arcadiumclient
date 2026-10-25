# Build Arcadium

The source supports Minecraft 1.8.9, 1.7.10, 1.21.11 and 26.2. Build the Java client components before
compiling the native launcher. No prebuilt Arcadium client JAR is required.

Install JDK 25, Python 3.12 or newer, Rust with Cargo, and the native build tools
for your operating system. Dependencies are downloaded from their upstream
repositories during the first build.

```sh
export JAVA_HOME=/path/to/jdk-25
python3 scripts/build_client.py
```

On Windows, set `$env:JAVA_HOME = 'C:/path/to/jdk-25'` in PowerShell and use
`python` in place of `python3`. Keep `JAVA_HOME` set for Cargo too: it compiles the
checked-in `packages/arcadium_java/assets/java/JavaInfo.java` probe into Cargo's
`OUT_DIR`. No prebuilt JavaInfo probe is required. The script builds the OneConfig
fork and its local API, Arcadium Runtime, the modern Fabric runtime, the Forge
compatibility layer and game branding. It then generates the launcher's component
catalog and copies the exact built JARs into its distribution assets. Original
third-party author, copyright and license metadata is preserved.

Minecraft 1.7.10 uses Forge with Angelica, GTNHLib and LWJGL3ify on Java 25.
Its source ports are in `client-legacy-mods`; pinned commits and original license
hashes are recorded in `client-legacy-mods/ports.json`. The same installer contains
all four Minecraft versions. Game settings, packs, worlds and Arcadium preferences
use the shared game directory; added mods and loader caches remain version-specific.
Minecraft 1.8.9 uses Ornithe with its Forge compatibility layer. Bedwars Overlay
and NoHitDelay are available on 1.8.9; LevelHead supports the two legacy versions.

Minecraft 1.21.11 uses Fabric. Its OneClient baseline, dependency hashes and
default states are pinned in `distribution/oneclient-1.21.11.lock.json`; the
approved feature selection is in `distribution/port-scope-1.21.11.json`.
Forge compatibility and the optional optimizer selector are not part of this port.

Minecraft 26.2 uses Fabric 0.20.1 and the separately built `client-next` runtime.
Its five OneClient baseline groups are pinned in `distribution/oneclient-26.2.lock.json`;
SkyBlock and individually disabled mods retain their upstream default states.
The 26.2 scope inherits the approved 1.21.11 choices. On the first launch, a supported
NVIDIA GPU in any adapter slot triggers an optional Nvidium recommendation. Enabling
it installs the pinned, unmodified Nvidium release and selects OpenGL. The choice is
remembered, and later renderer or mod changes are preserved.
The recommendation is deferred when custom launch arguments override the managed
mods folder, so it cannot install into an unused directory.

To rebuild only the modern 26.2 runtime after publishing the local Core modules:

```sh
sh client-modern/gradlew -p client-next jar test
python3 scripts/package_modern.py --minecraft-version 26.2
```

The existing wrapper is shared between the two modern runtime projects. Minecraft
26.2 is unobfuscated, so its Loom build uses no remapping stage. The runtime's
selected Idle counterpart retains its original code and notices; a recursive audit
verifies the MIT artwork replacements, including images inside nested JARs.

The runtime build refreshes Loom's remapped dependencies after publishing the
local configuration library, so changes to its API are picked up by cached builds.
Once dependencies are cached, `python3 scripts/build_client.py --offline` avoids
remote metadata refreshes while rebuilding the local client components.

To compile the launcher without creating an installer:

```sh
export SQLX_OFFLINE=true
cargo build --locked -p arcadium_app --bin Arcadium
```

Run it with `ARCADIUM_COMPONENTS_DIR` pointing to the absolute path of
`packages/arcadium_common/assets/distribution` so the development executable can
find the built components. Set `SQLX_OFFLINE=true` when building without a local
development database.

Windows installers require Visual Studio C++ build tools and NSIS 3:

```powershell
./scripts/build_windows.ps1 -JavaHome C:/path/to/jdk-25
```

macOS installers must be built on a Mac with the Xcode command-line tools:

```sh
export JAVA_HOME=/path/to/jdk-25
python3 scripts/build_macos.py --target aarch64-apple-darwin
python3 scripts/build_macos.py --target x86_64-apple-darwin
```

These commands build the Java components before Cargo. The ARM64 package targets
macOS 13 or newer; the Intel package targets macOS 12 or newer. Local macOS
packages use ad-hoc signing. Creating an installer does not publish a release.

Run packaging checks with:

```sh
python3 -m unittest discover -s scripts -p test_package_client.py
python3 -m unittest discover -s scripts -p test_prepare_public_release.py
```

Game runtime QA requires explicitly supplied library-cache and native-mod inputs.
Use `scripts/test_forge_compat_runtime.py --help` for the required arguments. On
Windows, use a short ASCII `--output-root`, such as `C:/Temp/ArcadiumQa`, so native
Skiko/ICU extraction paths stay within Win32 path limits. Tests create isolated
offline accounts and worlds; do not point their inputs at active account data.

The `arcadium_core` example `verify_arcadium` checks the real launcher catalog,
provisioning, installation and Java launch path. Set `ARCADIUM_COMPONENTS_DIR` to
the built component directory and `ARCADIUM_VERIFICATION_LAUNCHER` to the built
native launcher, then run:

```sh
cargo run --release -p arcadium_core --example verify_arcadium -- /path/to/new/test-profile --minecraft=1.7.10 --prepare --java=/path/to/jdk-25 --offline-probe
```

It uses a synthetic offline account, records the actual Java arguments and stops
its own game after startup verification. Add `--prepare-only` to check installation
without opening the game. Verification results are written inside that test profile.

Arcadium's launcher and client source are distributed under the project GPL
license. External dependencies retain their own copyright and license terms;
see `NOTICE.md`, `licenses/` and `distribution/bundle-policy.json`.

Argentum, Argentum Extras and Cera remain separate, official, unmodified download
dependencies under their original reserved-rights terms. Their source and binary
JARs are not published in this repository or covered by Arcadium's GPL license.
Tipper similarly retains its original OSL-3.0 terms. These dependencies are
outside the OneClient/Polyfrost source replacement.

Minecraft game files and the official Forge binary are downloaded locally from
their original distribution endpoints. The source distribution contains neither
the Minecraft game nor the excluded MCP mapping payload. The optional Forge
runtime is fetched only when Forge mods are used.

The unchanged LZMA 0.0.1 and Java3D Vecmath 1.5.2 support libraries are also fetched
from their official Minecraft library URLs only when Forge mods are used. Their
SHA-256 pins are checked before loading, and verified cached copies are reused.
Trove 3.0.3 stays separately nested with its original license and author notices.

The Forge source tests generate their SRG/patch fixtures locally from the pinned
official Forge download. Those test resources are never included in the
production JAR. Native compatibility fixtures come from the exact locked upstream
URLs; a read-only local cache can be supplied with
`-ParcadiumTestNativeMods=/path/to/native/mods`. Optional guest-mod cases require
user-provided JARs through `-ParcadiumTestMods=/path/to/forge/mods` or
`ARCADIUM_TEST_FORGE_MODS_DIR`. They are explicitly skipped when unavailable;
pure fixture and automatically obtained native-library checks still run.

Before publishing source, run:

```sh
python3 scripts/check_source_distribution.py
```

This checks the fresh Git source candidate list, explicit wrapper/asset/metadata
exceptions, protected directories and credential literals. It writes a local
report and performs no publication or deletion. The MRPACKs contain download
metadata and a small Fabric dependency compatibility JSON, with no game/mod
binary payloads. Source-built distribution JARs, test/download caches and private
work are ignored by Git.

Advanced game integration tools require an explicit local fixture reference and
native-mod directory. They do not assume the maintainer's old build history,
JDK cache path or private baseline commit. The optional benchmark accepts a
user-supplied baseline component directory. The encryption/cache scripts in
`ci/` and `scripts/` are legacy optional tooling, not required for these public
source build commands.
