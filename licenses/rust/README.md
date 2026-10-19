# Rust dependency notices

`manifest.json` records the locked external Cargo packages, their original
SPDX expressions and authors, and the notices retained for each package.
The project license does not replace these dependency licenses.

There are 867 packages and 1,466 retained notice files. The initial published
crate notices were supplemented with 104 original notice sets recovered from
the exact source revision recorded by Cargo, a matching published workspace
member, or the release evidence in `upstream-overrides.json`.
`upstream-resolutions.json` records the source and SHA-256 for each recovery.
No current upstream branch was substituted for an older dependency.

Six upstream releases contain an explicit MIT or MPL-2.0 declaration but omit
a standalone license file: block 0.1.6, dispatch 0.2.0, drm-fourcc 2.2.0,
malloc_buf 0.0.6, selectors 0.38.0, and simd_helpers 0.1.0. Their published
Cargo metadata and available original source headers are retained. The full
canonical license references in `_standard/` are identified as references;
they are not represented as recovered upstream copyright notices. No author
or copyright year was invented.

For Interfrost, Arcadium selects the MIT alternative from the original
alternative license expression. The original expression and all recovered
license texts remain available. For zune-core, the Zlib alternative is selected.

`scripts/resolve_rust_license_notices.py` can repeat the recovery using locked
Cargo metadata and the pinned overrides. It writes only inside its specified
notice directory. `verification.json` records the completed local hash check.
Native Skia and WebView2 SDK notices are separately documented in
[`../native/README.md`](../native/README.md); a Rust wrapper's license alone
does not establish the license of its native dependencies.
