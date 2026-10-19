# Native and embedded Java dependency notices

These notices apply to separately licensed dependencies. Arcadium's GPL-3.0-only
license for its own code does not relicense these libraries, fonts, SDKs, or
system runtimes. Original copyright and permission texts remain unchanged.
The JSON records distinguish published binary hashes, source-release evidence,
and source-based dependency inventories; they do not assert a reproducible
native build where one has not been performed.

## Compose and Skiko

`compose-skiko/bundled-artifacts.json` records the 48 nested JARs in the exact
Compose bundle, their extracted original notices, and the six Skiko native
library hashes. AndroidX/Compose, JBR API 1.9.0, JSpecify 1.0.0, and Skiko
0.154.0 retain their Apache-2.0 notices. The JBR API is a separately licensed
API artifact; it is not labeled with an assumed JDK license.

Skiko's source is pinned to `a0c3d414a614b90810f3fc1654a8e8a18dbd983d`.
Its native dependency identifies Skia `m154-ab5932137b`.
`skia-native-notice-provenance.json` records original notices at the dependency
revisions in that Skia release's DEPS file. The original Skia BSD license,
Skiko Apache license and NOTICE, and native build configuration are retained
under `source-evidence/`. Optional tools listed by DEPS are not automatically
claimed to be linked into the runtime.

## Rust Skia

`rust-skia/source-provenance.json` records freya-skia-bindings
0.100.0-beta.1 and its Skia source tag `m153-0.101.2`. Shared native component
pins are identical to the corresponding pins documented for Skiko. Additional
Brotli, Vulkan allocator, SPIRV-Cross, Wuffs, and graphics-header notices are
retained under `rust-skia/original-notices/`. Their exact source revisions and
the selected Cargo features are recorded separately.

The FreeType License alternative is selected for FreeType. Portions of this
software are copyright 1996-2026 The FreeType Project
(https://freetype.org). All rights reserved. The date comes from the actual
pinned FreeType header. Its original FTL, alternative GPL text, and component
notices are retained; keeping the alternative text does not select it.

This software is based in part on the work of the Independent JPEG Group.
The original libjpeg-turbo and IJG notices are retained.

This product includes DNG technology under license by Adobe Systems
Incorporated. The original Adobe DNG SDK agreement is retained without
modification. It is a separately licensed native dependency, not Arcadium
code conveyed under GPL. Its agreement grants software distribution and
requires preservation of the original notices. No Adobe documentation is
modified or incorporated into Arcadium's own documentation.

## WebView2

`webview2-sdk/provenance.json` pins Microsoft.Web.WebView2 SDK
1.0.3800.47. All nine bundled loader/import/static-library files in
webview2-com-sys 0.39.1 match that official NuGet package byte for byte.
Microsoft's original SDK LICENSE is BSD-3-Clause; both it and NOTICE.txt
are retained. The Rust wrapper's MIT license is separate. The WebView2
Runtime remains a separate Microsoft runtime dependency and is not
relicensed by Arcadium.

## Pack200 and XZ

`pack200/` corroborates org.glavo:pack200:0.3.0 as
GPL-2.0-only WITH Classpath-exception-2.0 using the original Oracle source
headers, published Maven sources, release tag, and complete original OpenJDK
license and exception. The exception permits linking independent modules
under their own chosen terms. Pack200 itself retains its original license;
the exception does not remove its corresponding-source conditions.

The exact published source archive is pinned. Twenty-five of its 26 Java
files match the release tag byte for byte; the remaining package-info.java
contains only a documented Javadoc repair. If a future distribution conveys
Pack200 in a shaded binary, retain these notices and provide the exact
corresponding library source and build information with that distribution.
No Forge adapter binary is being published as part of this source snapshot.

`xz-java/` corroborates org.tukaani:xz:1.10 under 0BSD. All 116 published
Java source files, including the Java 9 multi-release sources, match its
official v1.10 tag. The original COPYING, 0BSD license and AUTHORS are retained.
