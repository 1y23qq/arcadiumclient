# Website assets

`public/` contains the handwritten HTML, CSS and JavaScript for the official website. No private account data is part of this directory.

The Arcadium A and launcher/client screenshots were supplied or already owned by this project.

The current FPS comparison uses three owner-supplied 1920 × 1080 screenshots from 2026-11-04: Lunar Client at 337 FPS, Badlion Client at 401 FPS and Arcadium Client at 821 FPS. The owner confirmed the same PC, graphics conditions and Hypixel Bedwars Lobby 1 for all three captures: Intel Core i7-13620H, NVIDIA GeForce RTX 4060 Laptop, 1920 × 1080 and an 8-chunk render distance. These numbers are visible screenshot readouts, not timed averages.

Original PNGs are preserved byte-for-byte in `source/comparison-2026-11-04/`. `sources.json` records their SHA-256 hashes, conditions and official logo sources; `outputs.json` records the published image hashes. `tools/build-comparison.cjs` composes the full frames with an enlarged copy of each original top-left FPS region and exports WebP images at quality 96 through Sharp. The wide version is 2400 × 576; the mobile version stacks the same panels at 800 × 1728. Only the appropriate image is loaded by the responsive picture element.

The client logos are self-hosted copies of [Lunar Client's official logo](https://www.lunarclient.com/assets/img/logo.webp), the [Badlion logo from its official resources page](https://www.badlion.net/wiki/resources), and this project's Arcadium A. Logo designs are unchanged. They identify the compared products and imply no endorsement; trademark and artwork rights remain with their respective owners.

The previous `fps-comparison-original.jpg` and `fps-comparison.svg` are retained as historical assets and are no longer displayed.

The current screenshots were supplied by the owner on 2026-10-22 and are copied without modification:

- `launcher-home-f640f51643fe.png`: `launcher.png`, 1263 × 865.
- `client-main-menu-d39da5ba1e1d.png`: `main menu.png`, 1612 × 940.
- `client-mod-menu-1b4da1abc2e1.png`: `ingame mod menu.png`, 1920 × 1080.

Their filenames include the first 12 characters of each source file's SHA-256 hash for cache invalidation. Full hashes are recorded in [the screenshot provenance file](../../licenses/assets/site-screenshots-2026-10-22.json). They retain their original aspect ratios. The older Glass theme images are retained as historical assets and are no longer displayed.

The performance chart uses a shared zero-based 1000 FPS scale. The testing configuration is collapsed by default. Arcadium bar animation follows the reduced-motion preference and pauses outside the viewport or when the page is hidden.

The current website uses the unmodified Inter variable WOFF2 from the official rsms/inter repository, pinned to commit `353b61b9f4430d5f420d56605a6e7993e0941470`. Its self-hosted filename is `Inter-Variable-693b77d4f32e.woff2`, and its complete SIL Open Font License is in `public/assets/Inter-Variable-OFL.txt`. The source URL, byte size and SHA-256 are recorded in `../../licenses/assets/site-font-inter-20261110.json`. Historical Manrope, Sora and Inter Regular assets retain their original notices. Windows, Apple and Linux glyph paths exactly match Font Awesome Free 6.7.2; the Discord glyph is an unmodified copy from the same release. The full CC BY 4.0 legal text, original package license, pinned source URLs and attribution are in `public/assets/platform-icons/`. These symbols identify the corresponding operating systems and Discord service. Trademark rights remain with their owners.

Generic interface icons use [Lucide](https://github.com/lucide-icons/lucide), pinned to commit `3fef7aca71a50acc54bd2f24f0cc4747c22aa6d2`. Original SVGs and the complete upstream ISC and Feather-derived MIT notices are retained in `public/assets/lucide/`. `public/assets/interface.svg` combines their unchanged geometry into symbols using the existing UI IDs and Lucide's 24-pixel view box and 2-pixel stroke. Active pages reference a content-fingerprinted copy to avoid stale cached symbols; full notices are also embedded in its metadata. The exact mapping, source URLs and hashes are in `../../licenses/assets/site-lucide-20261110.json`. Previous custom sprites are retained as historical GPL assets. No emoji glyphs or external icon runtime are used for interface controls.

The hero's three-dimensional arrangement is made with CSS, using the project's own screenshot files. No reference-site pictures, logos, text or source code are embedded. The launcher and main-menu windows omit the screenshots' 38-pixel decorative padding; the Options view shows the complete panel at `(216, 166, 1488, 748)` within the 1920 × 1080 capture. Those are presentation viewports; the source image files are unchanged. A bottom fade belongs to the hero layout.

The separate gallery and image-preview dialog preserve the full original frames and proportions. Gallery images use `object-fit: contain`; none are zoomed to fill their frame. The preview dialog provides a direct link to its source asset. Existing screenshot files and comparison readouts have not been altered by the redesign.

Machine-readable hashes and provenance are in `../../licenses/assets/`. The repository's GPL applies to Arcadium code, while third-party fonts and icons retain their specific licenses.
