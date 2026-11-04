# Website assets

`public/` contains the handwritten website source restored from stable Arcadium 0.3.6. No private account data is part of this directory.

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

Manrope, Sora and Inter are distributed under their original SIL Open Font License 1.1 notices in `public/assets/`. Windows, Apple and Linux glyph paths exactly match Font Awesome Free 6.7.2; the Discord glyph is an unmodified copy from the same release. The full CC BY 4.0 legal text, original package license, pinned source URLs and attribution are in `public/assets/platform-icons/`. These symbols identify the corresponding operating systems and Discord service. Trademark rights remain with their owners.

Machine-readable hashes and provenance are in `../../licenses/assets/`. The repository's GPL applies to Arcadium code, while third-party fonts and icons retain their specific licenses.
