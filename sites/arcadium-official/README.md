# Arcadium website

Static HTML, CSS, JavaScript and self-hosted assets are maintained directly in `public/`. There is no generated build directory or frontend build step. These are the editable website sources.

Preview locally:

```powershell
python -m http.server 4173 --directory public --bind 127.0.0.1
```

Cloudflare Pages uses `pages_build_output_dir: ./public` from `wrangler.jsonc`. The production origin is https://arcadiumclient.com/ and the Pages project is `arcadium-client`. Publication requires the project owner's authorization; source changes do not authorize deployment.

Headings and body text use Manrope; the wordmark uses Sora. Font files and their original SIL Open Font License notices are self-hosted. Icons use Font Awesome Free under CC BY 4.0, with complete notices in `public/assets/platform-icons/`. See [ASSETS.md](ASSETS.md) for provenance and comparison-image details.

Screenshots retain their full frames and original aspect ratios. The FPS comparison adds enlarged counter insets and uses a vertically stacked image on small screens. Its sources and renderer are in `source/comparison-2026-11-04/` and `tools/build-comparison.cjs`; the renderer uses Sharp as a build-time tool, with no browser dependency. Download routes in `_redirects` and `release.json` identify published Windows, Apple Silicon and Intel Mac assets. Keep those values synchronized with an authorized release. The online count is read from the public presence endpoint; no account cookies, API credentials or private server keys belong in website files.

Arcadium support: https://discord.gg/QbNfaCHjUz
Source: https://github.com/1y23qq/arcadiumclient
