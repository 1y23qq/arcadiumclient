const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('sharp');

const site = path.resolve(__dirname, '..');
const source = path.join(site, 'source/comparison-2026-11-04');
const assets = path.join(site, 'public/assets');
const evidence = path.resolve(site, '../../build/site-comparison-070');
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const uri = (bytes, mime) => `data:${mime};base64,${bytes.toString('base64')}`;

async function main() {
  const manifest = JSON.parse(await fs.readFile(path.join(source, 'sources.json'), 'utf8'));
  const names = {lunar: 'Lunar Client', badlion: 'Badlion Client', arcadium: 'Arcadium Client'};
  const captures = [];
  for (const item of manifest.captures) {
    const bytes = await fs.readFile(path.join(source, item.file));
    if (digest(bytes) !== item.sha256) throw new Error(`Capture changed: ${item.client}`);
    const metadata = await sharp(bytes).metadata();
    if (metadata.width !== item.width || metadata.height !== item.height) throw new Error('Unexpected capture dimensions');
    let logo, mime;
    if (item.client === 'arcadium') {
      logo = await fs.readFile(path.join(assets, 'arcadium.svg')); mime = 'image/svg+xml';
    } else {
      const entry = manifest.logos[item.client === 'lunar' ? 'lunar-logo.webp' : 'badlion-logo.png'];
      const original = await fs.readFile(path.join(assets, entry.file));
      if (digest(original) !== entry.sha256) throw new Error('Logo changed');
      logo = await sharp(original).png().toBuffer(); mime = 'image/png';
    }
    captures.push({...item, name: names[item.client], image: uri(bytes, 'image/png'), logo: uri(logo, mime)});
  }
  await fs.mkdir(evidence, {recursive: true});
  const outputs = [];
  for (const stacked of [false, true]) {
    const width = stacked ? 800 : 2400, height = stacked ? 1728 : 576;
    const definitions = captures.map(item => `<image id="capture-${item.client}" width="1920" height="1080" href="${item.image}"/>`).join('');
    const panels = captures.map((item, index) => {
      const x = stacked ? 0 : index * 800, y = stacked ? index * 576 : 0;
      return `<g transform="translate(${x} ${y})">
        <image x="26" y="24" width="52" height="52" href="${item.logo}"/>
        <text x="96" y="59" fill="#f3f4f8" font-family="Arial, sans-serif" font-size="30" font-weight="600">${item.name}</text>
        <svg x="16" y="104" width="768" height="432" viewBox="0 0 1920 1080"><use href="#capture-${item.client}"/></svg>
        <rect x="15" y="103" width="282" height="100" rx="3" fill="#12151c" stroke="#7b828e"/>
        <svg x="16" y="104" width="280" height="98" viewBox="0 0 200 70"><use href="#capture-${item.client}"/></svg>
      </g>`;
    }).join('');
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><title>Lunar Client 337 FPS, Badlion Client 401 FPS, Arcadium Client 821 FPS</title><desc>Full screenshots from Hypixel Bedwars Lobby 1, with enlarged copies of their original FPS counters and official client logos.</desc><defs>${definitions}</defs><rect width="${width}" height="${height}" fill="#12151c"/>${panels}</svg>`;
    const stem = stacked ? 'fps-comparison-mobile' : 'fps-comparison';
    await fs.writeFile(path.join(evidence, stem + '.svg'), svg);
    const png = await sharp(Buffer.from(svg)).png().toBuffer();
    await fs.writeFile(path.join(evidence, stem + '.png'), png);
    const webp = await sharp(png).webp({quality: 96, effort: 6}).toBuffer();
    const sha256 = digest(webp), file = `${stem}-${sha256.slice(0, 12)}.webp`;
    await fs.writeFile(path.join(assets, file), webp);
    outputs.push({file, width, height, sha256, bytes: webp.length});
  }
  await fs.writeFile(path.join(source, 'outputs.json'), JSON.stringify(outputs, null, 2) + '\n');
  console.log(JSON.stringify(outputs, null, 2));
}
main().catch(error => {console.error(error); process.exitCode = 1;});
