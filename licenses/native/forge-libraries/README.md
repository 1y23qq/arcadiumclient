# Forge library audit

`audit.json` records the exact three original dependencies observed in the
local Forge compatibility build identified by `nested-jar-receipt.json`.
It records the binary hashes, direct upstream license grants, original
notices and source evidence. No dependency binary is copied into this
notice directory or published by this audit. These libraries retain their
own licenses; the Forge adapter's GPL-3.0-only metadata does not relicense them.

The current adapter contains only unchanged Trove. LZMA and Vecmath are verified
official on-demand downloads, resolved only when Forge mods are present. Their
original implementations are retained, and their JARs and classes are excluded
from the adapter. The historical source-evidence limitations below therefore
do not require Arcadium to convey those two original binaries.

- `lzma:lzma:0.0.1`: the exact 5,762-byte artifact at
  `https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar`
  has SHA-256
  `0c3085cb93c3ef95d731c274b6f2dd195aa0405fe9328d69cab7f5e899d5e124`.
  It matches the LZMA JAR in official LWJGL 2.9.0 commit
  `8deac76a5b374ce2f1011c772f989c3368c935d6` byte for byte.
  That release's directly associated `doc/3rdparty/lzma_license.txt`
  grants a GNU LGPL or CPL alternative and Igor Pavlov's explicit
  independent-linking exception. The original notice does not specify
  license version numbers. The full LGPL 2.1 text is supplied as a labeled
  versioned reference, without claiming an absent original source notice.
  The Maven sources classifier is absent, and the pinned LWJGL tree contains
  the binary rather than its three Java sources. Exact corresponding source
  was not recovered. Keep this unchanged library as an official separately
  downloaded dependency instead of conveying its bytes in Arcadium's own
  wrapper unless its source-distribution requirements are resolved.
- `java3d:vecmath:1.5.2`: the original nested JAR itself contains Sun's
  complete GPL version 2 and Classpath exception, plus COPYRIGHT.txt.
  Both files are preserved byte for byte. The published
  `javax.vecmath:vecmath:1.5.2` source archive and original Sun source headers
  are pinned. This is release-version source evidence, not proof of an exact
  rebuild of the signed java3d artifact: all 47 class files differ in the
  republished javax binary. That distinction is recorded rather than hidden.
- `net.sf.trove4j:trove4j:3.0.3`: the official 3.0.3 source release
  contains the exact nested binary, complete source/build files, original
  LGPL 2.1 license and authors. Source headers explicitly allow later LGPL
  versions. The additional CERN permission notice for PrimeFinder and
  HashFunctions is preserved in `README-license.txt`; it is not absorbed
  into the general LGPL label. The original source release is pinned by
  SHA-256 `6242de1c7a8715016ca98013b456b27b0c0dbe700cd1d2e8020871bfa3829bc2`.

The receipt identifies the local artifact that was inspected, rather than
asserting that later packaging must continue to nest these libraries.
No compiled Forge adapter is published by this source-only audit.
