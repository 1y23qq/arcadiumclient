# Build Arcadium

The source supports Minecraft 1.8.9. Build the Java client components before
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
fork and its local API, Arcadium Runtime, the Forge
compatibility layer and game branding. It then generates the launcher's component
catalog and copies the exact built JARs into its distribution assets. Original
third-party author, copyright and license metadata is preserved.

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
