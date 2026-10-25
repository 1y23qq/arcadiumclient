# Notices

Arcadium is a modified distribution maintained by 0aurorq3 and contributors.
The launcher and Arcadium-maintained client source are conveyed under
GPL-3.0-only. Individual dependencies and separately licensed files retain
their own notices and licenses. No rights in third-party names or trademarks
are granted.

## Launcher

Derived from Polyfrost/OneLauncher at
5a8728ef04383a578c258dc9107f7132f6797e1a, copyright 2023-2026 Polyfrost and
contributors, GPL-3.0-only. Arcadium changes made in October 2026 include
branding, authentication, distribution, updates, platform support and interface
changes. Original contributor and copied-library notices are retained in
[UPSTREAM-ATTRIBUTION.md](licenses/UPSTREAM-ATTRIBUTION.md).

## Configuration interface

client-ui is a modified OneConfig library based on
97df4a7651881bbf7ab899aeba7af4f428154a38, copyright 2021-2026 Polyfrost and
contributors. Its original LGPLv3 and additional terms remain in
[client-ui/LICENSE](client-ui/LICENSE). This adaptation is conveyed under GNU
GPL version 3 using section 5 of those additional terms. The original notices
and the additional terms are preserved. Arcadium changes made in October 2026
include themes, input, fonts, module presentation and legacy-game integration.

## Dependencies and assets

The independently implemented client runtime is in `client-runtime/`. It does
not contain the PolyPlus implementation. PolyPlus, ChatTweaks, PolySprint and
CrashPatch are excluded from the generated distribution. Existing configuration
identifiers are retained where needed for migration; they are not claims of
upstream authorship or endorsement.

The modern runtimes are in `client-modern/` and `client-next/`. Their Glint Colorizer configuration
adapts Polyfrost's GPL-3.0 GlintColorizer definitions; the original license is in
`client-legacy-mods/glintcolorizer/LICENSE`. The modern rendering code and
shaders are independently implemented. BetterHurtCam's MIT notice is retained
in `client-modern/src/main/resources/META-INF/arcadium-modern-notices.txt`.
The selected Idle/FPS feature uses Dynamic FPS 3.11.6 and 3.11.9 under MIT. Its artwork is
replaced with Arcadium assets derived from MIT-licensed Feather geometry;
the code and asset notices are retained in the nested component and in
`client-modern/src/idleAssets/licenses/`.

The Minecraft 1.21.11 bundle metadata is adapted from Polyfrost's
[DataStorageV2](https://github.com/Polyfrost/DataStorageV2/tree/a8e6ec3ce5c80ca7a69245f43912adefdd1b862f),
copyright Polyfrost and contributors, under GPL version 3. Original dependency
download URLs, hashes and default states are retained in
`distribution/oneclient-1.21.11.lock.json`, with Arcadium's exclusions recorded
there. This metadata contains no third-party mod or shader binaries; downloaded
dependencies retain their respective licenses.

The Minecraft 26.2 bundle metadata follows
[DataStorageV2](https://github.com/Polyfrost/DataStorageV2/tree/45403497daf6ebfcd284c05ac1252d02a427dca8)
under the same GPL-3.0 terms. Exact versions and defaults are recorded in
`distribution/oneclient-26.2.lock.json`. Optional Nvidium is downloaded separately
and unmodified under LGPL-3.0-only; its license and source reference remain in
the downloaded JAR and the lock file.

Exact external download versions, hashes, authors, source references and
original notices are recorded in [licenses/bundled/](licenses/bundled/).
Argentum, Argentum Extras and Cera retain their reserved-rights terms and are
downloaded unchanged from their official endpoints. Neither their source nor
their binary JARs are included in this repository or covered by Arcadium's GPL
license. Tipper retains its separate OSL-3.0 terms.

Rust dependencies and their original or explicitly identified reference notices
are in [licenses/rust/](licenses/rust/). Native SDK notices are in
[licenses/native/](licenses/native/). Original OneConfig and embedded library
notices are preserved in the corresponding source trees and build outputs.

Font and icon provenance and full notices are in [licenses/assets/](licenses/assets/).
Inter, Monocraft, Poppins, JetBrains Mono and Unifont retain their SIL Open Font
License terms. Font Awesome icons retain their CC BY 4.0 attribution. The
Arcadium brand assets are supplied by the project owner. Minecraft game files,
default skin textures and third-party restricted client code are not part of
this source distribution.

The additional title panoramas in `client-runtime/src/main/resources/assets/arcadium/panoramas/`
are recordings supplied by the project owner. Their source-set identifiers and
original image hashes are retained in the accompanying `provenance.json`.

The bundled emoji atlas contains selected Twemoji graphics by Twitter, Inc.
and contributors, under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
The PNGs were resized and combined into an atlas. Source hashes, changes and
original authors and license are in [licenses/assets/twemoji/](licenses/assets/twemoji/).

The optional Forge compatibility runtime retrieves the original official Forge
artifact locally when Forge mods are used. Game patches and MCP mapping data
are derived only in local build/test or game caches and are not conveyed in the
source repository or Arcadium's production JAR. Forge, FML and decoding-library
notices are preserved in [licenses/](licenses/) and
[licenses/forge-compat/](licenses/forge-compat/).

Minecraft 1.7.10 uses native Forge, Angelica, GTNHLib, LWJGL3ify and modular
UniMixins dependencies. Their exact versions, source links and download hashes
are recorded in `distribution/forge-1.7.10.dependencies.json`; original notices
are retained in [licenses/forge1710/](licenses/forge1710/). These dependencies
remain unmodified external downloads under their respective licenses. Arcadium's
Forge integration is in `client-forge1710/`, with retained library notices in
the merged component. Minecraft and Forge game patches are not embedded in it.

The unchanged LZMA and Vecmath libraries are also obtained from their official
endpoints when Forge mods are used; their bytes are excluded from the Arcadium
adapter. Trove retains its original LGPL and CERN notices. Exact grants, hashes
and source evidence are in [licenses/native/forge-libraries/](licenses/native/forge-libraries/).

This software is provided without warranty. See LICENSE for redistribution and
source-code rights. Support: https://discord.gg/QbNfaCHjUz
