// Deterministic visual index of the exact converted VectorDrawable paths.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.argv[2] || 'sharp');
const root = path.resolve(__dirname, '..');
const base = path.join(root, 'art-source/icons/game-icons');
const entries = JSON.parse(fs.readFileSync(path.join(base, 'import-report.json'), 'utf8'));
const colors = ['#e9bc66', '#77bff0', '#89ce86', '#b29de8', '#dc796b'];
let svg = '<svg xmlns="http://www.w3.org/2000/svg" width="1008" height="1040" viewBox="0 0 1008 1040"><rect width="1008" height="1040" fill="#102532"/><text x="22" y="34" fill="#e9bc66" font-family="sans-serif" font-size="21">Game-icons.net / DouQiuQiu - imported item icon set</text>';
entries.forEach((entry, i) => {
  const x = i % 9 * 112, y = Math.floor(i / 9) * 132 + 52;
  const xml = fs.readFileSync(path.join(root, 'app/src/main/res/drawable', entry.resource + '.xml'), 'utf8');
  svg += `<rect x="${x + 4}" y="${y}" width="104" height="124" rx="8" fill="#193441" stroke="#36525e"/>`;
  svg += `<g transform="translate(${x + 20},${y + 8}) scale(0.140625)" fill="${colors[i % colors.length]}">`;
  for (const match of xml.matchAll(/android:pathData="([^"]+)"/g)) svg += `<path d="${match[1]}"/>`;
  svg += '</g>';
  const label = entry.source.split('/')[1].replace('.svg', '');
  svg += `<text x="${x + 56}" y="${y + 98}" text-anchor="middle" font-family="sans-serif" font-size="9" fill="#e6ddc8">${label.slice(0, 19)}</text>`;
  svg += `<text x="${x + 56}" y="${y + 113}" text-anchor="middle" font-family="sans-serif" font-size="9" fill="#9bb3bb">${entry.author}</text>`;
});
svg += '<text x="22" y="1018" font-family="sans-serif" font-size="13" fill="#9bb3bb">Icons made by Lorc and Delapouite / CC BY 3.0 / recolored, background removed, Android vectors</text></svg>';
fs.writeFileSync(path.join(base, 'preview.svg'), svg);
sharp(Buffer.from(svg)).png().toFile(path.join(base, 'preview.png')).then(() => console.log('Wrote icon preview.png')).catch(e => { console.error(e); process.exitCode = 1; });
